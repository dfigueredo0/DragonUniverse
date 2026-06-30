package studio.elysium.dragonuniverse.mixin.sodium;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.elysium.dragonuniverse.client.render.post.DUNormalBuffer;
import studio.elysium.dragonuniverse.client.render.water.DUWater;

/**
 * Injects a world-space normal output into Sodium's terrain shaders so the look stack gets a real
 * geometry-normals G-buffer instead of the depth-reconstructed one. Sodium loads its GLSL from its
 * own jar via the classloader ({@code Class.getResourceAsStream("/assets/sodium/shaders/...")}),
 * <b>bypassing the resource-pack system</b>, so a pack override can't reach it — string surgery on
 * the loaded source is the only path.
 *
 * <p>The vertex shader already computes the camera-relative world position but doesn't pass it on;
 * added an {@code out}/{@code in} varying for it and compute {@code normalize(cross(dFdx, dFdy))} in the
 * fragment shader, writing it to {@code layout(location = 1)}. Because the shaders are
 * {@code #version 330 core}, the explicit layout qualifier binds the second output automatically — no
 * program-link patch needed.</p>
 *
 * <p><b>Fail-soft:</b> every anchor substring is checked <i>before</i> any replacement. If the source
 * shape doesn't match (a newer Sodium), leave the source untouched and trip
 * {@link DUNormalBuffer#markShaderFailed} so the MRT attach never runs against a program lacking
 * the normal output — a blind inject would be a GLSL compile failure = dead terrain renderer. </p>
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public class SodiumShaderLoaderMixin {

    /**
     * Sign on the reconstructed normal. {@code cross(dFdx, dFdy)} orientation depends on screen
     * handedness and triangle winding, so this is verified in-game against a known +Y top face:
     * if a flat top reads as -Y, flip to {@code "-1.0"}.
     */
    @Unique
    private static final String NORMAL_SIGN = "1.0";

    /**
     * Global-scope GLSL appended after {@code out vec4 fragColor;}: the water uniforms (bound per-frame by
     * {@code DUWater}) plus clean-room helpers — a value-noise height field for the multi-octave ripple
     * normal and a depth→view-space reconstruction.
     */
    @Unique
    private static final String WATER_GLOBALS = """

            // === Dragon Universe Stage 1 water material (depth-aware; clean-room) ===
            in vec3 du_WaveNormal;
            uniform sampler2D du_OpaqueDepth;
            uniform mat4 du_InvProj;
            uniform mat3 du_NormalToView;
            uniform vec3 du_SunDirVS;
            uniform float du_Time;
            uniform vec4 du_WaterStillUV;
            uniform vec4 du_WaterFlowUV;
            uniform float du_WaterEnabled;
            // Stage D colored shadows: during the sun's-eye TRANSLUCENT re-invoke (du_ShadowPass>0.5) the
            // fragment emits a flat occluder TINT into the tint map instead of the full water surface shading.
            uniform float du_ShadowPass;
            uniform float du_WaterColoredShadows;
            uniform float du_ColoredWaterOpacity;
            uniform float du_AlphaK;
            uniform float du_AlphaMin;
            uniform float du_AlphaMax;
            uniform float du_FogDensity;
            uniform vec3 du_DeepColor;
            uniform vec3 du_ShallowColor;
            uniform float du_FoamWidth;
            uniform vec3 du_FoamColor;
            uniform float du_FresnelF0;
            uniform float du_Glint;
            uniform float du_NormalScale;
            uniform float du_NormalStrength;
            uniform float du_NormalSpeed;
            uniform float du_EyeInWater;
            uniform vec3 du_UnderColor;
            uniform float du_UnderFresnelPow;
            uniform float du_UnderAlphaMin;
            uniform float du_UnderAlphaMax;
            uniform sampler2D du_RippleField;
            uniform float du_RippleEnabled;
            uniform float du_RippleExtent;
            uniform float du_RippleTexel;
            uniform float du_RippleNormalStr;
            // SSR routing (Step 0): water surface G-buffer at location=2 (rg=oct view normal, b=fresnel,
            // a=surface depth/mask). Bound only for the translucent water pass (DUWaterGbuffer); when SSR
            // is off the attachment isn't bound, so the write is discarded.
            layout(location = 2) out vec4 du_WaterGbuf;
            uniform float du_SsrEnabled;
            vec2 du_octEncode(vec3 n) {
                n /= (abs(n.x) + abs(n.y) + abs(n.z));
                vec2 e = n.xy;
                if (n.z < 0.0) {
                    e = (1.0 - abs(e.yx)) * vec2(e.x >= 0.0 ? 1.0 : -1.0, e.y >= 0.0 ? 1.0 : -1.0);
                }
                return e;
            }
            bool du_inUV(vec2 uv, vec4 b) { return all(greaterThanEqual(uv, b.xy)) && all(lessThanEqual(uv, b.zw)); }
            float du_hash(vec2 p) { p = fract(p * vec2(127.1, 311.7)); p += dot(p, p + 34.45); return fract(p.x * p.y); }
            float du_vnoise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                vec2 u = f * f * (3.0 - 2.0 * f);
                float a = du_hash(i);
                float b = du_hash(i + vec2(1.0, 0.0));
                float c = du_hash(i + vec2(0.0, 1.0));
                float d = du_hash(i + vec2(1.0, 1.0));
                return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
            }
            float du_height(vec2 w) {
                float h = 0.0;
                float amp = 0.5;
                float frq = du_NormalScale;
                vec2 dir = vec2(1.0, 0.65);
                for (int i = 0; i < 3; i++) {
                    h += amp * du_vnoise(w * frq + du_Time * du_NormalSpeed * dir);
                    frq *= 2.03;
                    amp *= 0.5;
                    dir = vec2(-dir.y, dir.x);
                }
                return h;
            }
            vec3 du_waterNormalWorld(vec2 w, float strength) {
                float e = 0.20;
                float hx = du_height(w + vec2(e, 0.0)) - du_height(w - vec2(e, 0.0));
                float hz = du_height(w + vec2(0.0, e)) - du_height(w - vec2(0.0, e));
                return normalize(vec3(-hx * strength, 1.0, -hz * strength));
            }
            vec3 du_viewPos(vec2 uv, float depth) {
                vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
                vec4 v = du_InvProj * ndc;
                return v.xyz / v.w;
            }
            """;

    /**
     * Statements appended inside {@code main()} right after Sodium's final {@code fragColor = _linearFog(...)}:
     * for water fragments only (atlas-UV gated), recompute the surface from the opaque-depth delta — alpha &
     * fog from the through-water path, shoreline foam from the world-up Y-delta, fresnel sun glint, and a
     * multi-octave perturbed normal.
     */
    @Unique
    private static final String WATER_BODY = """

                if (du_ShadowPass > 0.5) {
                    // Stage D: sun's-eye colored-shadow pass. Emit a flat occluder tint (rgb) + opacity (a)
                    // into the tint map; bypass ALL water surface shading (no SSR/refraction/sky/waves/fog),
                    // so normal frames (du_ShadowPass==0) are untouched. Glass/other translucent: dyed albedo
                    // (Sodium's pre-fog `color`). Water: the material's shallow/deep tint, or excluded.
                    if (du_inUV(v_TexCoord, du_WaterStillUV) || du_inUV(v_TexCoord, du_WaterFlowUV)) {
                        if (du_WaterColoredShadows < 0.5) discard;   // water off → write no occlusion at all
                        fragColor = vec4(mix(du_ShallowColor, du_DeepColor, 0.5), du_ColoredWaterOpacity);
                    } else {
                        fragColor = vec4(color.rgb, color.a);
                    }
                } else if (du_WaterEnabled > 0.5 && (du_inUV(v_TexCoord, du_WaterStillUV) || du_inUV(v_TexCoord, du_WaterFlowUV))) {
                    // Shared, eye-independent surface prep (value-identical to the original above-water path).
                    vec2 du_suv = gl_FragCoord.xy / vec2(textureSize(du_OpaqueDepth, 0));
                    vec3 du_Pw = du_viewPos(du_suv, gl_FragCoord.z);
                    vec3 du_V = normalize(-du_Pw);
                    vec3 du_up = normalize(du_NormalToView * vec3(0.0, 1.0, 0.0));
                    vec3 du_waveWorld = normalize(du_WaveNormal);
                    vec3 du_waveVS = normalize(du_NormalToView * du_waveWorld);
                    float du_baseNoV = clamp(dot(du_waveVS, du_V), 0.0, 1.0);
                    float du_baseFres = du_FresnelF0 + (1.0 - du_FresnelF0) * pow(1.0 - du_baseNoV, 5.0);
                    float du_sky = clamp(max(max(fragColor.r, fragColor.g), fragColor.b), 0.0, 1.0);
                    float du_pert = clamp(du_baseFres + du_sky * 0.5, 0.0, 1.0) * du_NormalStrength;
                    vec3 du_rippleN = du_waterNormalWorld(d_WorldPos.xz, du_pert);
                    vec3 du_worldN = normalize(du_waveWorld + vec3(du_rippleN.x, 0.0, du_rippleN.z));
                    // Stage 3: layer the world-anchored ripple-sim height field as an extra normal source.
                    if (du_RippleEnabled > 0.5) {
                        vec2 du_rUV = d_WorldPos.xz / du_RippleExtent + 0.5;
                        if (du_rUV.x > 0.0 && du_rUV.x < 1.0 && du_rUV.y > 0.0 && du_rUV.y < 1.0) {
                            float du_rt = du_RippleTexel;
                            float du_shl = texture(du_RippleField, du_rUV - vec2(du_rt, 0.0)).r;
                            float du_shr = texture(du_RippleField, du_rUV + vec2(du_rt, 0.0)).r;
                            float du_shu = texture(du_RippleField, du_rUV - vec2(0.0, du_rt)).r;
                            float du_shd = texture(du_RippleField, du_rUV + vec2(0.0, du_rt)).r;
                            vec3 du_simSlope = vec3(-(du_shr - du_shl) * du_RippleNormalStr, 0.0, -(du_shd - du_shu) * du_RippleNormalStr);
                            du_worldN = normalize(du_worldN + du_simSlope);
                        }
                    }
                    vec3 du_N = normalize(du_NormalToView * du_worldN);
                    float du_NoV = clamp(dot(du_N, du_V), 0.0, 1.0);

                    if (du_EyeInWater < 0.5) {
                        // Top-side: surface seen from above (Stage 1 path — through-water depth difference).
                        vec3 du_Po = du_viewPos(du_suv, texture(du_OpaqueDepth, du_suv).r);
                        float du_t = max(0.0, length(du_Po - du_Pw));
                        float du_dy = max(0.0, dot(du_Pw - du_Po, du_up));
                        float du_fres = du_FresnelF0 + (1.0 - du_FresnelF0) * pow(1.0 - du_NoV, 5.0);
                        float du_fog = 1.0 - exp(-du_FogDensity * du_t);
                        float du_alpha = clamp(1.0 - exp(-du_AlphaK * du_t), du_AlphaMin, du_AlphaMax);
                        vec3 du_col = mix(fragColor.rgb * du_ShallowColor, du_DeepColor, du_fog);
                        float du_foam = smoothstep(du_FoamWidth, 0.0, du_dy);
                        du_col = mix(du_col, du_FoamColor, du_foam);
                        du_alpha = max(du_alpha, du_foam);
                        vec3 du_refl = reflect(-du_V, du_N);
                        float du_glint = pow(max(dot(du_refl, du_SunDirVS), 0.0), du_Glint) * du_fres;
                        du_col += vec3(du_glint);
                        fragColor = vec4(du_col, du_alpha);
                        // SSR routing: stash the surface normal (view-space, octahedral), fresnel, and the
                        // water surface window depth for the composite-stage SSR raymarch. a>0 = the mask.
                        if (du_SsrEnabled > 0.5) {
                            du_WaterGbuf = vec4(du_octEncode(du_N), du_fres, gl_FragCoord.z);
                        }
                    } else {
                        // Underwater: surface seen FROM BELOW. No through-water depth path (invalid here), no
                        // shoreline foam, no top-side sun glint. Horizon-independent — depends only on the
                        // wave normal vs. view dir, so it shades consistently above/below the eye-height line.
                        // Inverted fresnel behaviour: near-normal (looking up) transmits; grazing -> total
                        // internal reflection (opaque, mirror-like toward the dark underwater tint).
                        float du_tir = clamp(pow(1.0 - du_NoV, du_UnderFresnelPow), 0.0, 1.0);
                        vec3 du_col = mix(fragColor.rgb * du_ShallowColor, du_UnderColor, du_tir);
                        float du_alpha = mix(du_UnderAlphaMin, du_UnderAlphaMax, du_tir);
                        fragColor = vec4(du_col, du_alpha);
                    }
                }
            """;

    /**
     * Global-scope GLSL appended into the terrain <b>vertex</b> shader after {@code out vec2 v_TexCoord;}:
     * the {@code du_WaveNormal} varying, the wave uniforms, and a Gerstner (trochoidal) wave sum with its
     * analytic surface normal.
     */
    @Unique
    private static final String WATER_VTX_GLOBALS = """

            // === Dragon Universe Stage 2 ambient waves (Gerstner; clean-room) ===
            out vec3 du_WaveNormal;
            uniform vec4 du_WaterStillUV;
            uniform vec4 du_WaterFlowUV;
            uniform float du_Time;
            uniform float du_WaveEnabled;
            uniform float du_WaveAmplitude;
            uniform float du_WaveLength;
            uniform float du_WaveSpeed;
            uniform float du_WaveSteepness;
            uniform sampler2D du_RippleField;
            uniform float du_RippleEnabled;
            uniform float du_RippleExtent;
            uniform float du_RippleDisplaceScale;
            bool du_inUV(vec2 uv, vec4 b) { return all(greaterThanEqual(uv, b.xy)) && all(lessThanEqual(uv, b.zw)); }
            vec3 du_gerstner(vec2 p, float t, out vec3 nrm) {
                vec2 dirs[4] = vec2[4](
                    normalize(vec2(1.0, 0.30)),
                    normalize(vec2(-0.70, 0.70)),
                    normalize(vec2(0.20, -1.00)),
                    normalize(vec2(-0.90, -0.40))
                );
                float lenScale[4] = float[4](1.0, 0.62, 0.38, 0.23);
                float ampScale[4] = float[4](1.0, 0.55, 0.32, 0.18);
                vec3 disp = vec3(0.0);
                float nx = 0.0;
                float ny = 1.0;
                float nz = 0.0;
                for (int i = 0; i < 4; i++) {
                    vec2 D = dirs[i];
                    float L = max(du_WaveLength * lenScale[i], 0.001);
                    float A = du_WaveAmplitude * ampScale[i];
                    float w = 6.28318530718 / L;
                    float phi = du_WaveSpeed * w;
                    float Q = du_WaveSteepness / max(w * A * 4.0, 0.001);
                    float ph = w * dot(D, p) + phi * t;
                    float S = sin(ph);
                    float C = cos(ph);
                    float WA = w * A;
                    disp.x += Q * A * D.x * C;
                    disp.z += Q * A * D.y * C;
                    disp.y += A * S;
                    nx += -D.x * WA * C;
                    nz += -D.y * WA * C;
                    ny += -Q * WA * S;
                }
                nrm = normalize(vec3(nx, ny, nz));
                return disp;
            }
            """;

    /**
     * Statements appended inside the vertex {@code main()} right after {@code vec3 position = ...} (and
     * before the normals inject's {@code d_WorldPos = position;}, so both d_WorldPos and gl_Position use the
     * displaced position). Water verts only (atlas-UV gated); non-water verts get a flat up wave normal.
     */
    private static final String WATER_VTX_DISPLACE = """

                du_WaveNormal = vec3(0.0, 1.0, 0.0);
                bool du_isWaterVert = du_inUV(_vert_tex_diffuse_coord, du_WaterStillUV) || du_inUV(_vert_tex_diffuse_coord, du_WaterFlowUV);
                if (du_isWaterVert) {
                    if (du_WaveEnabled > 0.5) {
                        vec3 du_wnrm;
                        vec3 du_disp = du_gerstner(position.xz, du_Time, du_wnrm);
                        position += du_disp;
                        du_WaveNormal = du_wnrm;
                    }
                    // Stage 3/4 ripple sim: displace the surface by the simulated height so wakes/charge
                    // rings physically deform the water (the fsh adds the matching per-fragment normal).
                    if (du_RippleEnabled > 0.5) {
                        vec2 du_rfUV = position.xz / du_RippleExtent + 0.5;
                        if (du_rfUV.x > 0.0 && du_rfUV.x < 1.0 && du_rfUV.y > 0.0 && du_rfUV.y < 1.0) {
                            position.y += clamp(texture(du_RippleField, du_rfUV).r * du_RippleDisplaceScale, -1.0, 1.0);
                        }
                    }
                }""";

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, require = 0)
    private static void dragonuniverse$injectNormals(Identifier id, CallbackInfoReturnable<String> cir) {
        String src = cir.getReturnValue();
        if (src == null || id == null || !id.getPath().contains("block_layer_opaque")) {
            return;     // only the terrain block shaders; includes/other shaders pass through untouched
        }

        boolean isVertex = src.contains("gl_Position");
        boolean isFragment = src.contains("out vec4 fragColor");

        if (isVertex) {
            final String declAnchor = "out vec2 v_TexCoord;";
            final String posAnchor = "vec3 position = _vert_position + translation;";
            if (!src.contains(declAnchor) || !src.contains(posAnchor)) {
                DUNormalBuffer.markShaderFailed("vsh anchors missing");
                return;
            }
            String out = src
                    .replace(declAnchor, declAnchor + "\nout vec3 d_WorldPos;")
                    .replace(posAnchor, posAnchor + "\n    d_WorldPos = position;");

            // Displace water vertices with a Gerstner sum and emit the analytic surface normal
            // (du_WaveNormal) so the fsh lights the displaced geometry. The displacement is
            // injected BETWEEN the position decl and the normals injects `d_WorldPos = position;` (the
            // second replace lands before that line), so d_WorldPos — and gl_Position, computed later from
            // `position` — both pick up the displaced position. Water-vert gated by the same atlas UV.
            out = out
                    .replace(declAnchor, declAnchor + WATER_VTX_GLOBALS)
                    .replace(posAnchor, posAnchor + WATER_VTX_DISPLACE);

            cir.setReturnValue(out);
            DUNormalBuffer.markVshReady();
        } else if (isFragment) {
            final String declAnchor = "out vec4 fragColor;";
            final String mainAnchor = "void main() {";
            if (!src.contains(declAnchor) || !src.contains(mainAnchor)) {
                DUNormalBuffer.markShaderFailed("fsh anchors missing");
                return;
            }
            // dFdx/dFdy of the interpolated world position give the exact flat face normal.
            String normalWrite = "\n    d_Normal = vec4(normalize(cross(dFdx(d_WorldPos), dFdy(d_WorldPos))) * ("
                    + NORMAL_SIGN + " * 0.5) + 0.5, 1.0);";
            String out = src
                    .replace(declAnchor, declAnchor + "\nlayout(location = 1) out vec4 d_Normal;\nin vec3 d_WorldPos;")
                    .replace(mainAnchor, mainAnchor + normalWrite);

            // Append depth-aware water shading to the SAME shared terrain fsh. It's gated per-fragment
            // by the water atlas-UV bounds (Sodium has no per-fragment material id) and overrides fragColor
            // only for water; everything else falls through untouched. Reuses the d_WorldPos and is
            // independently fail-soft, a missing fog anchor leaves the normal inject intact and skips water.
            final String waterFogAnchor =
                    "fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog, fadeFactor);";
            if (out.contains(waterFogAnchor)) {
                out = out
                        .replace(declAnchor, declAnchor + WATER_GLOBALS)
                        // Clear the SSR gbuf for every fragment of the shared program (non-water translucent
                        // pixels then read mask 0); water fragments overwrite it in the body below.
                        .replace(mainAnchor, mainAnchor + "\n    du_WaterGbuf = vec4(0.0);")
                        .replace(waterFogAnchor, waterFogAnchor + WATER_BODY);
                DUWater.markFshReady();
            } else {
                DUWater.markFailed("water fog anchor missing");
            }

            cir.setReturnValue(out);
            DUNormalBuffer.markFshReady();
        }
    }
}

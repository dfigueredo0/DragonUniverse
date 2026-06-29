package studio.elysium.dragonuniverse.client.render.debug;

import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import studio.elysium.dragonuniverse.client.render.vfx.DUVFX;
import studio.elysium.dragonuniverse.client.render.DUScreenShake;
import studio.elysium.dragonuniverse.client.render.core.DUFrameData;
import studio.elysium.dragonuniverse.client.render.core.DUGodrayData;
import studio.elysium.dragonuniverse.client.render.core.DUGradeData;
import studio.elysium.dragonuniverse.client.render.core.DUSsaoData;
import studio.elysium.dragonuniverse.client.render.core.DUSsrData;
import studio.elysium.dragonuniverse.client.render.core.DUWaterData;
import studio.elysium.dragonuniverse.client.render.core.DULookData;
import studio.elysium.dragonuniverse.client.render.water.DUWater;
import studio.elysium.dragonuniverse.client.render.water.DUWaterSim;
import studio.elysium.dragonuniverse.client.render.post.DUPostChain;
import studio.elysium.dragonuniverse.client.render.sky.DUSkyLayer;
import studio.elysium.dragonuniverse.client.render.sky.DUSkyRenderer;
import studio.elysium.dragonuniverse.client.render.sky.DUSkybox;
import studio.elysium.dragonuniverse.client.render.sky.planet.DUPlanetRenderer;
import net.minecraft.resources.Identifier;
import studio.elysium.dragonuniverse.world.planet.PlanetDef;
import studio.elysium.dragonuniverse.world.planet.PlanetRegistry;
import studio.elysium.dragonuniverse.client.render.vfx.beam.*;
import studio.elysium.dragonuniverse.client.render.vfx.bolt.BoltConfig;
import studio.elysium.dragonuniverse.client.render.vfx.bolt.DUBolts;
import studio.elysium.dragonuniverse.client.render.vfx.DUEmissiveDemo;
import studio.elysium.dragonuniverse.client.render.vfx.DUWorldRenderer;

import java.util.ArrayList;
import java.util.List;

public final class DUDebugWindows {
    private static final float[] fog = { 0F };
    private static final float[] bloom = { 1F };
    private static final float[] sky = { 1F, 1F, 1F };

    // HDR look stack
    private static final float[] exposure = { DULookData.exposure };
    private static final ImInt tonemapOp = new ImInt(DULookData.operator);
    private static final String[] TONEMAP_OPS = { "Reinhard", "ACES (filmic)" };

    // Bloom
    private static final float[] bloomThreshold = { DULookData.bloomThreshold };
    private static final float[] bloomIntensity = { DULookData.bloomIntensity };
    private static final int[] bloomRadius = { DULookData.bloomRadius };

    // God rays + rim light
    private static final ImBoolean godrayEnabled = new ImBoolean(DUGodrayData.godrayEnabled);
    private static final float[] godrayIntensity = { DUGodrayData.intensity };
    private static final float[] godrayDensity = { DUGodrayData.density };
    private static final float[] godrayDecay = { DUGodrayData.decay };
    private static final float[] godrayWeight = { DUGodrayData.weight };
    private static final int[] godraySamples = { DUGodrayData.sampleCount };
    private static final float[] godrayColor = { DUGodrayData.sunColor.x, DUGodrayData.sunColor.y, DUGodrayData.sunColor.z };
    private static final ImBoolean rimEnabled = new ImBoolean(DUGodrayData.rimEnabled);
    private static final float[] rimStrength = { DUGodrayData.rimStrength };
    private static final float[] rimPower = { DUGodrayData.rimPower };
    private static final float[] rimColor = { DUGodrayData.rimColor.x, DUGodrayData.rimColor.y, DUGodrayData.rimColor.z };
    private static final ImBoolean silverEnabled = new ImBoolean(DUGodrayData.silverEnabled);
    private static final float[] silverStrength = { DUGodrayData.silverStrength };
    private static final float[] silverPower = { DUGodrayData.silverPower };
    private static final float[] silverScatter = { DUGodrayData.silverScatterPower };
    private static final float[] silverColor = { DUGodrayData.silverColor.x, DUGodrayData.silverColor.y, DUGodrayData.silverColor.z };
    // Color grade
    private static final ImBoolean gradeEnabled = new ImBoolean(DUGradeData.enabled);
    private static final float[] gradeContrast = { DUGradeData.contrast };
    private static final float[] gradeSaturation = { DUGradeData.saturation };
    private static final float[] gradeTemperature = { DUGradeData.temperature };
    private static final float[] gradeTint = { DUGradeData.tint };
    private static final float[] gradeLift = { DUGradeData.lift.x, DUGradeData.lift.y, DUGradeData.lift.z };
    private static final float[] gradeGamma = { DUGradeData.gamma.x, DUGradeData.gamma.y, DUGradeData.gamma.z };
    private static final float[] gradeGain = { DUGradeData.gain.x, DUGradeData.gain.y, DUGradeData.gain.z };
    private static final ImBoolean gradeSkyEnabled = new ImBoolean(DUGradeData.skyEnabled);
    private static final float[] gradeSkyStrength = { DUGradeData.skyStrength };
    private static final float[] gradeSkyWarm = { DUGradeData.skyWarm.x, DUGradeData.skyWarm.y, DUGradeData.skyWarm.z };
    private static final float[] gradeSkyCool = { DUGradeData.skyCool.x, DUGradeData.skyCool.y, DUGradeData.skyCool.z };
    private static final ImBoolean gradeSkyFromDim = new ImBoolean(DUGradeData.skyFromDimension);
    private static final float[] gradeSkyDimScale = { DUGradeData.skyDimScale };

    private static final ImBoolean ssaoEnabled = new ImBoolean(DUSsaoData.ssaoEnabled);
    private static final ImBoolean ssaoHiZ = new ImBoolean(DUSsaoData.hiZEnabled);
    private static final float[] ssaoHiZLodBias = { DUSsaoData.hiZLodBias };
    private static final float[] ssaoRadius = { DUSsaoData.radius };
    private static final float[] ssaoBias = { DUSsaoData.bias };
    private static final float[] ssaoIntensity = { DUSsaoData.intensity };
    private static final float[] ssaoPower = { DUSsaoData.power };
    private static final int[] ssaoSamples = { DUSsaoData.sampleCount };
    // Water
    private static final ImBoolean waterEnabled = new ImBoolean(DUWaterData.enabled);
    private static final float[] waterAlphaK = { DUWaterData.alphaK };
    private static final float[] waterAlphaMin = { DUWaterData.alphaMin };
    private static final float[] waterAlphaMax = { DUWaterData.alphaMax };
    private static final float[] waterFogDensity = { DUWaterData.fogDensity };
    private static final float[] waterDeep = { DUWaterData.deepColor.x, DUWaterData.deepColor.y, DUWaterData.deepColor.z };
    private static final float[] waterShallow = { DUWaterData.shallowColor.x, DUWaterData.shallowColor.y, DUWaterData.shallowColor.z };
    private static final float[] waterFoamWidth = { DUWaterData.foamWidth };
    private static final float[] waterFoam = { DUWaterData.foamColor.x, DUWaterData.foamColor.y, DUWaterData.foamColor.z };
    private static final float[] waterF0 = { DUWaterData.fresnelF0 };
    private static final float[] waterGlint = { DUWaterData.glint };
    private static final float[] waterNormalScale = { DUWaterData.normalScale };
    private static final float[] waterNormalStrength = { DUWaterData.normalStrength };
    private static final float[] waterNormalSpeed = { DUWaterData.normalSpeed };
    private static final ImBoolean waterWaves = new ImBoolean(DUWaterData.wavesEnabled);
    private static final float[] waterWaveAmp = { DUWaterData.waveAmplitude };
    private static final float[] waterWaveLen = { DUWaterData.waveLength };
    private static final float[] waterWaveSpeed = { DUWaterData.waveSpeed };
    private static final float[] waterWaveSteep = { DUWaterData.waveSteepness };
    private static final float[] waterUnderColor = { DUWaterData.underColor.x, DUWaterData.underColor.y, DUWaterData.underColor.z };
    private static final float[] waterUnderFresPow = { DUWaterData.underFresnelPow };
    private static final float[] waterUnderAlphaMin = { DUWaterData.underAlphaMin };
    private static final float[] waterUnderAlphaMax = { DUWaterData.underAlphaMax };
    private static final ImBoolean waterUnderFog = new ImBoolean(DUWaterData.underwaterFog);
    private static final float[] waterUnderFogColor = { DUWaterData.underwaterFogColor.x, DUWaterData.underwaterFogColor.y, DUWaterData.underwaterFogColor.z };
    private static final float[] waterUnderFogDensity = { DUWaterData.underwaterFogDensity };
    private static final ImBoolean waterRipple = new ImBoolean(DUWaterData.rippleEnabled);
    private static final ImBoolean waterRippleSrc = new ImBoolean(DUWaterData.rippleTestSource);
    private static final float[] waterRippleProp = { DUWaterData.ripplePropagation };
    private static final float[] waterRippleDamping = { DUWaterData.rippleDamping };
    private static final float[] waterRippleDecay = { DUWaterData.rippleHeightDecay };
    private static final float[] waterRippleNStr = { DUWaterData.rippleNormalStrength };
    private static final float[] waterRippleDisp = { DUWaterData.rippleDisplaceScale };
    private static final float[] waterRippleInjStr = { DUWaterData.rippleInjectStrength };
    private static final float[] waterRippleInjRad = { DUWaterData.rippleInjectRadius };
    private static final ImBoolean waterWakes = new ImBoolean(DUWaterData.wakesEnabled);
    private static final float[] waterWakeStr = { DUWaterData.wakeStrength };
    private static final float[] waterWakeRad = { DUWaterData.wakeRadius };
    private static final float[] waterWakeThresh = { DUWaterData.wakeSpeedThreshold };
    private static final ImBoolean waterCharge = new ImBoolean(DUWaterData.chargeEnabled);
    private static final ImBoolean waterDebugCharge = new ImBoolean(DUWaterData.debugCharge);
    private static final float[] waterChargeStr = { DUWaterData.chargeStrength };
    private static final float[] waterChargeRad = { DUWaterData.chargeRadius };
    // SSR
    private static final ImBoolean ssrEnabled = new ImBoolean(DUSsrData.enabled);
    private static final int[] ssrMaxSteps = { DUSsrData.maxSteps };
    private static final float[] ssrMaxDist = { DUSsrData.maxDistance };
    private static final float[] ssrStride = { DUSsrData.stride };
    private static final float[] ssrThickness = { DUSsrData.thickness };
    private static final int[] ssrRefine = { DUSsrData.refineSteps };
    private static final float[] ssrEdgeFade = { DUSsrData.edgeFade };
    private static final float[] ssrFresnelStr = { DUSsrData.fresnelStrength };
    private static final float[] ssrRoughness = { DUSsrData.roughness };
    private static final ImBoolean ssrFallback = new ImBoolean(DUSsrData.fallbackEnabled);
    private static final float[] ssrZenith = { DUSsrData.skyZenith.x, DUSsrData.skyZenith.y, DUSsrData.skyZenith.z };
    private static final float[] ssrHorizon = { DUSsrData.skyHorizon.x, DUSsrData.skyHorizon.y, DUSsrData.skyHorizon.z };
    private static final float[] ssrSunStr = { DUSsrData.sunStrength };
    private static final float[] ssrSunSharp = { DUSsrData.sunSharpness };
    private static final ImBoolean ssrTemporal = new ImBoolean(DUSsrData.temporalEnabled);
    private static final float[] ssrMaxFeedback = { DUSsrData.maxFeedback };
    private static final float[] ssrMotionSens = { DUSsrData.motionSensitivity };

    private static final float[] testColor = { 0.2F, 0.6F, 1.0F };
    private static final float[] testWidth = { 0.25F };

    private static final float[] emColor = { 0.4F, 0.8F, 1.0F };
    private static final float[] emSize = { 0.6F };
    private static final float[] emStrength = { 1.5F };
    private static final float[] emPulseSpeed = { 0.15F };

    private static final int[] vfxCount = { 16 };
    private static final float[] shakeAmount = { 1.5F };
    private static final int[] shakeDuration = { 12 };

    private static final int[] vortexCount = { 80 };
    private static final float[] vortexRadius = { 2.5F };
    private static final float[] vortexOrbitSpeed = { 0.25F };
    private static final float[] vortexBurstSpeed = { 0.25F };

    private static final ImInt beamType = new ImInt(1); // 0 straight, 1 spiral, 2 sine, 3 wobble
    private static final String[] BEAM_TYPES = { "Straight", "Spiral", "Sine", "Wobble" };
    private static final float[] beamWidth = { 0.12F };
    private static final float[] beamColor = { 0.5F, 0.8F, 1.0F };
    private static final float[] beamAlpha = { 0.3F };
    private static final int[] beamLifetime = { 40 };
    private static final float[] beamLength = { 16F };
    private static final float[] beamRadius = { 0.4F };
    private static final float[] beamTurns = { 4F };
    private static final float[] beamAmplitude = { 0.5F };
    private static final float[] beamFrequency = { 3F };
    private static final float[] beamAnim = { 0.4F };

    private static final int[] boltSegments = { 6 };
    private static final float[] boltDisp = { 1.2F };
    private static final float[] boltFork = { 0.18F };
    private static final int[] boltForkDepth = { 3 };
    private static final float[] boltWidth = { 0.09F };
    private static final float[] boltColor = { 0.6F, 0.8F, 1.0F };
    private static final int[] boltLifetime = { 8 };

    // Skybox controls
    private static final ImInt skyPreset = new ImInt(0);
    private static final String[] SKY_PRESETS = { "Airless Starfield", "Thick Atmosphere", "Textured Nebula", "Cubemap (Panorama)" };
    private static final float[] skyTmpFloat = { 0F };
    private static final float[] skyTmp3 = { 0F, 0F, 0F };

    private static final ImBoolean skyEnabled = new ImBoolean(DUSkyRenderer.isEnabled());
    private static final ImBoolean planetsEnabled = new ImBoolean(DUPlanetRenderer.isEnabled());
    private static final ImBoolean lockSun = new ImBoolean(DUFrameData.lockSunDir);
    private static final ImBoolean postFx = new ImBoolean(DUPostChain.isEnabled());
    private static final ImBoolean emBase = new ImBoolean(DUEmissiveDemo.showBase);
    private static final ImBoolean emEmissive = new ImBoolean(DUEmissiveDemo.showEmissive);
    private static final ImBoolean emGlow = new ImBoolean(DUEmissiveDemo.showGlow);

    private DUDebugWindows() {}

    public static void render() {
        if (ImGui.begin("Dragon Universe - Render Debug")) {
            if (ImGui.collapsingHeader("Post FX (HDR look stack)")) {
                if (ImGui.checkbox("Enable look stack", postFx)) {
                    DUPostChain.setEnabled(postFx.get());
                }
                ImGui.text("HDR foundation: blit -> VFX (additive, HDR) -> tonemap.");
                ImGui.text("Additive VFX now accumulate past 1.0 instead of clipping to white.");
                if (ImGui.sliderFloat("Exposure", exposure, 0.1f, 8.0f)) {
                    DULookData.exposure = exposure[0];
                }
                if (ImGui.combo("Tonemap operator", tonemapOp, TONEMAP_OPS)) {
                    DULookData.operator = tonemapOp.get();
                }
                ImGui.separator();
                ImGui.text("Bloom: bright-extract -> blur -> add before tonemap.");
                if (ImGui.sliderFloat("Bloom threshold", bloomThreshold, 0.0f, 4.0f)) {
                    DULookData.bloomThreshold = bloomThreshold[0];
                }
                if (ImGui.sliderFloat("Bloom intensity", bloomIntensity, 0.0f, 3.0f)) {
                    DULookData.bloomIntensity = bloomIntensity[0];
                }
                if (ImGui.sliderInt("Bloom radius (blur passes)", bloomRadius, 1, 8)) {
                    DULookData.bloomRadius = bloomRadius[0];
                }
            }

            if (ImGui.collapsingHeader("God Rays & Rim")) {
                ImGui.text("Rays emanate from the sun direction (Per-Frame UBO -> sunDir),");
                ImGui.text("masked by sky vs. geometry. Sun must be in front of the camera.");
                if (ImGui.button("Aim sun at look direction")) {
                    aimSunAtCamera();
                }
                ImGui.sameLine();
                ImGui.text(DUFrameData.lockSunDir ? "(sun locked)" : "(sun auto / day cycle)");
                // gate is the 0..1 visibility fade: 1 on-screen, ramping to 0 as the sun leaves the
                // frame, 0 when grazing/behind. "Aim sun at look direction" should read ~ (0.50, 0.50).
                ImGui.text(String.format("sun screen = (%.3f, %.3f)  gate=%.2f",
                        DUGodrayData.lastSunU(), DUGodrayData.lastSunV(), DUGodrayData.lastSunGate()));

                if (ImGui.checkbox("Enable god rays", godrayEnabled)) {
                    DUGodrayData.godrayEnabled = godrayEnabled.get();
                }
                if (ImGui.sliderFloat("Ray intensity (exposure)", godrayIntensity, 0.0f, 3.0f)) {
                    DUGodrayData.intensity = godrayIntensity[0];
                }
                if (ImGui.sliderFloat("Density", godrayDensity, 0.0f, 2.0f)) {
                    DUGodrayData.density = godrayDensity[0];
                }
                if (ImGui.sliderFloat("Decay", godrayDecay, 0.5f, 1.0f)) {
                    DUGodrayData.decay = godrayDecay[0];
                }
                if (ImGui.sliderFloat("Weight", godrayWeight, 0.0f, 2.0f)) {
                    DUGodrayData.weight = godrayWeight[0];
                }
                if (ImGui.sliderInt("Samples", godraySamples, 8, 128)) {
                    DUGodrayData.sampleCount = godraySamples[0];
                }
                if (ImGui.colorEdit3("Ray color", godrayColor)) {
                    DUGodrayData.sunColor.set(godrayColor[0], godrayColor[1], godrayColor[2]);
                }

                ImGui.separator();
                ImGui.text("Rim light (geometry normals): Fresnel pow(1-dot(N,V),power) * sun-facing.");
                ImGui.text("Sun-driven & world-space -> persists as you turn. Put the sun to the side.");
                if (ImGui.checkbox("Enable rim light", rimEnabled)) {
                    DUGodrayData.rimEnabled = rimEnabled.get();
                }
                if (ImGui.sliderFloat("Rim strength", rimStrength, 0.0f, 4.0f)) {
                    DUGodrayData.rimStrength = rimStrength[0];
                }
                if (ImGui.sliderFloat("Rim power", rimPower, 0.5f, 8.0f)) {
                    DUGodrayData.rimPower = rimPower[0];
                }
                if (ImGui.colorEdit3("Rim color", rimColor)) {
                    DUGodrayData.rimColor.set(rimColor[0], rimColor[1], rimColor[2]);
                }

                ImGui.separator();
                ImGui.text("Silver-lining (clouds): back-lit cloud-edge glow. Fancy/Fast graphics; look");
                ImGui.text("toward the sun with clouds in front. Needs cloud normals.");
                if (ImGui.checkbox("Enable silver-lining", silverEnabled)) {
                    DUGodrayData.silverEnabled = silverEnabled.get();
                }
                if (ImGui.sliderFloat("Silver strength", silverStrength, 0.0f, 6.0f)) {
                    DUGodrayData.silverStrength = silverStrength[0];
                }
                if (ImGui.sliderFloat("Silver edge power", silverPower, 0.5f, 8.0f)) {
                    DUGodrayData.silverPower = silverPower[0];
                }
                if (ImGui.sliderFloat("Silver scatter focus", silverScatter, 1.0f, 32.0f)) {
                    DUGodrayData.silverScatterPower = silverScatter[0];
                }
                if (ImGui.colorEdit3("Silver color", silverColor)) {
                    DUGodrayData.silverColor.set(silverColor[0], silverColor[1], silverColor[2]);
                }

                ImGui.separator();
                ImGui.text("SSAO: hemisphere occlusion (geometry normals + depth), bilateral-blurred and");
                ImGui.text("multiplied into the scene before the additive passes. Inspect raw vs. blurred.");
                if (ImGui.checkbox("Enable SSAO", ssaoEnabled)) {
                    DUSsaoData.ssaoEnabled = ssaoEnabled.get();
                }
                if (ImGui.sliderFloat("AO radius (blocks)", ssaoRadius, 0.1f, 4.0f)) {
                    DUSsaoData.radius = ssaoRadius[0];
                }
                if (ImGui.sliderFloat("AO bias", ssaoBias, 0.0f, 0.2f)) {
                    DUSsaoData.bias = ssaoBias[0];
                }
                if (ImGui.sliderFloat("AO intensity", ssaoIntensity, 0.0f, 3.0f)) {
                    DUSsaoData.intensity = ssaoIntensity[0];
                }
                if (ImGui.sliderFloat("AO power (contrast)", ssaoPower, 0.5f, 8.0f)) {
                    DUSsaoData.power = ssaoPower[0];
                }
                if (ImGui.sliderInt("AO samples", ssaoSamples, 4, 64)) {
                    DUSsaoData.sampleCount = ssaoSamples[0];
                }
                if (ImGui.checkbox("Hi-Z distance persistence", ssaoHiZ)) {
                    DUSsaoData.hiZEnabled = ssaoHiZ.get();
                }
                if (ImGui.sliderFloat("Hi-Z LOD bias", ssaoHiZLodBias, -2.0f, 4.0f)) {
                    DUSsaoData.hiZLodBias = ssaoHiZLodBias[0];
                }
                ImGui.text("(Hi-Z: distant contact shadows persist instead of fading. +bias = coarser/more");
                ImGui.text("persistent but softer; -bias = finer. Inspect the pyramid below.)");
            }

            if (ImGui.collapsingHeader("Color Grade")) {
                ImGui.text("Final creative grade over the composited frame. Neutral = identity.");
                if (ImGui.checkbox("Enable color grade", gradeEnabled)) {
                    DUGradeData.enabled = gradeEnabled.get();
                }
                if (ImGui.sliderFloat("Contrast", gradeContrast, 0.0f, 2.0f)) {
                    DUGradeData.contrast = gradeContrast[0];
                }
                if (ImGui.sliderFloat("Saturation", gradeSaturation, 0.0f, 2.0f)) {
                    DUGradeData.saturation = gradeSaturation[0];
                }
                if (ImGui.sliderFloat("Temperature (cool<->warm)", gradeTemperature, -1.0f, 1.0f)) {
                    DUGradeData.temperature = gradeTemperature[0];
                }
                if (ImGui.sliderFloat("Tint (green<->magenta)", gradeTint, -1.0f, 1.0f)) {
                    DUGradeData.tint = gradeTint[0];
                }
                ImGui.text("Lift / Gamma / Gain (shadows / mids / highlights), per RGB:");
                if (ImGui.sliderFloat3("Lift", gradeLift, -0.5f, 0.5f)) {
                    DUGradeData.lift.set(gradeLift[0], gradeLift[1], gradeLift[2]);
                }
                if (ImGui.sliderFloat3("Gamma", gradeGamma, 0.2f, 3.0f)) {
                    DUGradeData.gamma.set(gradeGamma[0], gradeGamma[1], gradeGamma[2]);
                }
                if (ImGui.sliderFloat3("Gain", gradeGain, 0.0f, 3.0f)) {
                    DUGradeData.gain.set(gradeGain[0], gradeGain[1], gradeGain[2]);
                }

                ImGui.separator();
                ImGui.text("Directional sky-tint (sun-driven): warm looking toward the sun, cool away,");
                ImGui.text("neutral side-on. Full-frame atmospheric wash; independent of the static grade.");
                if (ImGui.checkbox("Enable sky-tint", gradeSkyEnabled)) {
                    DUGradeData.skyEnabled = gradeSkyEnabled.get();
                }
                if (ImGui.sliderFloat("Sky-tint strength", gradeSkyStrength, 0.0f, 1.0f)) {
                    DUGradeData.skyStrength = gradeSkyStrength[0];
                }
                if (ImGui.colorEdit3("Sky warm (toward sun)", gradeSkyWarm)) {
                    DUGradeData.skyWarm.set(gradeSkyWarm[0], gradeSkyWarm[1], gradeSkyWarm[2]);
                }
                if (ImGui.colorEdit3("Sky cool (away)", gradeSkyCool)) {
                    DUGradeData.skyCool.set(gradeSkyCool[0], gradeSkyCool[1], gradeSkyCool[2]);
                }
                if (ImGui.checkbox("Drive from dimension atmosphere", gradeSkyFromDim)) {
                    DUGradeData.skyFromDimension = gradeSkyFromDim.get();
                }
                if (ImGui.sliderFloat("Atmosphere tint scale", gradeSkyDimScale, 0.0f, 2.0f)) {
                    DUGradeData.skyDimScale = gradeSkyDimScale[0];
                }
                if (DUGradeData.skyFromDimension) {
                    ImGui.text(String.format("auto: strength=%.2f warm=(%.2f,%.2f,%.2f) cool=(%.2f,%.2f,%.2f)",
                            DUGradeData.autoSkyStrength * DUGradeData.skyDimScale,
                            DUGradeData.autoSkyWarm.x, DUGradeData.autoSkyWarm.y, DUGradeData.autoSkyWarm.z,
                            DUGradeData.autoSkyCool.x, DUGradeData.autoSkyCool.y, DUGradeData.autoSkyCool.z));
                    ImGui.text("(overrides the manual sky sliders; Earth-like until a planet surface() maps");
                    ImGui.text("this dimension — that hook is reserved for the dimension-export phase.)");
                }
            }

            if (ImGui.collapsingHeader("Water")) {
                ImGui.text("Depth-aware water injected into Sodium's translucent terrain pass. Alpha/fog from");
                ImGui.text("the through-water depth, shoreline foam from the Y-delta, fresnel glint, octave normals.");
                if (ImGui.checkbox("Enable water", waterEnabled)) {
                    DUWaterData.enabled = waterEnabled.get();
                }
                if (ImGui.sliderFloat("Alpha falloff", waterAlphaK, 0.02f, 2.0f)) {
                    DUWaterData.alphaK = waterAlphaK[0];
                }
                if (ImGui.sliderFloat("Alpha min (shore)", waterAlphaMin, 0.0f, 1.0f)) {
                    DUWaterData.alphaMin = waterAlphaMin[0];
                }
                if (ImGui.sliderFloat("Alpha max (deep)", waterAlphaMax, 0.0f, 1.0f)) {
                    DUWaterData.alphaMax = waterAlphaMax[0];
                }
                if (ImGui.sliderFloat("Fog density", waterFogDensity, 0.0f, 1.0f)) {
                    DUWaterData.fogDensity = waterFogDensity[0];
                }
                if (ImGui.colorEdit3("Deep color", waterDeep)) {
                    DUWaterData.deepColor.set(waterDeep[0], waterDeep[1], waterDeep[2]);
                }
                if (ImGui.colorEdit3("Shallow tint", waterShallow)) {
                    DUWaterData.shallowColor.set(waterShallow[0], waterShallow[1], waterShallow[2]);
                }
                if (ImGui.sliderFloat("Foam width (blocks)", waterFoamWidth, 0.0f, 3.0f)) {
                    DUWaterData.foamWidth = waterFoamWidth[0];
                }
                if (ImGui.colorEdit3("Foam color", waterFoam)) {
                    DUWaterData.foamColor.set(waterFoam[0], waterFoam[1], waterFoam[2]);
                }
                if (ImGui.sliderFloat("Fresnel F0", waterF0, 0.0f, 0.2f)) {
                    DUWaterData.fresnelF0 = waterF0[0];
                }
                if (ImGui.sliderFloat("Sun glint exponent", waterGlint, 8.0f, 600.0f)) {
                    DUWaterData.glint = waterGlint[0];
                }
                if (ImGui.sliderFloat("Ripple frequency", waterNormalScale, 0.05f, 2.0f)) {
                    DUWaterData.normalScale = waterNormalScale[0];
                }
                if (ImGui.sliderFloat("Ripple strength", waterNormalStrength, 0.0f, 4.0f)) {
                    DUWaterData.normalStrength = waterNormalStrength[0];
                }
                if (ImGui.sliderFloat("Ripple speed", waterNormalSpeed, 0.0f, 3.0f)) {
                    DUWaterData.normalSpeed = waterNormalSpeed[0];
                }
                ImGui.separator();
                ImGui.text("Ambient waves: Gerstner vertex displacement + analytic normal.");
                if (ImGui.checkbox("Enable waves", waterWaves)) {
                    DUWaterData.wavesEnabled = waterWaves.get();
                }
                if (ImGui.sliderFloat("Wave amplitude (blocks)", waterWaveAmp, 0.0f, 1.0f)) {
                    DUWaterData.waveAmplitude = waterWaveAmp[0];
                }
                if (ImGui.sliderFloat("Wave length (blocks)", waterWaveLen, 1.0f, 24.0f)) {
                    DUWaterData.waveLength = waterWaveLen[0];
                }
                if (ImGui.sliderFloat("Wave speed", waterWaveSpeed, 0.0f, 3.0f)) {
                    DUWaterData.waveSpeed = waterWaveSpeed[0];
                }
                if (ImGui.sliderFloat("Wave steepness", waterWaveSteep, 0.0f, 1.0f)) {
                    DUWaterData.waveSteepness = waterWaveSteep[0];
                }
                ImGui.separator();
                ImGui.text("Underwater: surface-from-below branch + scene volume fog.");
                if (ImGui.colorEdit3("Underside tint (TIR)", waterUnderColor)) {
                    DUWaterData.underColor.set(waterUnderColor[0], waterUnderColor[1], waterUnderColor[2]);
                }
                if (ImGui.sliderFloat("Underside fresnel power", waterUnderFresPow, 0.5f, 8.0f)) {
                    DUWaterData.underFresnelPow = waterUnderFresPow[0];
                }
                if (ImGui.sliderFloat("Underside alpha (up)", waterUnderAlphaMin, 0.0f, 1.0f)) {
                    DUWaterData.underAlphaMin = waterUnderAlphaMin[0];
                }
                if (ImGui.sliderFloat("Underside alpha (grazing)", waterUnderAlphaMax, 0.0f, 1.0f)) {
                    DUWaterData.underAlphaMax = waterUnderAlphaMax[0];
                }
                if (ImGui.checkbox("Underwater scene fog", waterUnderFog)) {
                    DUWaterData.underwaterFog = waterUnderFog.get();
                }
                if (ImGui.colorEdit3("Underwater fog color", waterUnderFogColor)) {
                    DUWaterData.underwaterFogColor.set(waterUnderFogColor[0], waterUnderFogColor[1], waterUnderFogColor[2]);
                }
                if (ImGui.sliderFloat("Underwater fog density", waterUnderFogDensity, 0.0f, 0.5f)) {
                    DUWaterData.underwaterFogDensity = waterUnderFogDensity[0];
                }
                ImGui.separator();
                ImGui.text("Ripple sim: world-anchored ping-pong height field, layered into the normal.");
                if (ImGui.checkbox("Enable ripple sim", waterRipple)) {
                    DUWaterData.rippleEnabled = waterRipple.get();
                }
                if (ImGui.button("Drop test ripple (at player)")) {
                    DUWaterSim.dropTestRippleAtPlayer();
                }
                if (ImGui.checkbox("Continuous test source (fixed world point)", waterRippleSrc)) {
                    DUWaterData.rippleTestSource = waterRippleSrc.get();
                    if (!DUWaterData.rippleTestSource) {
                        DUWaterSim.resetTestSource();
                    }
                }
                if (ImGui.sliderFloat("Propagation (C, <0.5)", waterRippleProp, 0.0f, 0.49f)) {
                    DUWaterData.ripplePropagation = waterRippleProp[0];
                }
                if (ImGui.sliderFloat("Damping", waterRippleDamping, 0.9f, 1.0f)) {
                    DUWaterData.rippleDamping = waterRippleDamping[0];
                }
                if (ImGui.sliderFloat("Height decay", waterRippleDecay, 0.99f, 1.0f)) {
                    DUWaterData.rippleHeightDecay = waterRippleDecay[0];
                }
                if (ImGui.sliderFloat("Ripple normal strength", waterRippleNStr, 0.0f, 24.0f)) {
                    DUWaterData.rippleNormalStrength = waterRippleNStr[0];
                }
                if (ImGui.sliderFloat("Ripple displace scale (surface)", waterRippleDisp, 0.0f, 1.0f)) {
                    DUWaterData.rippleDisplaceScale = waterRippleDisp[0];
                }
                if (ImGui.sliderFloat("Inject strength", waterRippleInjStr, 0.0f, 2.0f)) {
                    DUWaterData.rippleInjectStrength = waterRippleInjStr[0];
                }
                if (ImGui.sliderFloat("Inject radius (blocks)", waterRippleInjRad, 0.2f, 6.0f)) {
                    DUWaterData.rippleInjectRadius = waterRippleInjRad[0];
                }
                ImGui.separator();
                ImGui.text("Real sources: entity wakes + player charge-ripples.");
                if (ImGui.checkbox("Entity wakes", waterWakes)) {
                    DUWaterData.wakesEnabled = waterWakes.get();
                }
                if (ImGui.sliderFloat("Wake strength", waterWakeStr, 0.0f, 16.0f)) {
                    DUWaterData.wakeStrength = waterWakeStr[0];
                }
                if (ImGui.sliderFloat("Wake radius (blocks)", waterWakeRad, 0.2f, 4.0f)) {
                    DUWaterData.wakeRadius = waterWakeRad[0];
                }
                if (ImGui.sliderFloat("Wake speed threshold", waterWakeThresh, 0.0f, 0.2f)) {
                    DUWaterData.wakeSpeedThreshold = waterWakeThresh[0];
                }
                if (ImGui.checkbox("Player charge-ripples", waterCharge)) {
                    DUWaterData.chargeEnabled = waterCharge.get();
                }
                if (ImGui.checkbox("Force charge (debug)", waterDebugCharge)) {
                    DUWaterData.debugCharge = waterDebugCharge.get();
                }
                if (ImGui.sliderFloat("Charge strength", waterChargeStr, 0.0f, 2.0f)) {
                    DUWaterData.chargeStrength = waterChargeStr[0];
                }
                if (ImGui.sliderFloat("Charge radius (blocks)", waterChargeRad, 0.2f, 6.0f)) {
                    DUWaterData.chargeRadius = waterChargeRad[0];
                }
                if (!DUWater.shaderReady()) {
                    ImGui.text("(inject inactive: needs Sodium + the terrain shader inject to load)");
                }
            }

            if (ImGui.collapsingHeader("SSR — water reflections")) {
                ImGui.text("Screen-space reflections off water: refined raymarch + sky fallback, fresnel-blended");
                ImGui.text("into the surface (wet look) + roughness + temporal accum. Off when eye underwater.");
                if (ImGui.checkbox("Enable SSR", ssrEnabled)) {
                    DUSsrData.enabled = ssrEnabled.get();
                }
                if (ImGui.sliderInt("Max steps", ssrMaxSteps, 8, 256)) {
                    DUSsrData.maxSteps = ssrMaxSteps[0];
                }
                if (ImGui.sliderFloat("Max distance (blocks)", ssrMaxDist, 1.0f, 256.0f)) {
                    DUSsrData.maxDistance = ssrMaxDist[0];
                }
                if (ImGui.sliderFloat("Step stride (blocks)", ssrStride, 0.05f, 4.0f)) {
                    DUSsrData.stride = ssrStride[0];
                }
                if (ImGui.sliderFloat("Thickness (blocks)", ssrThickness, 0.05f, 8.0f)) {
                    DUSsrData.thickness = ssrThickness[0];
                }
                if (ImGui.sliderInt("Refine steps (binary search)", ssrRefine, 0, 12)) {
                    DUSsrData.refineSteps = ssrRefine[0];
                }
                if (ImGui.sliderFloat("Edge fade", ssrEdgeFade, 0.0f, 0.5f)) {
                    DUSsrData.edgeFade = ssrEdgeFade[0];
                }
                if (ImGui.sliderFloat("Fresnel strength (wet look)", ssrFresnelStr, 0.0f, 3.0f)) {
                    DUSsrData.fresnelStrength = ssrFresnelStr[0];
                }
                if (ImGui.sliderFloat("Roughness (reflection blur)", ssrRoughness, 0.0f, 1.0f)) {
                    DUSsrData.roughness = ssrRoughness[0];
                }
                ImGui.text("Sky fallback (rays that miss / leave the screen):");
                if (ImGui.checkbox("Enable sky fallback", ssrFallback)) {
                    DUSsrData.fallbackEnabled = ssrFallback.get();
                }
                if (ImGui.colorEdit3("Sky zenith", ssrZenith)) {
                    DUSsrData.skyZenith.set(ssrZenith[0], ssrZenith[1], ssrZenith[2]);
                }
                if (ImGui.colorEdit3("Sky horizon", ssrHorizon)) {
                    DUSsrData.skyHorizon.set(ssrHorizon[0], ssrHorizon[1], ssrHorizon[2]);
                }
                if (ImGui.sliderFloat("Reflected sun strength", ssrSunStr, 0.0f, 16.0f)) {
                    DUSsrData.sunStrength = ssrSunStr[0];
                }
                if (ImGui.sliderFloat("Reflected sun sharpness", ssrSunSharp, 16.0f, 2000.0f)) {
                    DUSsrData.sunSharpness = ssrSunSharp[0];
                }
                ImGui.text("Temporal accumulation (stabilizes wave shimmer; gated by camera stillness):");
                if (ImGui.checkbox("Enable temporal", ssrTemporal)) {
                    DUSsrData.temporalEnabled = ssrTemporal.get();
                }
                if (ImGui.sliderFloat("Max feedback (still camera)", ssrMaxFeedback, 0.0f, 0.97f)) {
                    DUSsrData.maxFeedback = ssrMaxFeedback[0];
                }
                if (ImGui.sliderFloat("Motion sensitivity", ssrMotionSens, 1.0f, 120.0f)) {
                    DUSsrData.motionSensitivity = ssrMotionSens[0];
                }
                if (!DUWaterData.enabled || !DUWater.shaderReady()) {
                    ImGui.text("(needs water enabled + the terrain inject active to produce reflections)");
                }
            }

            DUTargetViewPanel.render();

            if (ImGui.collapsingHeader("Per-Frame UBO")) {
                ImGui.text(String.format("time = %.2f", currentTime()));
                ImGui.text(String.format("sunDir = (%.2f, %.2f, %.2f)",
                        DUFrameData.sunDir.x, DUFrameData.sunDir.y, DUFrameData.sunDir.z));
                if (ImGui.checkbox("Lock sun dir", lockSun)) {
                    DUFrameData.lockSunDir = lockSun.get();
                }

                if (ImGui.sliderFloat("Fog density", fog, 0f, 1f)) {
                    DUFrameData.fogDensity = fog[0];
                }
                if (ImGui.sliderFloat("Bloom intensity", bloom, 0f, 4f)) {
                    DUFrameData.bloomIntensity = bloom[0];
                }
                if (ImGui.colorEdit3("Sky tint", sky)) {
                    DUFrameData.skyTint.set(sky[0], sky[1], sky[2]);
                }
            }

            if (ImGui.collapsingHeader("VFX Test")) {
                ImGui.colorEdit3("Trail color", testColor);
                ImGui.sliderFloat("Trail width", testWidth, 0.02f, 1.0f);

                if (ImGui.button("Spawn test trail in front of camera")) {
                    spawnTestTrail();
                }
                ImGui.sameLine();
                if (ImGui.button("Clear trails")) {
                    DUWorldRenderer.clearTrails();
                }
                ImGui.text("Active trails: " + DUWorldRenderer.getTrails().size());
            }

            if (ImGui.collapsingHeader("Emissive Demo")) {
                if (ImGui.checkbox("Base (lit)", emBase)) {
                    DUEmissiveDemo.showBase = emBase.get();
                }
                ImGui.sameLine();
                if (ImGui.checkbox("Emissive", emEmissive)) {
                    DUEmissiveDemo.showEmissive = emEmissive.get();
                }
                ImGui.sameLine();
                if (ImGui.checkbox("Glow halo", emGlow)) {
                    DUEmissiveDemo.showGlow = emGlow.get();
                }

                ImGui.colorEdit3("Emissive color", emColor);
                if (ImGui.sliderFloat("Cube size", emSize, 0.1f, 2.0f)) {
                    // Live-resize already-spawned cubes (not just future spawns).
                    for (DUEmissiveDemo.DemoCube cube : DUEmissiveDemo.getCubes()) {
                        cube.size = emSize[0];
                    }
                }
                if (ImGui.sliderFloat("Emissive strength", emStrength, 0f, 4f)) {
                    DUEmissiveDemo.emissiveStrength = emStrength[0];
                }
                if (ImGui.sliderFloat("Pulse speed", emPulseSpeed, 0f, 1.0f)) {
                    DUEmissiveDemo.pulseSpeed = emPulseSpeed[0];
                }

                if (ImGui.button("Spawn cube in front of camera")) {
                    spawnDemoCube();
                }
                ImGui.sameLine();
                if (ImGui.button("Clear cubes")) {
                    DUEmissiveDemo.clearCubes();
                }
                ImGui.text("Active cubes: " + DUEmissiveDemo.getCubes().size());
            }

            if (ImGui.collapsingHeader("VFX / Screenshake")) {
                ImGui.sliderInt("Particle count", vfxCount, 1, 100);
                if (ImGui.button("Sparks")) spawnVfx(0);
                ImGui.sameLine();
                if (ImGui.button("Ichor splash")) spawnVfx(1);
                if (ImGui.button("Space dust")) spawnVfx(2);
                ImGui.sameLine();
                if (ImGui.button("Star twinkle")) spawnVfx(3);

                ImGui.separator();
                ImGui.sliderFloat("Shake intensity", shakeAmount, 0f, 5f);
                ImGui.sliderInt("Shake duration (ticks)", shakeDuration, 1, 60);
                if (ImGui.button("Screenshake")) {
                    DUScreenShake.shake(shakeAmount[0], shakeDuration[0]);
                }

                ImGui.separator();
                ImGui.text("Vortex");
                ImGui.sliderInt("Vortex count", vortexCount, 1, 300);
                ImGui.sliderFloat("Vortex radius", vortexRadius, 0.5f, 8f);
                ImGui.sliderFloat("Orbit speed", vortexOrbitSpeed, 0f, 0.8f);
                ImGui.sliderFloat("Burst speed", vortexBurstSpeed, 0f, 1.0f);
                if (ImGui.button("Implode vortex in front of camera")) {
                    spawnVortex();
                }
            }

            if (ImGui.collapsingHeader("Bolts")) {
                ImGui.sliderInt("Subdivisions", boltSegments, 1, 7);
                ImGui.sliderFloat("Displacement", boltDisp, 0f, 4f);
                ImGui.sliderFloat("Fork probability", boltFork, 0f, 1f);
                ImGui.sliderInt("Max fork depth", boltForkDepth, 0, 5);
                ImGui.sliderFloat("Base width", boltWidth, 0.02f, 0.4f);
                ImGui.colorEdit3("Bolt color", boltColor);
                ImGui.sliderInt("Lifetime (ticks)", boltLifetime, 2, 40);

                if (ImGui.button("Strike in front of camera")) {
                    spawnBolt();
                }
                ImGui.sameLine();
                if (ImGui.button("Clear bolts")) {
                    DUBolts.clear();
                }
                ImGui.text("Active bolts: " + DUBolts.count());
            }

            if (ImGui.collapsingHeader("Beams")) {
                ImGui.combo("Path", beamType, BEAM_TYPES);
                ImGui.sliderFloat("Beam width", beamWidth, 0.02f, 0.6f);
                ImGui.colorEdit3("Beam color", beamColor);
                ImGui.sliderFloat("Beam intensity", beamAlpha, 0.05f, 1.0f);
                ImGui.sliderInt("Beam lifetime", beamLifetime, 2, 200);
                ImGui.sliderFloat("Beam length", beamLength, 2f, 48f);

                int ty = beamType.get();
                if (ty == 1) {
                    ImGui.sliderFloat("Spiral radius", beamRadius, 0f, 2f);
                    ImGui.sliderFloat("Spiral turns", beamTurns, 0f, 16f);
                    ImGui.sliderFloat("Anim speed", beamAnim, 0f, 2f);
                } else if (ty == 2) {
                    ImGui.sliderFloat("Sine amplitude", beamAmplitude, 0f, 3f);
                    ImGui.sliderFloat("Sine frequency", beamFrequency, 0f, 12f);
                    ImGui.sliderFloat("Anim speed", beamAnim, 0f, 2f);
                } else if (ty == 3) {
                    ImGui.sliderFloat("Wobble amplitude", beamAmplitude, 0f, 3f);
                    ImGui.sliderFloat("Wobble frequency", beamFrequency, 0f, 12f);
                }

                if (ImGui.button("Fire beam from camera")) {
                    spawnBeam();
                }
                ImGui.sameLine();
                if (ImGui.button("Clear beams")) {
                    DUBeams.clear();
                }
                ImGui.text("Active beams: " + DUBeams.count());
            }

            if (ImGui.collapsingHeader("Skybox")) {
                if (ImGui.checkbox("Enable skybox", skyEnabled)) {
                    DUSkyRenderer.setEnabled(skyEnabled.get());
                }
                if (Minecraft.getInstance().level != null) {
                    var dim = Minecraft.getInstance().level.dimension().identifier();
                    ImGui.text("Dimension: " + dim);
                    ImBoolean allow = new ImBoolean(DUSkyRenderer.isDimensionAllowed(dim));
                    if (ImGui.checkbox("Render in this dimension", allow)) {
                        DUSkyRenderer.setDimensionAllowed(dim, allow.get());
                    }
                }
                if (ImGui.combo("Preset", skyPreset, SKY_PRESETS)) {
                    DUSkyRenderer.setActive(switch (skyPreset.get()) {
                        case 1 -> DUSkybox.thickAtmosphere();
                        case 2 -> DUSkybox.texturedNebula();
                        case 3 -> DUSkybox.cubemapSky();
                        default -> DUSkybox.airlessStarfield();
                    });
                }
                ImGui.text("Layers (composited top-down):");

                List<DUSkyLayer> layers = DUSkyRenderer.active().layers();
                for (int i = 0; i < layers.size(); i++) {
                    DUSkyLayer layer = layers.get(i);
                    ImGui.pushID(i);
                    ImBoolean en = new ImBoolean(layer.enabled);
                    if (ImGui.checkbox(layer.type.name(), en)) {
                        layer.enabled = en.get();
                    }
                    skyTmpFloat[0] = layer.opacity;
                    if (ImGui.sliderFloat("opacity", skyTmpFloat, 0f, 1f)) {
                        layer.opacity = skyTmpFloat[0];
                    }
                    skyTmp3[0] = layer.r;
                    skyTmp3[1] = layer.g;
                    skyTmp3[2] = layer.b;
                    if (ImGui.colorEdit3("color", skyTmp3)) {
                        layer.r = skyTmp3[0];
                        layer.g = skyTmp3[1];
                        layer.b = skyTmp3[2];
                    }
                    ImGui.popID();
                }
            }

            if (ImGui.collapsingHeader("Planets")) {
                if (ImGui.checkbox("Enable orbital planets", planetsEnabled)) {
                    DUPlanetRenderer.setEnabled(planetsEnabled.get());
                }
                ImGui.text("Ray-marched at AfterSky (shares the skybox view UBO).");
                ImGui.text("Note: planets render in the skybox's allowed dimension(s).");
                ImGui.text("Loaded planets: " + PlanetRegistry.size());

                if (ImGui.button("Spawn test planet in front of camera")) {
                    spawnTestPlanet();
                }
                ImGui.sameLine();
                if (ImGui.button("Reload from disk")) {
                    PlanetRegistry.loadAll();
                }
                ImGui.text("(Full multi-planet editor lands in Task 3.)");
            }
        }
        ImGui.end();
    }

    /** Verification helper — spawns a planet ~300 blocks ahead and persists it. */
    private static void spawnTestPlanet() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var cam = mc.gameRenderer.getMainCamera();
        Vector3f fwd = new Vector3f(cam.forwardVector());
        Vector3f pos = new Vector3f(
                (float) cam.position().x, (float) cam.position().y, (float) cam.position().z)
                .add(new Vector3f(fwd).mul(300f));

        // Unique id so repeated clicks don't overwrite the same body.
        Identifier id = Identifier.fromNamespaceAndPath("dragonuniverse", "test_planet_" + (PlanetRegistry.size() + 1));
        while (PlanetRegistry.contains(id)) {
            id = Identifier.fromNamespaceAndPath("dragonuniverse", id.getPath() + "_b");
        }
        PlanetRegistry.save(PlanetDef.createDefault(id, pos)
                .withTexture(Identifier.fromNamespaceAndPath("dragonuniverse", "textures/planet/terra.png")));
    }

    private static void spawnBeam() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var cam = mc.gameRenderer.getMainCamera();
        Vector3f fwd = new Vector3f(cam.forwardVector());

        Vector3f from = new Vector3f(
                (float) cam.position().x, (float) cam.position().y, (float) cam.position().z)
                .add(new Vector3f(fwd).mul(1.0f));
        Vector3f to = new Vector3f(
                (float) cam.position().x, (float) cam.position().y, (float) cam.position().z)
                .add(new Vector3f(fwd).mul(beamLength[0]));

        BeamPath path = switch (beamType.get()) {
            case 1 -> new SpiralBeam(beamRadius[0], beamTurns[0], beamAnim[0]);
            case 2 -> new SineBeam(beamAmplitude[0], beamFrequency[0], beamAnim[0]);
            case 3 -> new WobbleBeam(beamAmplitude[0], beamFrequency[0]);
            default -> StraightBeam.INSTANCE;
        };
        BeamConfig cfg = BeamConfig.builder()
                .segments(48)
                .baseWidth(beamWidth[0])
                .color(beamColor[0], beamColor[1], beamColor[2])
                .alpha(beamAlpha[0], beamAlpha[0])
                .lifetime(beamLifetime[0])
                .build();
        DUBeams.spawn(from, to, path, cfg);
    }

    private static void spawnBolt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var cam = mc.gameRenderer.getMainCamera();
        Vector3f fwd = new Vector3f(cam.forwardVector());
        fwd.y = 0f;
        if (fwd.lengthSquared() < 1.0e-4f) {
            fwd.set(0f, 0f, 1f);
        }
        fwd.normalize();

        float bx = (float) cam.position().x + fwd.x * 8f;
        float by = (float) cam.position().y;
        float bz = (float) cam.position().z + fwd.z * 8f;

        Vector3f end = new Vector3f(bx, by, bz);
        Vector3f start = new Vector3f(bx, by + 12f, bz);

        BoltConfig cfg = BoltConfig.builder()
                .segments(boltSegments[0])
                .displacement(boltDisp[0])
                .forkProbability(boltFork[0])
                .maxForkDepth(boltForkDepth[0])
                .baseWidth(boltWidth[0])
                .color(boltColor[0], boltColor[1], boltColor[2])
                .lifetime(boltLifetime[0])
                .build();
        DUBolts.spawn(start, end, cfg);
    }

    private static void spawnVfx(int kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var cam = mc.gameRenderer.getMainCamera();
        Vector3f fwd = new Vector3f(cam.forwardVector());
        double x = cam.position().x + fwd.x * 3.0;
        double y = cam.position().y + fwd.y * 3.0;
        double z = cam.position().z + fwd.z * 3.0;
        int n = vfxCount[0];
        switch (kind) {
            case 0 -> DUVFX.sparks(mc.level, x, y, z, n);
            case 1 -> DUVFX.ichorSplash(mc.level, x, y, z, n);
            case 2 -> DUVFX.spaceDust(mc.level, x, y, z, n);
            case 3 -> DUVFX.pocketStarTwinkle(mc.level, x, y, z, n);
            default -> { }
        }
    }

    private static void spawnVortex() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var cam = mc.gameRenderer.getMainCamera();
        Vector3f fwd = new Vector3f(cam.forwardVector());
        double cx = cam.position().x + fwd.x * 5.0;
        double cy = cam.position().y + fwd.y * 5.0;
        double cz = cam.position().z + fwd.z * 5.0;
        DUVFX.implodeVortex(mc.level, cx, cy, cz, vortexCount[0], vortexRadius[0],
                vortexOrbitSpeed[0], vortexBurstSpeed[0]);
    }

    private static void spawnDemoCube() {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f fwd = new Vector3f(cam.forwardVector());
        Vector3f center = new Vector3f(
                (float) cam.position().x,
                (float) cam.position().y,
                (float) cam.position().z).add(fwd.mul(4.0f));
        DUEmissiveDemo.addCube(new DUEmissiveDemo.DemoCube(
                center, emSize[0], emColor[0], emColor[1], emColor[2]));
    }

    /** helper: point the (locked) sun straight down the camera's view so god rays are visible. */
    private static void aimSunAtCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vector3f fwd = new Vector3f(mc.gameRenderer.getMainCamera().forwardVector()).normalize();
        DUFrameData.sunDir.set(fwd);
        DUFrameData.lockSunDir = true;
        lockSun.set(true);
    }

    private static float currentTime() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? 0f : mc.level.getGameTime();
    }

    private static void spawnTestTrail() {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f base = new Vector3f(
                (float) cam.position().x,
                (float) cam.position().y,
                (float) cam.position().z);
        Vector3f fwd = new Vector3f(cam.forwardVector());

        List<Vector3f> pts = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            float d = 2.0f + i * 0.4f;
            // gentle sine wiggle so the ribbon shape is visible
            Vector3f p = new Vector3f(base).add(new Vector3f(fwd).mul(d));
            p.y += (float) Math.sin(i * 0.6f) * 0.3f;
            pts.add(p);
        }
        DUWorldRenderer.addTrail(new DUWorldRenderer.ActiveTrail(
                pts, testWidth[0], testColor[0], testColor[1], testColor[2],
                1.0f, 0.0f));
    }
}


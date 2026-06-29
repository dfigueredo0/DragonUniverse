package studio.elysium.dragonuniverse.client.render.sky;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import studio.elysium.dragonuniverse.client.render.core.DUGradeData;
import studio.elysium.dragonuniverse.world.planet.AtmosphereDef;
import studio.elysium.dragonuniverse.world.planet.PlanetDef;
import studio.elysium.dragonuniverse.world.planet.PlanetRegistry;

/**
 * Per-dimension atmosphere hook for the color-grade sky-tint. Maps the <i>current dimension's</i>
 * atmosphere to {@link DUGradeData}'s {@code autoSky*} values: a thicker atmosphere (higher
 * {@code density * densityFactor}) gives a stronger tint, and the atmosphere's Rayleigh
 * {@code scatterCoefficients} drive the warm/cool colours — the scattered colour cool (away from the sun)
 * and its reddened complement warm (toward the sun), exactly the Rayleigh intuition (blue sky, red sun).
 *
 * <p><b>Dimension binding:</b> a planet declares the dimension its surface maps to via
 * {@link PlanetDef#surface()}. That field is a <i>reserved hook</i> for the future dimension-export phase
 * and isn't populated yet, so today this resolves nothing and falls back to an Earth-like default for
 * every dimension. Once {@code surface()} is authored, per-dimension tints light up automatically with no
 * further change here — that's the point of wiring the hook now.</p>
 *
 * <p>Recomputes only on dimension change (cheap registry scan), into static fields the grade UBO reads;
 * the live {@code skyDimScale} master multiplier is applied at upload, so tuning doesn't need a recompute.
 * Render-thread only.</p>
 */
public final class DUAtmosphereTint {
    private DUAtmosphereTint() {}

    /** Strength for an Earth-like atmosphere (density 1). Matches the manual sky-tint default. */
    private static final float REF_STRENGTH = 0.3F;
    /** How far the warm/cool colours deviate from white (keeps the tint a subtle wash, not a cast). */
    private static final float TINT_AMOUNT = 0.4F;

    private static Identifier lastDim = null;

    /** Refresh the auto sky-tint for the current dimension (no-op unless the dimension changed). */
    public static void refresh() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Identifier dim = mc.level.dimension().identifier();
        if (dim.equals(lastDim)) {
            return;     // atmosphere only changes on a dimension change
        }
        lastDim = dim;
        apply(atmosphereFor(dim));
    }

    /** The atmosphere of the planet whose surface maps to {@code dim}, or null (no mapped planet). */
    private static AtmosphereDef atmosphereFor(Identifier dim) {
        for (PlanetDef planet : PlanetRegistry.all()) {
            if (planet.surface().isPresent() && planet.surface().get().equals(dim)) {
                return planet.atmosphere();
            }
        }
        return null;
    }

    private static void apply(AtmosphereDef atmo) {
        if (atmo == null) {
            // No mapped planet (e.g. the overworld today): Earth-like default.
            DUGradeData.autoSkyStrength = REF_STRENGTH;
            DUGradeData.autoSkyWarm.set(1.0F, 0.85F, 0.6F);
            DUGradeData.autoSkyCool.set(0.7F, 0.8F, 1.0F);
            return;
        }
        // Strength from optical thickness (raw; the live skyDimScale is applied at upload). An airless
        // body authored with low density yields ~0 = no tint.
        DUGradeData.autoSkyStrength = clamp01(atmo.density() * atmo.densityFactor() * REF_STRENGTH);

        // Colours from the Rayleigh scatter tint, normalized so the peak channel is 1.
        Vector3f s = atmo.scatterCoefficients();
        float sm = Math.max(1.0e-4F, Math.max(s.x, Math.max(s.y, s.z)));
        Vector3f scatterN = new Vector3f(s.x / sm, s.y / sm, s.z / sm);
        // Cool (away from sun) = white tinted toward the scattered colour.
        DUGradeData.autoSkyCool.set(toWhite(scatterN.x), toWhite(scatterN.y), toWhite(scatterN.z));
        // Warm (toward sun) = white tinted toward the complement (transmitted, reddened light).
        Vector3f comp = new Vector3f(1.0F - scatterN.x, 1.0F - scatterN.y, 1.0F - scatterN.z);
        float cm = Math.max(1.0e-4F, Math.max(comp.x, Math.max(comp.y, comp.z)));
        DUGradeData.autoSkyWarm.set(toWhite(comp.x / cm), toWhite(comp.y / cm), toWhite(comp.z / cm));
    }

    /** Blend a normalized channel toward white by TINT_AMOUNT (1 = white). */
    private static float toWhite(float c) {
        return 1.0F + (c - 1.0F) * TINT_AMOUNT;
    }

    private static float clamp01(float v) {
        return Math.clamp(v, 0.0F, 1.0F);
    }
}

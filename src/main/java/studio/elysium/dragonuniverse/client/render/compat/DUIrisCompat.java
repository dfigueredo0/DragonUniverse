package studio.elysium.dragonuniverse.client.render.compat;

import net.neoforged.fml.ModList;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.lang.reflect.Method;

/**
 * Iris detect-and-step-aside check. Dragon Universe owns its own look stack (HDR/tonemap, bloom,
 * god rays, grading) and does <b>not</b> defer to or route through Iris. The single remaining
 * concern: when an Iris shaderpack is active it overrides core/pipeline shaders and owns the
 * gbuffer/sky/post, so the screen-space passes would fight it. In that case cleanly stand down
 * and let Iris own the frame — {@link #shaderPackActive()} is the only Iris awareness kept.
 *
 * <p>Detect Iris at runtime via {@link ModList} and reflect the one API
 * call. If the reflection ever fails (API drift, mod absent) we degrade to "no pack active"
 * keeping Dragon Universe's own look rather than blanking the frame.</p>
 */
public final class DUIrisCompat {
    private DUIrisCompat() {}

    private static Boolean loaded;

    private static boolean reflectionResolved;
    private static Object irisApiInstance;
    private static Method isShaderPackInUse;

    public static boolean loaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("iris");
        }
        return loaded;
    }

    /**
     * True when an Iris shaderpack is active. When this is true the look stack
     * steps aside so Iris owns the frame rather than double-processing it.
     */
    public static boolean shaderPackActive() {
        if (!loaded()) {
            return false;
        }
        if (!reflectionResolved) {
            resolveReflection();
        }
        if (irisApiInstance == null || isShaderPackInUse == null) {
            return false;
        }
        try {
            return (boolean) isShaderPackInUse.invoke(irisApiInstance);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void resolveReflection() {
        reflectionResolved = true;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisApiInstance = api.getMethod("getInstance").invoke(null);
            isShaderPackInUse = api.getMethod("isShaderPackInUse");
        } catch (Throwable t) {
            DragonUniverse.LOGGER.warn("[Dragon Universe] Iris is loaded but its v0 API could not be "
                    + "reflected; assuming no shaderpack active (DU look stack stays on).", t);
        }
    }
}

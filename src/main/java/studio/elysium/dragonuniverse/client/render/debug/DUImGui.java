package studio.elysium.dragonuniverse.client.render.debug;

import foundry.imgui.api.ImGuiMCEvents;
import net.neoforged.fml.ModList;
import studio.elysium.dragonuniverse.DragonUniverse;

public final class DUImGui {
    private static boolean ENABLED = false;
    private static boolean INITIALIZED = false;

    private DUImGui() { }

    public static boolean available() {
        DragonUniverse.LOGGER.info(ModList.get().isLoaded("imguimc") ? ": ImGuiMC available" : ": ImGuiMC not available");
        return ModList.get().isLoaded("imguimc");
    }
    public static boolean enabled() { return ENABLED; }
    public static void toggle() {
        ENABLED = !ENABLED;
    }
    public static void setEnabled(boolean value) {
        ENABLED = value;
    }

    public static void init() {
        if (INITIALIZED || !available()) return;
        INITIALIZED = true;
        try {
            ImGuiMCEvents.INSTANCE.preRenderImGuiEvent(DUImGui::drawAll);
            DragonUniverse.LOGGER.info("[Dragon Universe] ImGui debug overlay initialized.");
        } catch (Throwable t) {
            DragonUniverse.LOGGER.warn("[Dragon Universe] ImGui debug overlay initialization failed.", t);
        }
    }

    public static void drawAll() {
        if (!ENABLED)
            return;
        DUDebugWindows.render();
        DUPlanetEditor.render();
    }
}

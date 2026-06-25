package studio.elysium.dragonuniverse.client.render.debug;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Transparent, non-pausing screen whose only job is to release the mouse cursor so the ImGui
 * debug overlay is clickable during active gameplay.
 *
 * <p>Minecraft only frees the cursor while a {@link Screen} is open; in normal play the cursor is
 * grabbed for camera control, so ImGui never receives usable clicks. This screen fixes that
 * without freezing the world — {@link #isPauseScreen()} returns {@code false}, so the level keeps
 * ticking. It renders nothing itself: ImGuiMC draws the overlay through its own render hook.</p>
 */
public final class DUImGuiScreen extends Screen {
    public DUImGuiScreen() {
        super(Component.literal("Dragon Universe Debug"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // no-op: keep the world fully visible behind the overlay (no blur / darkening).
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Let the same toggle key close the overlay (key mappings don't fire while a screen is open).
        if (event.key() == GLFW.GLFW_KEY_GRAVE_ACCENT) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        DUImGui.setEnabled(false);
        super.onClose();
    }
}

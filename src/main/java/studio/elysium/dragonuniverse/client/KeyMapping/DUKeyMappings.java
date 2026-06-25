package studio.elysium.dragonuniverse.client.KeyMapping;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;
import studio.elysium.dragonuniverse.DragonUniverse;

public class DUKeyMappings {
    public static final KeyMapping.Category DRAGON_UNIVERSE_CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "category"));

    private static final KeyMapping ZOOM_KEYMAPPING = new KeyMapping("key.dragonuniverse.zoom_key", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, DRAGON_UNIVERSE_CATEGORY);
    private static final KeyMapping DEBUG_IMGUI_KEYMAPPING = new KeyMapping("key.dragonuniverse.debug_imgui_key", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, DRAGON_UNIVERSE_CATEGORY);

    public static final Lazy<KeyMapping> PRESS_ZOOM_KEY = Lazy.of(() -> ZOOM_KEYMAPPING);
    public static final Lazy<KeyMapping> TOGGLE_DEBUG_IMGUI_KEY = Lazy.of(() -> DEBUG_IMGUI_KEYMAPPING);

    public static void register() {
    }
}

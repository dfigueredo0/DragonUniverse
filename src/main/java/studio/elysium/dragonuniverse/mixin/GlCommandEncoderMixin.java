package studio.elysium.dragonuniverse.mixin;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses harmless, high-frequency "Render pipeline ... wants a depth texture but none was
 * provided" warning. It's logged once per draw by ImGuiMC's {@code imguimc:pipeline/imgui} (its
 * pipeline declares a depth-stencil state but the overlay renders without a depth attachment)
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public class GlCommandEncoderMixin {

    @Redirect(
            method = "trySetup",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V")
    )
    private void dragonuniverse$silenceDepthWarning(Logger logger, String message, Object arg) {
        if (message == null || !message.contains("wants a depth texture")) {
            logger.warn(message, arg);
        }
    }
}

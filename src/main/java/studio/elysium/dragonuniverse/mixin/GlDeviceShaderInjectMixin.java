package studio.elysium.dragonuniverse.mixin;

import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import studio.elysium.dragonuniverse.client.render.post.DUEntityNormals;

/**
 * Injects a {@code layout(location = 1)} world/view-space normal output into the vanilla
 * {@code core/entity} shader so entities feed the look stack's geometry-normals buffer. The vanilla GL
 * device pulls each shader's GLSL via {@link ShaderSource#get} inside {@code compileShader}; redirecting
 * that call lets us post-process the source for our target shader and pass everything else through
 * untouched (the analogue of {@code SodiumShaderLoaderMixin}, but for the resource-backed vanilla path).
 *
 * <p>{@code require = 0} (fail-soft): if a future MC renames the method the redirect simply no-ops and
 * entity normals stay off; the rest of the stack is unaffected.</p>
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public class GlDeviceShaderInjectMixin {

    @Redirect(
            method = "compileShader",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/shaders/ShaderSource;get(Lnet/minecraft/resources/Identifier;Lcom/mojang/blaze3d/shaders/ShaderType;)Ljava/lang/String;"
            ),
            require = 0
    )
    private String dragonuniverse$injectEntityNormals(ShaderSource source, Identifier id, ShaderType type) {
        String src = source.get(id, type);
        try {
            return DUEntityNormals.injectShader(id, type, src);
        } catch (Throwable t) {
            return src;
        }
    }
}

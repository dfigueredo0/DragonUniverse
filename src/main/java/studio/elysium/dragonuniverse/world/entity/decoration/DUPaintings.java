package studio.elysium.dragonuniverse.world.entity.decoration;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.util.Optional;

public class DUPaintings {
    public static final ResourceKey<PaintingVariant> EXAMPLE_KEY = create("example_key");

    public static void bootstrap(BootstrapContext<PaintingVariant> bootstrapContext) {
        register(bootstrapContext, EXAMPLE_KEY, 2, 2, true);
    }

    private static ResourceKey<PaintingVariant> create(final String id) {
        return ResourceKey.create(Registries.PAINTING_VARIANT, Identifier.fromNamespaceAndPath(DragonUniverse.MODID, id));
    }

    private static void register(final BootstrapContext<PaintingVariant> context, final ResourceKey<PaintingVariant> key,
                                 final int width, final int height, final boolean hasAuthor) {
        context.register(key, new PaintingVariant(width, height, key.identifier(),
                Optional.of(Component.translatable(key.identifier().toLanguageKey("painting", "title")).withStyle(ChatFormatting.YELLOW)),
                hasAuthor ? Optional.of(Component.translatable(key.identifier().toLanguageKey("painting", "author")).withStyle(ChatFormatting.GRAY)) : Optional.empty()
        ));
    }
}

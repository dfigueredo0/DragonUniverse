package studio.elysium.dragonuniverse.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.data.SoundDefinitionsProvider;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.sounds.DUSounds;

public class DUSoundsProvider extends SoundDefinitionsProvider {
    /**
     * Creates a new instance of this data provider.
     *
     * @param output The {@linkplain PackOutput} instance provided by the data generator.
     */
    public DUSoundsProvider(PackOutput output) {
        super(output, DragonUniverse.MODID);
    }

    @Override
    public void registerSounds() {
        add(DUSounds.CHA_LA_HEAD_CHA_LA.get(), definition().subtitle("sounds.dragonuniverse.cha_la_head_cha_la")
                .with(sound(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "cha_la_head_cha_la")).stream()));
    }
}

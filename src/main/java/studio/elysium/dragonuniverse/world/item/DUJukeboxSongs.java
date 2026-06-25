package studio.elysium.dragonuniverse.world.item;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Util;
import net.minecraft.world.item.JukeboxSong;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.sounds.DUSounds;

public class DUJukeboxSongs {
    public static final ResourceKey<JukeboxSong> CHA_LA_HEAD_CHA_LA_KEY = createKey("cha_la_head_cha_la_key");

    public static void bootstrap(BootstrapContext<JukeboxSong> bootstrapContext) {
        register(bootstrapContext, CHA_LA_HEAD_CHA_LA_KEY, ((Holder.Reference<SoundEvent>) DUSounds.CHA_LA_HEAD_CHA_LA.getDelegate()), 107, 15);
    }

    private static ResourceKey<JukeboxSong> createKey(String name) {
        return ResourceKey.create(Registries.JUKEBOX_SONG, Identifier.fromNamespaceAndPath(DragonUniverse.MODID, name));
    }

    private static void register(BootstrapContext<JukeboxSong> bootstrapContext, ResourceKey<JukeboxSong> key,
                                 final Holder.Reference<SoundEvent> soundEvent, int lengthInSeconds, int comparatorOutput) {
        bootstrapContext.register(key, new JukeboxSong(soundEvent,
                Component.translatable(Util.makeDescriptionId("jukebox_song", key.identifier())), lengthInSeconds, comparatorOutput));
    }
}

package studio.elysium.dragonuniverse.core.particles;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.util.function.Supplier;

public class DUParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, DragonUniverse.MODID);

    public static final Supplier<SimpleParticleType> ZIRCON_PARTICLES =
        PARTICLE_TYPES.register("zircon_particles", () -> new SimpleParticleType(true));

    /**
     * Generic VFX particle type. Its only real job is to give the client a {@code SpriteSet} from
     * the particle atlas (captured by the provider). Actual spawning is done client-side and
     * fully configured via {@code DUVFXBuilder}, not through this type's network path.
     */
    public static final Supplier<SimpleParticleType> DU_VFX =
        PARTICLE_TYPES.register("du_vfx", () -> new SimpleParticleType(false));

    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}

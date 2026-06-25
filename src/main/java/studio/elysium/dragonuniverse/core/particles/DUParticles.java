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

    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}

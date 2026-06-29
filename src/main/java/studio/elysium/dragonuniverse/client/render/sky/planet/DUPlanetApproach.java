package studio.elysium.dragonuniverse.client.render.sky.planet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.planet.PlanetDef;
import studio.elysium.dragonuniverse.world.planet.PlanetRegistry;

import java.util.HashSet;
import java.util.Set;

/**
 * STUBBED orbital→surface proximity transition. Purely client-side detection: each client
 * tick this checks the local player against every {@link PlanetDef} and, on first entering a body's
 * approach radius ({@code radius * APPROACH_K}), fires {@link #onApproachPlanet(PlanetDef)} once.
 *
 * <p>It does NOT teleport or create any dimension — that is a later phase. The real path (verified
 * 26.1 API) is documented in {@code docs/phaseN-planet-dimensions.md}: the client would send a C2S
 * request, and the server would resolve the body's surface {@link net.minecraft.server.level.ServerLevel}
 * and call {@code serverPlayer.teleport(new TeleportTransition(...))}. None of that exists here;
 * this only logs + shows a chat line so the proximity hook and approach band can be felt in-game.</p>
 */
public final class DUPlanetApproach {
    private DUPlanetApproach() {}


    public static final float APPROACH_K = 1.25f;
    /** Hysteresis: must leave past {@code radius * APPROACH_K * EXIT_FACTOR} before it can re-fire. */
    private static final float EXIT_FACTOR = 1.05f;

    // Bodies the player is currently inside the approach band of, so we fire once on entry, not every tick.
    private static final Set<Identifier> inside = new HashSet<>();

    private static String lastStatus = "";

    /** Latest approach status line, for the editor window to surface. */
    public static String lastStatus() {
        return lastStatus;
    }

    /** Called once per client tick from {@code DragonUniverseClient.onClientTick}. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            inside.clear();
            return;
        }
        if (PlanetRegistry.size() == 0) {
            if (!inside.isEmpty()) inside.clear();
            return;
        }

        Vec3 p = mc.player.position();
        for (PlanetDef def : PlanetRegistry.all()) {
            float radius = def.radius();
            double enter = radius * APPROACH_K;
            double exit = enter * EXIT_FACTOR;

            double dx = def.position().x - p.x;
            double dy = def.position().y - p.y;
            double dz = def.position().z - p.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            boolean within = distSq <= enter * enter;
            boolean wasInside = inside.contains(def.id());

            if (within && !wasInside) {
                inside.add(def.id());
                onApproachPlanet(def);
            } else if (!within && wasInside && distSq > exit * exit) {
                // Only clear once well outside the band (hysteresis) so jitter at the edge won't spam.
                inside.remove(def.id());
            }
        }
    }

    /**
     * STUB. Fired once when the player enters a body's approach band. A later phase replaces the body
     * of this method with a C2S transition request; the server then teleports via the verified 26.1
     * {@code TeleportTransition} path (see {@code docs/phaseN-planet-dimensions.md}). Do NOT add a real
     * teleport here — this class is client-only and must not import server classes.
     */
    public static void onApproachPlanet(PlanetDef def) {
        // TODO Phase: dimension transition — replace this stub with a C2S "request surface transition"
        // packet (reuse network/packet + ServerboundPackets). Server side: resolve the body's surface
        // ServerLevel from def.surface() via server.getLevel(ResourceKey.create(Registries.DIMENSION,
        // <surface id>)) and call serverPlayer.teleport(new TeleportTransition(level, pos, Vec3.ZERO,
        // yaw, pitch, TeleportTransition.PostTeleportTransition.DO_NOTHING)). Gate on def.surface()
        // being present (a body with no surface dimension yet stays orbital-only).
        String surface = def.surface().map(Identifier::toString).orElse("<no surface bound>");
        lastStatus = "Would transition to " + def.id() + " surface (" + surface + ")";

        DragonUniverse.LOGGER.info("[Dragon Universe] STUB onApproachPlanet: {} -> surface {} (no teleport; see docs/phaseN-planet-dimensions.md)",
                def.id(), surface);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(
                    Component.literal("[DU] Would transition to " + def.id() + " surface (stub)"));
        }
    }
}

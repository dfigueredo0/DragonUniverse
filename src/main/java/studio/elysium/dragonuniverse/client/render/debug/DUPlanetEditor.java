package studio.elysium.dragonuniverse.client.render.debug;

import imgui.ImGui;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import studio.elysium.dragonuniverse.client.render.sky.planet.DUPlanetApproach;
import studio.elysium.dragonuniverse.client.render.sky.planet.DUPlanetRenderer;
import studio.elysium.dragonuniverse.world.planet.AtmosphereDef;
import studio.elysium.dragonuniverse.world.planet.PlanetDef;
import studio.elysium.dragonuniverse.world.planet.PlanetRegistry;
import studio.elysium.dragonuniverse.world.planet.RingDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ImGui multi-planet editor. A dedicated "Planets" window with one tab per planet currently
 * in the camera's view (dot-against-look test vs {@link PlanetDef#position()}). Each tab live-edits an
 * immutable {@link PlanetDef} by rebuilding the record on every change; edits are pushed to the
 * registry as transient (instant render preview) and only written to JSON on Save.
 *
 * <p>Client-only and only ever invoked from {@link DUImGui#drawAll()}, which is gated behind
 * {@link DUImGui#available()} — so the imgui imports never load without ImGuiMC present.</p>
 */
public final class DUPlanetEditor {
    private DUPlanetEditor() {}

    /** Include a planet's tab when its direction from the camera is within ~78° of the look vector. */
    private static final float VIEW_DOT = 0.2f;
    /** Default spawn/teleport distance in front of the camera. */
    private static final float SPAWN_DISTANCE = 300f;

    private static Identifier selectedId = null;

    // Text fields can't be reloaded every frame (it would fight typing), so they persist and are
    // re-synced only when the active tab changes (tracked by textBufId).
    private static final ImString textureBuf = new ImString(256);
    private static final ImString ringTexBuf = new ImString(256);
    private static String textBufId = null;

    public static void render() {
        if (!ImGui.begin("Planets")) {
            ImGui.end();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ImGui.text("Load a world to edit planets.");
            ImGui.end();
            return;
        }

        // Master enable so the editor is usable even if planets were toggled off elsewhere.
        if (ImGui.button(DUPlanetRenderer.isEnabled() ? "Rendering: ON" : "Rendering: OFF")) {
            DUPlanetRenderer.setEnabled(!DUPlanetRenderer.isEnabled());
        }
        ImGui.sameLine();
        ImGui.text("Loaded: " + PlanetRegistry.size());

        String approach = DUPlanetApproach.lastStatus();
        if (!approach.isEmpty()) {
            ImGui.textDisabled("Last approach (stub): " + approach);
        }

        renderToolbar(mc);
        ImGui.separator();

        List<PlanetDef> inView = planetsInView(mc);
        if (inView.isEmpty()) {
            ImGui.text("No planets in view.");
            ImGui.text("Use 'New' to spawn one in front of the camera, or look toward an existing planet.");
        } else if (ImGui.beginTabBar("du_planet_tabs")) {
            for (PlanetDef def : inView) {
                if (ImGui.beginTabItem(def.id().toString())) {
                    selectedId = def.id();
                    renderTab(mc, def);
                    ImGui.endTabItem();
                }
            }
            ImGui.endTabBar();
        }

        ImGui.end();
    }

    private static void renderToolbar(Minecraft mc) {
        if (ImGui.button("New")) {
            PlanetDef created = PlanetDef.createDefault(freshId("planet"), inFront(mc, SPAWN_DISTANCE))
                    .withTexture(Identifier.fromNamespaceAndPath("dragonuniverse", "textures/planet/terra.png"));
            PlanetRegistry.save(created);
            selectedId = created.id();
            textBufId = null;
        }

        boolean hasSel = selectedId != null && PlanetRegistry.contains(selectedId);
        ImGui.sameLine();
        if (!hasSel) ImGui.beginDisabled();
        if (ImGui.button("Duplicate")) {
            PlanetRegistry.get(selectedId).ifPresent(src -> {
                Identifier id = freshId(selectedId.getPath() + "_copy");
                Vector3f pos = new Vector3f(src.position()).add(src.radius() * 2.5f, 0f, 0f);
                PlanetDef copy = src.withId(id).withPosition(pos);
                PlanetRegistry.save(copy);
                selectedId = id;
                textBufId = null;
            });
        }
        ImGui.sameLine();
        if (ImGui.button("Delete")) {
            PlanetRegistry.delete(selectedId);
            selectedId = null;
            textBufId = null;
        }
        ImGui.sameLine();
        if (ImGui.button("Save")) {
            PlanetRegistry.get(selectedId).ifPresent(PlanetRegistry::save);
        }
        if (!hasSel) ImGui.endDisabled();

        ImGui.sameLine();
        if (ImGui.button("Save All")) {
            for (PlanetDef d : PlanetRegistry.all()) {
                PlanetRegistry.save(d);
            }
        }
        ImGui.sameLine();
        if (ImGui.button("Reload")) {
            PlanetRegistry.loadAll();
            selectedId = null;
            textBufId = null;
        }
    }

    private static void renderTab(Minecraft mc, PlanetDef def) {
        // Re-sync the persistent text buffers when the active tab changes (not every frame).
        String idStr = def.id().toString();
        if (!idStr.equals(textBufId)) {
            textureBuf.set(def.texture().toString());
            ringTexBuf.set(def.ring().map(r -> r.texture().toString())
                    .orElse("dragonuniverse:textures/planet/terra_ring.png"));
            textBufId = idStr;
        }

        PlanetDef cur = def;

        // --- Identity (read-only) ---
        ImGui.textDisabled("id: " + idStr + "  (immutable; use Duplicate to fork)");

        // --- Surface ---
        if (ImGui.inputText("Texture", textureBuf)) {
            Identifier tid = tryId(textureBuf.get());
            if (tid != null) {
                cur = commit(cur.withTexture(tid));
            }
        }
        ImGui.textDisabled("equirectangular 2:1 map");

        float[] radius = { cur.radius() };
        if (ImGui.dragFloat("Radius", radius, 0.5f, 1f, 100000f)) {
            cur = commit(cur.withRadius(Math.max(1f, radius[0])));
        }

        float[] pos = { cur.position().x, cur.position().y, cur.position().z };
        if (ImGui.dragFloat3("Position", pos, 1.0f)) {
            cur = commit(cur.withPosition(new Vector3f(pos[0], pos[1], pos[2])));
        }
        ImGui.sameLine();
        if (ImGui.button("Move in front")) {
            cur = commit(cur.withPosition(inFront(mc, SPAWN_DISTANCE)));
        }

        // --- Atmosphere ---
        if (ImGui.collapsingHeader("Atmosphere")) {
            AtmosphereDef a = cur.atmosphere();
            float[] size = { a.size() };
            float[] falloff = { a.falloff() };
            float[] density = { a.density() };
            float[] brightness = { a.brightnessFactor() };
            float[] densityFactor = { a.densityFactor() };
            float[] scatter = { a.scatterCoefficients().x, a.scatterCoefficients().y, a.scatterCoefficients().z };

            boolean changed = false;
            changed |= ImGui.sliderFloat("Shell size", size, 0f, 1f);
            changed |= ImGui.sliderFloat("Falloff", falloff, 0.01f, 16f);
            changed |= ImGui.sliderFloat("Density", density, 0f, 4f);
            changed |= ImGui.colorEdit3("Scatter (Rayleigh RGB)", scatter);
            changed |= ImGui.sliderFloat("Brightness factor", brightness, 0f, 8f);
            changed |= ImGui.sliderFloat("Density factor", densityFactor, 0f, 4f);

            if (changed) {
                cur = commit(cur.withAtmosphere(new AtmosphereDef(
                        size[0], falloff[0], density[0],
                        new Vector3f(scatter[0], scatter[1], scatter[2]),
                        brightness[0], densityFactor[0])));
            }
        }

        // --- Ring ---
        if (ImGui.collapsingHeader("Ring")) {
            Optional<RingDef> ring = cur.ring();
            if (ring.isPresent()) {
                RingDef rd = ring.get();
                if (ImGui.inputText("Ring texture", ringTexBuf)) {
                    Identifier rtid = tryId(ringTexBuf.get());
                    if (rtid != null) {
                        cur = commit(cur.withRing(Optional.of(
                                new RingDef(rtid, rd.innerRadius(), rd.outerRadius(), rd.tilt()))));
                        rd = cur.ring().orElse(rd);
                    }
                }
                ImGui.textDisabled("1D radial strip (inner->outer)");

                float[] inner = { rd.innerRadius() };
                float[] outer = { rd.outerRadius() };
                float[] tilt = { rd.tilt().orElse(0f) };
                boolean changed = false;
                changed |= ImGui.dragFloat("Inner radius", inner, 0.5f, 0f, 100000f);
                changed |= ImGui.dragFloat("Outer radius", outer, 0.5f, 0f, 100000f);
                changed |= ImGui.sliderFloat("Tilt (deg)", tilt, -90f, 90f);
                if (changed) {
                    float in = Math.max(0f, inner[0]);
                    float out = Math.max(in + 0.01f, outer[0]);
                    cur = commit(cur.withRing(Optional.of(
                            new RingDef(rd.texture(), in, out, Optional.of(tilt[0])))));
                }

                if (ImGui.button("Remove ring")) {
                    cur = commit(cur.withRing(Optional.empty()));
                }
            } else {
                if (ImGui.button("Attach ring")) {
                    Identifier rtid = tryId(ringTexBuf.get());
                    Identifier ringTex = rtid != null ? rtid
                            : Identifier.fromNamespaceAndPath("dragonuniverse", "textures/planet/terra_ring.png");
                    float in = cur.radius() * 1.4f;
                    float out = cur.radius() * 2.3f;
                    cur = commit(cur.withRing(Optional.of(new RingDef(ringTex, in, out, Optional.of(0f)))));
                }
            }
        }

        ImGui.separator();
        ImGui.textDisabled("Edits preview live. 'Save' writes JSON; 'Reload' reverts to disk.");
    }

    private static PlanetDef commit(PlanetDef def) {
        PlanetRegistry.putTransient(def);
        return def;
    }

    private static List<PlanetDef> planetsInView(Minecraft mc) {
        var cam = mc.gameRenderer.getMainCamera();
        Vector3f fwd = new Vector3f(cam.forwardVector());
        Vec3 camPos = cam.position();

        List<PlanetDef> out = new ArrayList<>();
        for (PlanetDef d : PlanetRegistry.all()) {
            Vector3f to = new Vector3f(
                    d.position().x - (float) camPos.x,
                    d.position().y - (float) camPos.y,
                    d.position().z - (float) camPos.z);
            if (to.lengthSquared() < 1.0e-4f || to.normalize().dot(fwd) >= VIEW_DOT) {
                out.add(d);
            }
        }
        return out;
    }

    private static Vector3f inFront(Minecraft mc, float distance) {
        var cam = mc.gameRenderer.getMainCamera();
        Vector3f fwd = new Vector3f(cam.forwardVector());
        Vec3 p = cam.position();
        return new Vector3f((float) p.x, (float) p.y, (float) p.z).add(fwd.mul(distance));
    }

    private static Identifier freshId(String base) {
        Identifier id = Identifier.fromNamespaceAndPath("dragonuniverse", base);
        int n = 1;
        while (PlanetRegistry.contains(id)) {
            id = Identifier.fromNamespaceAndPath("dragonuniverse", base + "_" + n++);
        }
        return id;
    }

    private static Identifier tryId(String s) {
        try {
            return Identifier.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}

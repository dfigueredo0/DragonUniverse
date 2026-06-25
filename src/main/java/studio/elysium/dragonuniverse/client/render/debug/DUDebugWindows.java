package studio.elysium.dragonuniverse.client.render.debug;

import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import studio.elysium.dragonuniverse.client.render.vfx.DUVFX;
import studio.elysium.dragonuniverse.client.render.DUScreenShake;
import studio.elysium.dragonuniverse.client.render.core.DUFrameData;
import studio.elysium.dragonuniverse.client.render.post.DUPostChain;
import studio.elysium.dragonuniverse.client.render.vfx.beam.*;
import studio.elysium.dragonuniverse.client.render.vfx.bolt.BoltConfig;
import studio.elysium.dragonuniverse.client.render.vfx.bolt.DUBolts;
import studio.elysium.dragonuniverse.client.render.vfx.DUEmissiveDemo;
import studio.elysium.dragonuniverse.client.render.vfx.DUWorldRenderer;

import java.util.ArrayList;
import java.util.List;

public final class DUDebugWindows {
    private static final float[] fog = { 0F };
    private static final float[] bloom = { 1F };
    private static final float[] sky = { 1F, 1F, 1F };
    private static final float[] testColor = { 0.2F, 0.6F, 1.0F };
    private static final float[] testWidth = { 0.25F };

    private static final float[] emColor = { 0.4F, 0.8F, 1.0F };
    private static final float[] emSize = { 0.6F };
    private static final float[] emStrength = { 1.5F };
    private static final float[] emPulseSpeed = { 0.15F };

    private static final int[] vfxCount = { 16 };
    private static final float[] shakeAmount = { 1.5F };
    private static final int[] shakeDuration = { 12 };

    private static final int[] vortexCount = { 80 };
    private static final float[] vortexRadius = { 2.5F };
    private static final float[] vortexOrbitSpeed = { 0.25F };
    private static final float[] vortexBurstSpeed = { 0.25F };

    private static final ImInt beamType = new ImInt(1); // 0 straight, 1 spiral, 2 sine, 3 wobble
    private static final String[] BEAM_TYPES = { "Straight", "Spiral", "Sine", "Wobble" };
    private static final float[] beamWidth = { 0.12F };
    private static final float[] beamColor = { 0.5F, 0.8F, 1.0F };
    private static final int[] beamLifetime = { 40 };
    private static final float[] beamLength = { 16F };
    private static final float[] beamRadius = { 0.4F };
    private static final float[] beamTurns = { 4F };
    private static final float[] beamAmplitude = { 0.5F };
    private static final float[] beamFrequency = { 3F };
    private static final float[] beamAnim = { 0.4F };

    private static final int[] boltSegments = { 6 };
    private static final float[] boltDisp = { 1.2F };
    private static final float[] boltFork = { 0.18F };
    private static final int[] boltForkDepth = { 3 };
    private static final float[] boltWidth = { 0.09F };
    private static final float[] boltColor = { 0.6F, 0.8F, 1.0F };
    private static final int[] boltLifetime = { 8 };

    private static final ImBoolean lockSun = new ImBoolean(DUFrameData.lockSunDir);
    private static final ImBoolean postFx = new ImBoolean(DUPostChain.isEnabled());
    private static final ImBoolean emBase = new ImBoolean(DUEmissiveDemo.showBase);
    private static final ImBoolean emEmissive = new ImBoolean(DUEmissiveDemo.showEmissive);
    private static final ImBoolean emGlow = new ImBoolean(DUEmissiveDemo.showGlow);

    private DUDebugWindows() {}

    public static void render() {
        if (ImGui.begin("Dragon Universe - Render Debug")) {
            if (ImGui.collapsingHeader("Post FX")) {
                if (ImGui.checkbox("Enable post chain", postFx)) {
                    DUPostChain.setEnabled(postFx.get());
                }
                ImGui.text("Chain: fog -> bright -> blur H -> blur V -> composite.");
                ImGui.text("Drive it with Fog / Bloom / Sky tint in 'Per-Frame UBO' below:");
                ImGui.bulletText("Bloom intensity -> bloom strength + pulse");
                ImGui.bulletText("Fog density -> depth fog toward sky tint");
                ImGui.bulletText("Sky tint -> overall color grade");
            }

            DUTargetViewPanel.render();

            if (ImGui.collapsingHeader("Per-Frame UBO")) {
                ImGui.text(String.format("time = %.2f", currentTime()));
                ImGui.text(String.format("sunDir = (%.2f, %.2f, %.2f)",
                        DUFrameData.sunDir.x, DUFrameData.sunDir.y, DUFrameData.sunDir.z));
                if (ImGui.checkbox("Lock sun dir", lockSun)) {
                    DUFrameData.lockSunDir = lockSun.get();
                }

                if (ImGui.sliderFloat("Fog density", fog, 0f, 1f)) {
                    DUFrameData.fogDensity = fog[0];
                }
                if (ImGui.sliderFloat("Bloom intensity", bloom, 0f, 4f)) {
                    DUFrameData.bloomIntensity = bloom[0];
                }
                if (ImGui.colorEdit3("Sky tint", sky)) {
                    DUFrameData.skyTint.set(sky[0], sky[1], sky[2]);
                }
            }

            if (ImGui.collapsingHeader("VFX Test")) {
                ImGui.colorEdit3("Trail color", testColor);
                ImGui.sliderFloat("Trail width", testWidth, 0.02f, 1.0f);

                if (ImGui.button("Spawn test trail in front of camera")) {
                    spawnTestTrail();
                }
                ImGui.sameLine();
                if (ImGui.button("Clear trails")) {
                    DUWorldRenderer.clearTrails();
                }
                ImGui.text("Active trails: " + DUWorldRenderer.getTrails().size());
            }

            if (ImGui.collapsingHeader("Emissive Demo")) {
                if (ImGui.checkbox("Base (lit)", emBase)) {
                    DUEmissiveDemo.showBase = emBase.get();
                }
                ImGui.sameLine();
                if (ImGui.checkbox("Emissive", emEmissive)) {
                    DUEmissiveDemo.showEmissive = emEmissive.get();
                }
                ImGui.sameLine();
                if (ImGui.checkbox("Glow halo", emGlow)) {
                    DUEmissiveDemo.showGlow = emGlow.get();
                }

                ImGui.colorEdit3("Emissive color", emColor);
                if (ImGui.sliderFloat("Cube size", emSize, 0.1f, 2.0f)) {
                    // Live-resize already-spawned cubes (not just future spawns).
                    for (DUEmissiveDemo.DemoCube cube : DUEmissiveDemo.getCubes()) {
                        cube.size = emSize[0];
                    }
                }
                if (ImGui.sliderFloat("Emissive strength", emStrength, 0f, 4f)) {
                    DUEmissiveDemo.emissiveStrength = emStrength[0];
                }
                if (ImGui.sliderFloat("Pulse speed", emPulseSpeed, 0f, 1.0f)) {
                    DUEmissiveDemo.pulseSpeed = emPulseSpeed[0];
                }

                if (ImGui.button("Spawn cube in front of camera")) {
                    spawnDemoCube();
                }
                ImGui.sameLine();
                if (ImGui.button("Clear cubes")) {
                    DUEmissiveDemo.clearCubes();
                }
                ImGui.text("Active cubes: " + DUEmissiveDemo.getCubes().size());
            }

            if (ImGui.collapsingHeader("VFX / Screenshake")) {
                ImGui.sliderInt("Particle count", vfxCount, 1, 100);
                if (ImGui.button("Sparks")) spawnVfx(0);
                ImGui.sameLine();
                if (ImGui.button("Ichor splash")) spawnVfx(1);
                if (ImGui.button("Space dust")) spawnVfx(2);
                ImGui.sameLine();
                if (ImGui.button("Star twinkle")) spawnVfx(3);

                ImGui.separator();
                ImGui.sliderFloat("Shake intensity", shakeAmount, 0f, 5f);
                ImGui.sliderInt("Shake duration (ticks)", shakeDuration, 1, 60);
                if (ImGui.button("Screenshake")) {
                    DUScreenShake.shake(shakeAmount[0], shakeDuration[0]);
                }

                ImGui.separator();
                ImGui.text("Vortex");
                ImGui.sliderInt("Vortex count", vortexCount, 1, 300);
                ImGui.sliderFloat("Vortex radius", vortexRadius, 0.5f, 8f);
                ImGui.sliderFloat("Orbit speed", vortexOrbitSpeed, 0f, 0.8f);
                ImGui.sliderFloat("Burst speed", vortexBurstSpeed, 0f, 1.0f);
                if (ImGui.button("Implode vortex in front of camera")) {
                    spawnVortex();
                }
            }

            if (ImGui.collapsingHeader("Bolts")) {
                ImGui.sliderInt("Subdivisions", boltSegments, 1, 7);
                ImGui.sliderFloat("Displacement", boltDisp, 0f, 4f);
                ImGui.sliderFloat("Fork probability", boltFork, 0f, 1f);
                ImGui.sliderInt("Max fork depth", boltForkDepth, 0, 5);
                ImGui.sliderFloat("Base width", boltWidth, 0.02f, 0.4f);
                ImGui.colorEdit3("Bolt color", boltColor);
                ImGui.sliderInt("Lifetime (ticks)", boltLifetime, 2, 40);

                if (ImGui.button("Strike in front of camera")) {
                    spawnBolt();
                }
                ImGui.sameLine();
                if (ImGui.button("Clear bolts")) {
                    DUBolts.clear();
                }
                ImGui.text("Active bolts: " + DUBolts.count());
            }

            if (ImGui.collapsingHeader("Beams")) {
                ImGui.combo("Path", beamType, BEAM_TYPES);
                ImGui.sliderFloat("Beam width", beamWidth, 0.02f, 0.6f);
                ImGui.colorEdit3("Beam color", beamColor);
                ImGui.sliderInt("Beam lifetime", beamLifetime, 2, 200);
                ImGui.sliderFloat("Beam length", beamLength, 2f, 48f);

                int ty = beamType.get();
                if (ty == 1) {
                    ImGui.sliderFloat("Spiral radius", beamRadius, 0f, 2f);
                    ImGui.sliderFloat("Spiral turns", beamTurns, 0f, 16f);
                    ImGui.sliderFloat("Anim speed", beamAnim, 0f, 2f);
                } else if (ty == 2) {
                    ImGui.sliderFloat("Sine amplitude", beamAmplitude, 0f, 3f);
                    ImGui.sliderFloat("Sine frequency", beamFrequency, 0f, 12f);
                    ImGui.sliderFloat("Anim speed", beamAnim, 0f, 2f);
                } else if (ty == 3) {
                    ImGui.sliderFloat("Wobble amplitude", beamAmplitude, 0f, 3f);
                    ImGui.sliderFloat("Wobble frequency", beamFrequency, 0f, 12f);
                }

                if (ImGui.button("Fire beam from camera")) {
                    spawnBeam();
                }
                ImGui.sameLine();
                if (ImGui.button("Clear beams")) {
                    DUBeams.clear();
                }
                ImGui.text("Active beams: " + DUBeams.count());
            }
        }
        ImGui.end();
    }

    private static void spawnBeam() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var cam = mc.gameRenderer.getMainCamera();
        Vector3f fwd = new Vector3f(cam.forwardVector());

        Vector3f from = new Vector3f(
                (float) cam.position().x, (float) cam.position().y, (float) cam.position().z)
                .add(new Vector3f(fwd).mul(1.0f));
        Vector3f to = new Vector3f(
                (float) cam.position().x, (float) cam.position().y, (float) cam.position().z)
                .add(new Vector3f(fwd).mul(beamLength[0]));

        BeamPath path = switch (beamType.get()) {
            case 1 -> new SpiralBeam(beamRadius[0], beamTurns[0], beamAnim[0]);
            case 2 -> new SineBeam(beamAmplitude[0], beamFrequency[0], beamAnim[0]);
            case 3 -> new WobbleBeam(beamAmplitude[0], beamFrequency[0]);
            default -> StraightBeam.INSTANCE;
        };
        BeamConfig cfg = BeamConfig.builder()
                .segments(48)
                .baseWidth(beamWidth[0])
                .color(beamColor[0], beamColor[1], beamColor[2])
                .lifetime(beamLifetime[0])
                .build();
        DUBeams.spawn(from, to, path, cfg);
    }

    private static void spawnBolt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var cam = mc.gameRenderer.getMainCamera();
        Vector3f fwd = new Vector3f(cam.forwardVector());
        fwd.y = 0f;
        if (fwd.lengthSquared() < 1.0e-4f) {
            fwd.set(0f, 0f, 1f);
        }
        fwd.normalize();

        float bx = (float) cam.position().x + fwd.x * 8f;
        float by = (float) cam.position().y;
        float bz = (float) cam.position().z + fwd.z * 8f;

        Vector3f end = new Vector3f(bx, by, bz);
        Vector3f start = new Vector3f(bx, by + 12f, bz);

        BoltConfig cfg = BoltConfig.builder()
                .segments(boltSegments[0])
                .displacement(boltDisp[0])
                .forkProbability(boltFork[0])
                .maxForkDepth(boltForkDepth[0])
                .baseWidth(boltWidth[0])
                .color(boltColor[0], boltColor[1], boltColor[2])
                .lifetime(boltLifetime[0])
                .build();
        DUBolts.spawn(start, end, cfg);
    }

    private static void spawnVfx(int kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var cam = mc.gameRenderer.getMainCamera();
        Vector3f fwd = new Vector3f(cam.forwardVector());
        double x = cam.position().x + fwd.x * 3.0;
        double y = cam.position().y + fwd.y * 3.0;
        double z = cam.position().z + fwd.z * 3.0;
        int n = vfxCount[0];
        switch (kind) {
            case 0 -> DUVFX.sparks(mc.level, x, y, z, n);
            case 1 -> DUVFX.ichorSplash(mc.level, x, y, z, n);
            case 2 -> DUVFX.spaceDust(mc.level, x, y, z, n);
            case 3 -> DUVFX.pocketStarTwinkle(mc.level, x, y, z, n);
            default -> { }
        }
    }

    private static void spawnVortex() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var cam = mc.gameRenderer.getMainCamera();
        Vector3f fwd = new Vector3f(cam.forwardVector());
        double cx = cam.position().x + fwd.x * 5.0;
        double cy = cam.position().y + fwd.y * 5.0;
        double cz = cam.position().z + fwd.z * 5.0;
        DUVFX.implodeVortex(mc.level, cx, cy, cz, vortexCount[0], vortexRadius[0],
                vortexOrbitSpeed[0], vortexBurstSpeed[0]);
    }

    private static void spawnDemoCube() {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f fwd = new Vector3f(cam.forwardVector());
        Vector3f center = new Vector3f(
                (float) cam.position().x,
                (float) cam.position().y,
                (float) cam.position().z).add(fwd.mul(4.0f));
        DUEmissiveDemo.addCube(new DUEmissiveDemo.DemoCube(
                center, emSize[0], emColor[0], emColor[1], emColor[2]));
    }

    private static float currentTime() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? 0f : mc.level.getGameTime();
    }

    private static void spawnTestTrail() {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f base = new Vector3f(
                (float) cam.position().x,
                (float) cam.position().y,
                (float) cam.position().z);
        Vector3f fwd = new Vector3f(cam.forwardVector());

        List<Vector3f> pts = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            float d = 2.0f + i * 0.4f;
            // gentle sine wiggle so the ribbon shape is visible
            Vector3f p = new Vector3f(base).add(new Vector3f(fwd).mul(d));
            p.y += (float) Math.sin(i * 0.6f) * 0.3f;
            pts.add(p);
        }
        DUWorldRenderer.addTrail(new DUWorldRenderer.ActiveTrail(
                pts, testWidth[0], testColor[0], testColor[1], testColor[2],
                1.0f, 0.0f));
    }
}


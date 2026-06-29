package studio.elysium.dragonuniverse.client.render.post;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.ArrayList;

/**
 * Deferred GPU-resource disposal for the look-stack targets.
 *
 * <p><b>Why:</b> ImGuiMC's {@code getTexture(GpuTextureView)} hands ImGui the {@link GpuTextureView}
 * object itself (a mixin makes the view implement its texture-provider interface), and ImGui reads the
 * GL id from that exact object during its <i>deferred</i> draw at {@code endFrame} — after our render
 * stages have run. If a window resize makes us reallocate a target (closing the old view) before that
 * deferred draw, ImGui draws a closed view and the game crashes with
 * "Texture view ... has been closed!".</p>
 *
 * <p>So instead of closing a retired texture/view immediately, we park it here and close it a few
 * frames later — long after ImGui has finished drawing the frame that referenced it. {@link #tick()}
 * must be pumped once per frame (see {@code DUWorldRenderer}).</p>
 */
final class DUGpuTrash {
    /** Frames to keep a retired resource alive before closing. ImGui draws within 1 frame; 3 is safe. */
    private static final int TTL_FRAMES = 3;

    private static final class Entry {
        final GpuTexture texture;
        final GpuTextureView view;
        int ttl;

        Entry(GpuTexture texture, GpuTextureView view, int ttl) {
            this.texture = texture;
            this.view = view;
            this.ttl = ttl;
        }
    }

    private static final ArrayList<Entry> ENTRIES = new ArrayList<>();

    private DUGpuTrash() {}

    /** Park a texture and/or its view for delayed closing. Either may be null. */
    static void retire(GpuTexture texture, GpuTextureView view) {
        if (texture == null && view == null) {
            return;
        }
        ENTRIES.add(new Entry(texture, view, TTL_FRAMES));
    }

    /** Age every parked resource by one frame; close those whose grace period has elapsed. */
    static void tick() {
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            Entry e = ENTRIES.get(i);
            if (--e.ttl <= 0) {
                closeEntry(e);
                ENTRIES.remove(i);
            }
        }
    }

    private static void closeEntry(Entry e) {
        if (e.view != null) {
            e.view.close();
        }
        if (e.texture != null) {
            e.texture.close();
        }
    }
}

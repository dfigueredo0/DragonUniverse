package studio.elysium.dragonuniverse.world.planet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Live, in-memory registry of {@link PlanetDef}s backed by JSON files under
 * {@code config/dragonuniverse/planets/}. Load-all on startup, save-on-edit.
 *
 * <p>Dist-agnostic on purpose: the orbital renderer and ImGui editor (client) read/mutate this,
 * and a later dimension-export phase can read the same files server-side. The persisted JSON is the
 * single source of truth shared across both worlds — see {@link PlanetDef}.</p>
 *
 * <p>Not heavily synchronized: all editing happens on the client thread and load-all runs once at
 * setup. Reads are cheap snapshots ({@link #all()} copies).</p>
 */
public final class PlanetRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Map<Identifier, PlanetDef> PLANETS = new LinkedHashMap<>();
    private static boolean loaded = false;

    private PlanetRegistry() {}

    /** {@code config/dragonuniverse/planets/}, created lazily. */
    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(DragonUniverse.MODID).resolve("planets");
    }

    /** File for a body. Filename is derived from the id; the id inside the JSON stays authoritative. */
    private static Path fileFor(Identifier id) {
        String name = (id.getNamespace() + "_" + id.getPath()).replaceAll("[^a-zA-Z0-9._-]", "_");
        return directory().resolve(name + ".json");
    }

    /** Read every {@code *.json} in the planets dir into the live map. Safe to call once at startup. */
    public static synchronized void loadAll() {
        PLANETS.clear();
        Path dir = directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            DragonUniverse.LOGGER.error("[Dragon Universe] Could not create planets dir {}", dir, e);
            loaded = true;
            return;
        }

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(PlanetRegistry::loadOne);
        } catch (IOException e) {
            DragonUniverse.LOGGER.error("[Dragon Universe] Failed listing planets dir {}", dir, e);
        }
        loaded = true;
        DragonUniverse.LOGGER.info("[Dragon Universe] Loaded {} planet definition(s).", PLANETS.size());
    }

    private static void loadOne(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement json = JsonParser.parseReader(reader);
            PlanetDef.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> DragonUniverse.LOGGER.error("[Dragon Universe] Bad planet JSON {}: {}", path, err))
                    .ifPresent(def -> PLANETS.put(def.id(), def));
        } catch (Exception e) {
            DragonUniverse.LOGGER.error("[Dragon Universe] Failed reading planet {}", path, e);
        }
    }

    /** Insert/replace a body in memory AND persist it to disk (save-on-edit). */
    public static synchronized void save(PlanetDef def) {
        PLANETS.put(def.id(), def);
        Path path = fileFor(def.id());
        PlanetDef.CODEC.encodeStart(JsonOps.INSTANCE, def)
                .resultOrPartial(err -> DragonUniverse.LOGGER.error("[Dragon Universe] Cannot encode planet {}: {}", def.id(), err))
                .ifPresent(json -> {
                    try {
                        Files.createDirectories(path.getParent());
                        Files.writeString(path, GSON.toJson(json));
                    } catch (IOException e) {
                        DragonUniverse.LOGGER.error("[Dragon Universe] Failed writing planet {}", path, e);
                    }
                });
    }

    /** Remove a body from memory and delete its JSON file. */
    public static synchronized void delete(Identifier id) {
        PLANETS.remove(id);
        try {
            Files.deleteIfExists(fileFor(id));
        } catch (IOException e) {
            DragonUniverse.LOGGER.error("[Dragon Universe] Failed deleting planet {}", id, e);
        }
    }

    /** Put into memory only (no disk write) — used for transient edits the editor batches before Save. */
    public static synchronized void putTransient(PlanetDef def) {
        PLANETS.put(def.id(), def);
    }

    public static synchronized Collection<PlanetDef> all() {
        return java.util.List.copyOf(PLANETS.values());
    }

    public static synchronized Optional<PlanetDef> get(Identifier id) {
        return Optional.ofNullable(PLANETS.get(id));
    }

    public static synchronized boolean contains(Identifier id) {
        return PLANETS.containsKey(id);
    }

    public static synchronized int size() {
        return PLANETS.size();
    }

    public static boolean isLoaded() {
        return loaded;
    }
}

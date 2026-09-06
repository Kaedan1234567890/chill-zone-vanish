package com.chillzone.homes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class HomeStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Type FILE_TYPE = new TypeToken<Map<UUID, List<Home>>>() {}.getType();
    private final Path file;
    private final Map<UUID, List<Home>> homes = new HashMap<>();

    private HomeStore(Path file) { this.file = file; }

    public static HomeStore load(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("chill_zone_homes.json");
        HomeStore store = new HomeStore(file);
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                Map<UUID, List<Home>> loaded = GSON.fromJson(reader, FILE_TYPE);
                if (loaded != null) loaded.forEach((k,v) -> store.homes.put(k, new ArrayList<>(v)));
            } catch (Exception e) {
                ChillZoneHomes.LOGGER.error("Failed to read {}", file, e);
            }
        }
        return store;
    }

    public List<Home> getHomes(UUID uuid) {
        return homes.computeIfAbsent(uuid, u -> new ArrayList<>());
    }

    public Home getHome(UUID uuid, int index) {
        List<Home> list = getHomes(uuid);
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    public Home findHomeByName(UUID uuid, String requested) {
        if (requested == null) return null;
        String wanted = requested.strip();
        for (Home home : getHomes(uuid)) {
            if (home.name().equalsIgnoreCase(wanted)) return home;
        }
        return null;
    }

    public void addHome(UUID uuid, Home home) { getHomes(uuid).add(home); save(); }
    public void setHome(UUID uuid, int index, Home home) { getHomes(uuid).set(index, home); save(); }
    public void deleteHome(UUID uuid, int index) { getHomes(uuid).remove(index); save(); }

    public synchronized void save() {
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) { GSON.toJson(homes, FILE_TYPE, writer); }
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (Exception ignored) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception e) {
            ChillZoneHomes.LOGGER.error("Failed to save homes to {}", file, e);
        }
    }
}

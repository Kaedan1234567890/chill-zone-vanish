package com.chillzone.homes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public int defaultHomes = 3;
    public int maximumVisibleSlots = 28;
    public String luckPermsMetaKey = "homes-max";
    public boolean showLockedSlots = true;
    public boolean allowCrossDimensionTeleport = true;

    public static Config load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("chill-zone-homes.json");
        try {
            if (Files.exists(path)) {
                try (Reader r = Files.newBufferedReader(path)) {
                    Config cfg = GSON.fromJson(r, Config.class);
                    if (cfg != null) {
                        cfg = sanitize(cfg);
                        // 0.3.0 raises the old alpha default from 18 to 24 without overriding custom larger values.
                        if (cfg.maximumVisibleSlots == 18 || cfg.maximumVisibleSlots == 24) cfg.maximumVisibleSlots = 28;
                        try (Writer w = Files.newBufferedWriter(path)) { GSON.toJson(cfg, w); }
                        return cfg;
                    }
                }
            }
            Config cfg = new Config();
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) { GSON.toJson(cfg, w); }
            return cfg;
        } catch (Exception e) {
            ChillZoneHomes.LOGGER.error("Could not load config; using defaults", e);
            return new Config();
        }
    }

    private static Config sanitize(Config c) {
        c.defaultHomes = Math.max(1, Math.min(28, c.defaultHomes));
        c.maximumVisibleSlots = Math.max(c.defaultHomes, Math.min(28, c.maximumVisibleSlots));
        if (c.luckPermsMetaKey == null || c.luckPermsMetaKey.isBlank()) c.luckPermsMetaKey = "homes-max";
        return c;
    }
}

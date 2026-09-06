package com.chillzone.homes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent shard balances and shard-purchased home allowance. */
public final class ShardStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<UUID, Record>>(){}.getType();
    private final Path path;
    private final Map<UUID, Record> data;

    public static final class Record {
        public int shards = 0;
        public int purchasedHomeLimit = 3;
        /** Last username observed for this UUID. Added in fix8; old data remains compatible. */
        public String lastKnownName = "";
        /** Offset applied to Minecraft's vanilla PLAY_TIME statistic for admin adjustments. */
        public long playTimeOffsetTicks = 0L;
        /** Last effective play time seen for this player, so /baltop can show offline players. */
        public long lastKnownPlayTicks = 0L;
    }

    public record BalanceEntry(UUID uuid, String name, int shards, long playTicks) {}

    private ShardStore(Path path, Map<UUID, Record> data) {
        this.path = path;
        this.data = data == null ? new HashMap<>() : data;
    }

    public static ShardStore load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("chill-zone-shards.json");
        try {
            if (Files.exists(path)) {
                try (Reader r = Files.newBufferedReader(path)) {
                    Map<UUID, Record> loaded = GSON.fromJson(r, TYPE);
                    ShardStore store = new ShardStore(path, loaded);
                    store.backfillNamesFromUserCache();
                    return store;
                }
            }
        } catch (Exception e) {
            ChillZoneHomes.LOGGER.error("Could not load shard data", e);
        }
        ShardStore store = new ShardStore(path, new HashMap<>());
        store.backfillNamesFromUserCache();
        return store;
    }


    /**
     * Backfill usernames for older shard entries from the vanilla server usercache.json.
     * This lets /baltop show offline names even when those players earned Shards before
     * username tracking was added to this mod.
     */
    private synchronized void backfillNamesFromUserCache() {
        Path userCache = FabricLoader.getInstance().getGameDir().resolve("usercache.json");
        if (!Files.exists(userCache)) return;

        boolean changed = false;
        try (Reader reader = Files.newBufferedReader(userCache)) {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            if (root == null || !root.isJsonArray()) return;
            JsonArray array = root.getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("uuid") || !obj.has("name")) continue;
                try {
                    UUID id = UUID.fromString(obj.get("uuid").getAsString());
                    String name = obj.get("name").getAsString();
                    Record existing = data.get(id);
                    if (existing == null || name == null || name.isBlank()) continue;
                    if (existing.lastKnownName == null || existing.lastKnownName.isBlank()
                        || existing.lastKnownName.startsWith("Unknown-")) {
                        existing.lastKnownName = name;
                        changed = true;
                    }
                } catch (Exception ignored) {
                    // Ignore malformed/expired cache rows and continue with the rest.
                }
            }
        } catch (Exception e) {
            ChillZoneHomes.LOGGER.warn("Could not backfill shard usernames from usercache.json", e);
        }
        if (changed) save();
    }

    private Record record(UUID id) {
        Record r = data.computeIfAbsent(id, k -> new Record());
        r.shards = Math.max(0, r.shards);
        r.purchasedHomeLimit = Math.max(3, Math.min(28, r.purchasedHomeLimit));
        return r;
    }


    /** Remember the latest username so /bal can find players even while they are offline. */
    public synchronized void rememberPlayer(UUID id, String name) {
        if (id == null || name == null || name.isBlank()) return;
        Record r = record(id);
        if (!name.equals(r.lastKnownName)) {
            r.lastKnownName = name;
            save();
        }
    }

    /** Case-insensitive lookup of a player who has been observed by this mod. */
    public synchronized BalanceEntry findByName(String name) {
        if (name == null || name.isBlank()) return null;
        for (Map.Entry<UUID, Record> e : data.entrySet()) {
            Record r = e.getValue();
            if (r != null && r.lastKnownName != null && r.lastKnownName.equalsIgnoreCase(name)) {
                return new BalanceEntry(e.getKey(), r.lastKnownName, Math.max(0, r.shards), Math.max(0L, r.lastKnownPlayTicks));
            }
        }
        return null;
    }

    /** Richest first; ties are alphabetical by last known username. */
    public synchronized List<BalanceEntry> rankedBalances() {
        List<BalanceEntry> out = new ArrayList<>();
        for (Map.Entry<UUID, Record> e : data.entrySet()) {
            Record r = e.getValue();
            if (r == null) continue;
            String name = r.lastKnownName == null || r.lastKnownName.isBlank()
                ? "Unknown-" + e.getKey().toString().substring(0, 8)
                : r.lastKnownName;
            int shardBalance = Math.max(0, r.shards);
            long playTicks = Math.max(0L, r.lastKnownPlayTicks);
            // Do not show completely empty records. A player with either Shards OR play time remains listed.
            if (shardBalance == 0 && playTicks == 0L) continue;
            out.add(new BalanceEntry(e.getKey(), name, shardBalance, playTicks));
        }
        out.sort(Comparator.comparingInt(BalanceEntry::shards).reversed()
            .thenComparing(Comparator.comparingLong(BalanceEntry::playTicks).reversed())
            .thenComparing(BalanceEntry::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }


    /** Update the cached effective play time using the player's current vanilla PLAY_TIME value. */
    public synchronized long rememberPlayTime(UUID id, long rawPlayTicks) {
        Record r = record(id);
        long effective = Math.max(0L, rawPlayTicks + r.playTimeOffsetTicks);
        r.lastKnownPlayTicks = effective;
        return effective;
    }

    /** Last effective play time cached for an online or offline player. */
    public synchronized long playTicks(UUID id) {
        return Math.max(0L, record(id).lastKnownPlayTicks);
    }

    /** Set effective play time while leaving Minecraft's underlying statistic untouched. */
    public synchronized long setPlayTicks(UUID id, long rawPlayTicks, long desiredTicks) {
        Record r = record(id);
        long desired = Math.max(0L, desiredTicks);
        r.playTimeOffsetTicks = desired - Math.max(0L, rawPlayTicks);
        r.lastKnownPlayTicks = desired;
        save();
        return desired;
    }

    public synchronized long addPlayTicks(UUID id, long rawPlayTicks, long amountTicks) {
        long current = rememberPlayTime(id, rawPlayTicks);
        return setPlayTicks(id, rawPlayTicks, current + Math.max(0L, amountTicks));
    }

    public synchronized long takePlayTicks(UUID id, long rawPlayTicks, long amountTicks) {
        long current = rememberPlayTime(id, rawPlayTicks);
        return setPlayTicks(id, rawPlayTicks, Math.max(0L, current - Math.max(0L, amountTicks)));
    }

    public int shards(UUID id) { return record(id).shards; }
    public int purchasedHomeLimit(UUID id) { return record(id).purchasedHomeLimit; }

    public void addShard(UUID id) {
        record(id).shards++;
    }

    public int addShards(UUID id, int amount) {
        Record r = record(id);
        r.shards = Math.max(0, r.shards + Math.max(0, amount));
        save();
        return r.shards;
    }

    public int setShards(UUID id, int amount) {
        Record r = record(id);
        r.shards = Math.max(0, amount);
        save();
        return r.shards;
    }

    public int takeShards(UUID id, int amount) {
        Record r = record(id);
        r.shards = Math.max(0, r.shards - Math.max(0, amount));
        save();
        return r.shards;
    }


    /** Atomically transfer Shards between two players. Returns true only if the sender had enough. */
    public synchronized boolean transferShards(UUID from, UUID to, int amount) {
        if (from == null || to == null || from.equals(to) || amount <= 0) return false;
        Record sender = record(from);
        if (sender.shards < amount) return false;
        Record receiver = record(to);
        sender.shards -= amount;
        receiver.shards += amount;
        save();
        return true;
    }

    public boolean purchaseNextHome(UUID id, int nextHomeNumber) {
        Record r = record(id);
        if (nextHomeNumber < 4 || nextHomeNumber > 28) return false;
        int cost = costForHome(nextHomeNumber);
        if (r.shards < cost) return false;
        r.shards -= cost;
        r.purchasedHomeLimit = Math.max(r.purchasedHomeLimit, nextHomeNumber);
        save();
        return true;
    }

    public static int costForHome(int homeNumber) {
        // Home 4 = 100; each additional home costs 50 more.
        return 100 + ((homeNumber - 4) * 50);
    }

    public synchronized void save() {
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) { GSON.toJson(data, TYPE, w); }
        } catch (Exception e) {
            ChillZoneHomes.LOGGER.error("Could not save shard data", e);
        }
    }
}

package com.portaltiers.tagger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portaltiers.tagger.config.PortalConfig;
import com.portaltiers.tagger.model.GameMode;
import com.portaltiers.tagger.model.PlayerRanking;
import com.portaltiers.tagger.util.Http;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PortalTierManager {

    public static final Logger LOGGER = LoggerFactory.getLogger("PojavTierTagger");
    public static final Identifier ICON_FONT = Identifier.of("portaltiertagger", "icons");

    private static final String API_URL = "https://mmpvp-production.up.railway.app/api/rankings";

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PojavTierTagger-fetch");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, PlayerRanking> CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean fetching = new AtomicBoolean(false);

    public static volatile int cacheVersion = 0;
    public static volatile boolean loaded = false;
    private static volatile long lastAttemptMillis = 0L;
    private static volatile int failureStreak = 0;

    private PortalTierManager() {}

    public record DisplayedTier(GameMode mode, String tierCode) {}

    // ------------------------------------------------------------------
    // Networking
    // ------------------------------------------------------------------

    public static void maybeRefresh() {
        long now = System.currentTimeMillis();
        int intervalMin = Math.max(1, PortalConfig.get().refreshIntervalMinutes);
        long wait = loaded ? intervalMin * 60_000L : 60_000L;
        if (now - lastAttemptMillis >= wait) {
            refreshNow();
        }
    }

    public static void refreshNow() {
        if (!fetching.compareAndSet(false, true)) return;
        lastAttemptMillis = System.currentTimeMillis();

        EXEC.submit(() -> {
            try {
                String body = Http.get(API_URL);
                ingest(body);
                failureStreak = 0;
            } catch (Exception e) {
                if (failureStreak == 0) {
                    LOGGER.warn("Failed to fetch Pojav rankings from {} : {}", API_URL, e.toString());
                    notifyPlayer("§c[Pojav] Could not load tiers — retrying every minute…");
                } else if (failureStreak % 20 == 0) {
                    LOGGER.warn("Still failing to fetch Pojav rankings ({} attempts): {}",
                            failureStreak, e.toString());
                }
                failureStreak++;
            } finally {
                fetching.set(false);
            }
        });
    }

    private static void notifyPlayer(String legacyText) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal(legacyText), false);
            }
        });
    }

    private static void ingest(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray playersArray = root.getAsJsonArray("players");
            if (playersArray == null) return;

            Map<String, PlayerRanking> next = new ConcurrentHashMap<>();
            for (JsonElement elem : playersArray) {
                JsonObject obj = elem.getAsJsonObject();
                String ign = getJsonString(obj, "ign");
                if (ign == null || ign.equalsIgnoreCase("unknown")) continue;

                PlayerRanking pr = new PlayerRanking();
                pr.minecraftUsername = ign;
                pr.bestTier = getJsonString(obj, "best_tier");
                pr.points = getJsonInt(obj, "points");
                pr.region = getJsonString(obj, "region");

                String key = ign.toLowerCase(Locale.ROOT);
                next.put(key, pr);
            }

            CACHE.clear();
            CACHE.putAll(next);
            boolean firstLoad = !loaded;
            loaded = true;
            cacheVersion++;
            LOGGER.info("Loaded {} Pojav player rankings", CACHE.size());
            if (firstLoad) {
                notifyPlayer("§a[Pojav] Loaded " + CACHE.size() + " player tiers!");
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Pojav rankings", e);
        }
    }

    private static String getJsonString(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return (e != null && !e.isJsonNull()) ? e.getAsString() : null;
    }
    private static int getJsonInt(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return (e != null && !e.isJsonNull()) ? e.getAsInt() : 0;
    }

    public static void clearCache() {
        CACHE.clear();
        loaded = false;
        cacheVersion++;
    }

    public static int size() {
        return CACHE.size();
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    public static PlayerRanking lookup(String username) {
        if (username == null) return null;
        return CACHE.get(username.toLowerCase(Locale.ROOT));
    }

    /** Returns the player's single best tier with a default gamemode icon. */
    public static DisplayedTier resolve(String username) {
        PlayerRanking pr = lookup(username);
        if (pr == null || pr.bestTier == null) return null;

        // Use SWORD as the default icon – no need for a new OVERALL mode.
        GameMode mode = GameMode.SWORD;   // <-- fallback to an existing gamemode
        return new DisplayedTier(mode, pr.bestTier);
    }

    /** Points awarded for a tier code (PojavTiers scoring). */
    public static int tierPoints(String tier) {
        if (tier == null) return 0;
        return switch (tier.toUpperCase(Locale.ROOT)) {
            case "HT1" -> 100;
            case "LT1" -> 90;
            case "HT2" -> 80;
            case "LT2" -> 70;
            case "HT3" -> 60;
            case "LT3" -> 50;
            case "HT4" -> 40;
            case "LT4" -> 30;
            case "HT5" -> 20;
            case "LT5" -> 10;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------
    // Text building
    // ------------------------------------------------------------------

    public static MutableText buildBadge(DisplayedTier dt) {
        PortalConfig cfg = PortalConfig.get();
        MutableText badge = Text.empty();

        if (cfg.showIcons) {
            badge.append(Text.literal(String.valueOf(dt.mode().iconChar()))
                    .setStyle(Style.EMPTY.withFont(ICON_FONT).withColor(dt.mode().iconColor())));
            badge.append(Text.literal(" "));
        }

        String label = cfg.useBrackets ? "[" + dt.tierCode() + "]" : dt.tierCode();
        int color = cfg.getTierColor(dt.tierCode());
        badge.append(Text.literal(label).setStyle(Style.EMPTY.withColor(color)));
        return badge;
    }

    public static Text appendTier(String username, Text original) {
        DisplayedTier dt = resolve(username);
        if (dt == null) return original;

        MutableText result = buildBadge(dt);
        result.append(Text.literal(PortalConfig.get().separator).formatted(Formatting.GRAY));
        result.append(original);
        return result;
    }

    // ------------------------------------------------------------------
    // Chat deep replacement
    // ------------------------------------------------------------------

    private static Set<String> onlineNames() {
        Set<String> names = new HashSet<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return names;
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            if (e.getProfile() != null && e.getProfile().getName() != null) {
                names.add(e.getProfile().getName());
            }
        }
        return names;
    }

    public static Text deepReplace(Text message) {
        if (message == null) return null;
        Set<String> names = onlineNames();
        if (names.isEmpty()) return message;
        return replaceNode(message, names);
    }

    private static Text replaceNode(Text node, Set<String> names) {
        MutableText self = node.copyContentOnly();
        self.setStyle(node.getStyle());

        String content = self.getString();
        String trimmed = content.trim();

        MutableText rebuilt;
        if (!trimmed.isEmpty() && names.contains(trimmed)) {
            DisplayedTier dt = resolve(trimmed);
            if (dt != null) {
                rebuilt = buildBadge(dt);
                rebuilt.append(Text.literal(PortalConfig.get().separator).formatted(Formatting.GRAY));
                rebuilt.append(self);
            } else {
                rebuilt = self;
            }
        } else {
            rebuilt = self;
        }

        for (Text sibling : node.getSiblings()) {
            rebuilt.append(replaceNode(sibling, names));
        }
        return rebuilt;
    }
}

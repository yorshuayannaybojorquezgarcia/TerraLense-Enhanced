package dev.terralense.config;

import dev.terralense.core.Log;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;

/** Immutable snapshot of {@code config.yml}. */
public final class TerraLenseConfig {
    /** Terrain grid resolution, in cells, along one edge of the minimap. */
    public static final int GRID_SIZE = 24;

    public static final int MIN_ZOOM = 1;

    /** Widest zoom, in blocks per cell. */
    public static final int MAX_ZOOM = 16;

    private final boolean enabled;
    private final int updateIntervalTicks;
    private final boolean joinMessage;
    private final boolean updateCheck;
    private final boolean updateNotifyAdmins;

    private final boolean hudEnabled;
    private final HudPosition hudPosition;
    private final HudShape hudShape;
    private final HudVisualStyle hudVisualStyle;
    private final int hudSize;
    private final int hudGridSize;
    private final int hudMargin;

    private final MapOrientation orientation;
    private final int defaultZoom;
    private final int minZoom;
    private final int maxZoom;

    private final boolean heightShading;
    private final double heightShadingStrength;
    private final boolean biomeColors;

    private final boolean markerPlayers;
    private final int playerRadarRange;
    private final boolean markerWaypoints;
    private final boolean markerDeath;
    private final boolean markerSpawn;
    private final long deathMarkerDurationSeconds;

    private final ResourcePackMode resourcePackMode;
    private final boolean resourcePackPrompt;

    private final HudAnchor hudAnchor;
    private final int hudBorderArgb;
    private final boolean mapKeyEnabled;
    private final int fullscreenZoom;
    private final int hudOffsetCells;
    private final boolean hudPinToBarEdge;

    private final boolean worldsEnabled;
    private final Set<String> worldBlacklist;

    private final int renderThreads;
    private final int maxCachedChunks;
    private final int maxQueuedRenders;
    private final boolean debug;

    private TerraLenseConfig(Builder b) {
        this.enabled = b.enabled;
        this.updateIntervalTicks = b.updateIntervalTicks;
        this.joinMessage = b.joinMessage;
        this.updateCheck = b.updateCheck;
        this.updateNotifyAdmins = b.updateNotifyAdmins;
        this.hudEnabled = b.hudEnabled;
        this.hudPosition = b.hudPosition;
        this.hudShape = b.hudShape;
        this.hudVisualStyle = b.hudVisualStyle;
        this.hudSize = b.hudSize;
        this.hudGridSize = b.hudGridSize;
        this.hudMargin = b.hudMargin;
        this.orientation = b.orientation;
        this.defaultZoom = b.defaultZoom;
        this.minZoom = b.minZoom;
        this.maxZoom = b.maxZoom;
        this.heightShading = b.heightShading;
        this.heightShadingStrength = b.heightShadingStrength;
        this.biomeColors = b.biomeColors;
        this.markerPlayers = b.markerPlayers;
        this.playerRadarRange = b.playerRadarRange;
        this.markerWaypoints = b.markerWaypoints;
        this.markerDeath = b.markerDeath;
        this.markerSpawn = b.markerSpawn;
        this.deathMarkerDurationSeconds = b.deathMarkerDurationSeconds;
        this.resourcePackMode = b.resourcePackMode;
        this.resourcePackPrompt = b.resourcePackPrompt;
        this.hudAnchor = b.hudAnchor;
        this.hudBorderArgb = b.hudBorderArgb;
        this.mapKeyEnabled = b.mapKeyEnabled;
        this.fullscreenZoom = b.fullscreenZoom;
        this.hudOffsetCells = b.hudOffsetCells;
        this.hudPinToBarEdge = b.hudPinToBarEdge;
        this.worldsEnabled = b.worldsEnabled;
        this.worldBlacklist = Set.copyOf(b.worldBlacklist);
        this.renderThreads = b.renderThreads;
        this.maxCachedChunks = b.maxCachedChunks;
        this.maxQueuedRenders = b.maxQueuedRenders;
        this.debug = b.debug;
    }

    /**
     * Reads a configuration snapshot, clamping out-of-range values and warning about anything it
     * could not understand.
     */
    public static TerraLenseConfig load(FileConfiguration yaml, Log log) {
        Builder b = new Builder();
        Validation v = new Validation(log);

        b.enabled = yaml.getBoolean("general.enabled", true);
        // 2 ticks (10 updates/second) so rotation feels responsive; at 5 the map turned only 4
        // times a second, which reads as lag.
        b.updateIntervalTicks = v.clampInt("general.update-interval-ticks",
                yaml.getInt("general.update-interval-ticks", 2), 1, 100, 2);
        b.joinMessage = yaml.getBoolean("general.join-message", true);
        b.updateCheck = yaml.getBoolean("general.update-check.enabled", true);
        b.updateNotifyAdmins = yaml.getBoolean("general.update-check.notify-admins", true);
        b.debug = yaml.getBoolean("general.debug", false);

        b.hudEnabled = yaml.getBoolean("hud.enabled", true);
        b.hudVisualStyle = v.enumValue("hud.style", yaml.getString("hud.style"),
                HudVisualStyle.fromConfig(yaml.getString("hud.style")), HudVisualStyle.MODERN);
        b.hudPosition = v.enumValue("hud.position", yaml.getString("hud.position"),
                HudPosition.fromConfig(yaml.getString("hud.position")), HudPosition.TOP_RIGHT);
        b.hudShape = v.enumValue("hud.shape", yaml.getString("hud.shape"),
                HudShape.fromConfig(yaml.getString("hud.shape")), HudShape.CIRCLE);
        // 96 gives 4px cells at the fixed 24x24 grid: readable without dominating the screen.
        int requestedGrid = yaml.getInt("hud.resolution", GRID_SIZE);
        if (requestedGrid != 24 && requestedGrid != 32) {
            log.warn("config: hud.resolution must be 24 or 32; using 24.");
            requestedGrid = GRID_SIZE;
        }
        b.hudGridSize = requestedGrid;
        b.hudSize = v.clampInt("hud.size", yaml.getInt("hud.size", 96), 32, 168, 96);
        b.hudMargin = v.clampInt("hud.margin", yaml.getInt("hud.margin", 8), 0, 128, 8);

        b.orientation = v.enumValue("map.orientation", yaml.getString("map.orientation"),
                MapOrientation.fromConfig(yaml.getString("map.orientation")), MapOrientation.PLAYER_UP);
        b.minZoom = v.clampInt("map.zoom.minimum", yaml.getInt("map.zoom.minimum", MIN_ZOOM),
                MIN_ZOOM, MAX_ZOOM, MIN_ZOOM);
        b.maxZoom = v.clampInt("map.zoom.maximum", yaml.getInt("map.zoom.maximum", MAX_ZOOM),
                MIN_ZOOM, MAX_ZOOM, MAX_ZOOM);
        if (b.maxZoom < b.minZoom) {
            log.warn("config: map.zoom.maximum (" + b.maxZoom + ") is below map.zoom.minimum ("
                    + b.minZoom + "); swapping them.");
            int swap = b.minZoom;
            b.minZoom = b.maxZoom;
            b.maxZoom = swap;
        }
        // 8 blocks/cell covers 192x192 blocks at the 24-cell grid: wide coverage without
        // needing more cells.
        b.defaultZoom = v.clampInt("map.zoom.default", yaml.getInt("map.zoom.default", 8),
                b.minZoom, b.maxZoom, Math.clamp(8, b.minZoom, b.maxZoom));

        b.heightShading = yaml.getBoolean("terrain.height-shading.enabled", true);
        b.heightShadingStrength = v.clampDouble("terrain.height-shading.strength",
                yaml.getDouble("terrain.height-shading.strength", 0.45D), 0.0D, 1.0D, 0.45D);
        b.biomeColors = yaml.getBoolean("terrain.biome-colors.enabled", false);

        b.markerPlayers = yaml.getBoolean("markers.players.enabled", true);
        b.playerRadarRange = v.clampInt("markers.players.max-distance",
                yaml.getInt("markers.players.max-distance", 128), 8, 512, 128);
        b.markerWaypoints = yaml.getBoolean("markers.waypoints.enabled", true);
        b.markerDeath = yaml.getBoolean("markers.death.enabled", true);
        b.deathMarkerDurationSeconds = v.clampLong("markers.death.duration-seconds",
                yaml.getLong("markers.death.duration-seconds", 86_400L), 0L, 2_592_000L, 86_400L);
        b.markerSpawn = yaml.getBoolean("markers.spawn.enabled", true);

        b.resourcePackMode = v.enumValue("resource-pack.mode", yaml.getString("resource-pack.mode"),
                ResourcePackMode.fromConfig(yaml.getString("resource-pack.mode")), ResourcePackMode.AUTO);
        b.resourcePackPrompt = yaml.getBoolean("resource-pack.notify-on-decline", true);

        b.hudAnchor = v.enumValue("hud.anchor", yaml.getString("hud.anchor"),
                HudAnchor.fromConfig(yaml.getString("hud.anchor")), HudAnchor.BOSS_BAR);
        b.hudBorderArgb = parseBorderColor(yaml.getString("hud.border", "1E2A33"), log);
        b.mapKeyEnabled = yaml.getBoolean("map.key.enabled", true);
        b.fullscreenZoom = v.clampInt("map.key.zoom",
                yaml.getInt("map.key.zoom", 8), 1, 16, 8);
        // Negative moves left of centre.
        b.hudPinToBarEdge = yaml.getBoolean("hud.pin-to-bar-edge", true);
        b.hudOffsetCells = v.clampInt("hud.horizontal-offset",
                yaml.getInt("hud.horizontal-offset", 0), -160, 160, 0);

        b.worldsEnabled = yaml.getBoolean("worlds.enabled", true);
        b.worldBlacklist = readWorldBlacklist(yaml);

        int cpus = Runtime.getRuntime().availableProcessors();
        int threadCeiling = Math.max(1, Math.min(8, cpus));
        b.renderThreads = v.clampInt("performance.max-render-threads",
                yaml.getInt("performance.max-render-threads", 2), 1, threadCeiling,
                Math.min(2, threadCeiling));
        b.maxCachedChunks = v.clampInt("performance.max-cached-chunks",
                yaml.getInt("performance.max-cached-chunks", 2048), 64, 65_536, 2048);
        b.maxQueuedRenders = v.clampInt("performance.max-queued-renders",
                yaml.getInt("performance.max-queued-renders", 256), 8, 8192, 256);

        return new TerraLenseConfig(b);
    }

    /**
     * Parses an RGB hex border colour.
     *
     * @return packed opaque ARGB, or {@code 0} when disabled or unparseable
     */
    private static int parseBorderColor(String raw, Log log) {
        if (raw == null) {
            return 0;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("none")
                || value.equalsIgnoreCase("false")) {
            return 0;
        }
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        try {
            return 0xFF00_0000 | (Integer.parseInt(value, 16) & 0x00FF_FFFF);
        } catch (NumberFormatException ex) {
            log.warn("config: hud.border = '" + raw + "' is not RGB hex; no border "
                    + "will be drawn.");
            return 0;
        }
    }

    private static Set<String> readWorldBlacklist(FileConfiguration yaml) {
        Set<String> names = new LinkedHashSet<>();
        for (String raw : yaml.getStringList("worlds.blacklist")) {
            if (raw != null && !raw.isBlank()) {
                names.add(raw.trim().toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    /** Defaults used by unit tests and as a safe fallback if loading fails. */
    public static TerraLenseConfig defaults() {
        return new TerraLenseConfig(new Builder());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int updateIntervalTicks() {
        return updateIntervalTicks;
    }

    public boolean joinMessage() {
        return joinMessage;
    }

    /** Whether to check SpigotMC for a newer version on startup. */
    public boolean updateCheck() {
        return updateCheck;
    }

    /** Whether operators are told about an available update when they join. */
    public boolean updateNotifyAdmins() {
        return updateNotifyAdmins;
    }

    public boolean hudEnabled() {
        return hudEnabled;
    }

    public HudPosition hudPosition() {
        return hudPosition;
    }

    public HudVisualStyle hudVisualStyle() {
        return hudVisualStyle;
    }

    public HudShape hudShape() {
        return hudShape;
    }

    public int hudGridSize() {
        return hudGridSize;
    }

    public int hudSize() {
        return hudSize;
    }

    public int hudMargin() {
        return hudMargin;
    }

    public MapOrientation orientation() {
        return orientation;
    }

    public int defaultZoom() {
        return defaultZoom;
    }

    public int minZoom() {
        return minZoom;
    }

    public int maxZoom() {
        return maxZoom;
    }

    /** Clamps a requested zoom into the configured range. */
    public int clampZoom(int zoom) {
        return Math.clamp(zoom, minZoom, maxZoom);
    }

    public boolean heightShading() {
        return heightShading;
    }

    public double heightShadingStrength() {
        return heightShadingStrength;
    }

    public boolean biomeColors() {
        return biomeColors;
    }

    public boolean markerPlayers() {
        return markerPlayers;
    }

    public int playerRadarRange() {
        return playerRadarRange;
    }

    public boolean markerWaypoints() {
        return markerWaypoints;
    }

    public boolean markerDeath() {
        return markerDeath;
    }

    public boolean markerSpawn() {
        return markerSpawn;
    }

    /** Which client surface the HUD is drawn on. */
    public HudAnchor hudAnchor() {
        return hudAnchor;
    }

    /** Border colour as packed ARGB, or {@code 0} when no border is drawn. */
    public int hudBorderArgb() {
        return hudBorderArgb;
    }

    /** Whether double-tapping the swap-hands key opens the large map. */
    public boolean mapKeyEnabled() {
        return mapKeyEnabled;
    }

    /** Blocks per cell for the large map view. */
    public int fullscreenZoom() {
        return fullscreenZoom;
    }

    /** Horizontal HUD placement in cells, relative to the surface's centre. */
    public int hudOffsetCells() {
        return hudOffsetCells;
    }

    /** Whether to pin the map to the left end of the boss bar. */
    public boolean hudPinToBarEdge() {
        return hudPinToBarEdge;
    }

    public long deathMarkerDurationSeconds() {
        return deathMarkerDurationSeconds;
    }

    public ResourcePackMode resourcePackMode() {
        return resourcePackMode;
    }

    public boolean resourcePackPrompt() {
        return resourcePackPrompt;
    }

    public int renderThreads() {
        return renderThreads;
    }

    public int maxCachedChunks() {
        return maxCachedChunks;
    }

    public int maxQueuedRenders() {
        return maxQueuedRenders;
    }

    public boolean debug() {
        return debug;
    }

    /** Whether TerraLense should operate in the named world. */
    public boolean isWorldEnabled(String worldName) {
        if (!worldsEnabled) {
            return false;
        }
        return worldName != null && !worldBlacklist.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public Set<String> worldBlacklist() {
        return worldBlacklist;
    }

    /** Settings that affect generated resource-pack bytes. */
    public String packRelevantFingerprint() {
        return hudSize + ":" + hudVisualStyle.name() + ":" + hudShape.configName() + ":" + hudMargin + ":" + hudPosition.configName();
    }

    /** Mutable defaults holder; keeps the public type immutable. */
    private static final class Builder {
        private boolean enabled = true;
        private int updateIntervalTicks = 2;
        private boolean joinMessage = true;
        private boolean updateCheck = true;
        private boolean updateNotifyAdmins = true;
        private boolean hudEnabled = true;
        private HudPosition hudPosition = HudPosition.TOP_RIGHT;
        private HudShape hudShape = HudShape.CIRCLE;
        private HudVisualStyle hudVisualStyle = HudVisualStyle.MODERN;
        private int hudSize = 96;
        private int hudGridSize = GRID_SIZE;
        private int hudMargin = 8;
        private MapOrientation orientation = MapOrientation.PLAYER_UP;
        private int defaultZoom = 8;
        private int minZoom = MIN_ZOOM;
        private int maxZoom = MAX_ZOOM;
        private boolean heightShading = true;
        private double heightShadingStrength = 0.45D;
        private boolean biomeColors;
        private boolean markerPlayers = true;
        private int playerRadarRange = 128;
        private boolean markerWaypoints = true;
        private boolean markerDeath = true;
        private boolean markerSpawn = true;
        private long deathMarkerDurationSeconds = 86_400L;
        private ResourcePackMode resourcePackMode = ResourcePackMode.AUTO;
        private boolean resourcePackPrompt = true;
        private HudAnchor hudAnchor = HudAnchor.BOSS_BAR;
        private int hudBorderArgb = 0xFF1E_2A33;
        private boolean mapKeyEnabled = true;
        private int fullscreenZoom = 8;
        private int hudOffsetCells;
        private boolean hudPinToBarEdge = true;
        private boolean worldsEnabled = true;
        private Collection<String> worldBlacklist = List.of();
        private int renderThreads = 2;
        private int maxCachedChunks = 2048;
        private int maxQueuedRenders = 256;
        private boolean debug;
    }

    /** Helper that clamps values and reports every correction exactly once. */
    private record Validation(Log log) {
        int clampInt(String path, int value, int min, int max, int fallback) {
            if (value < min || value > max) {
                int clamped = Math.clamp(value, min, max);
                log.warn("config: " + path + " = " + value + " is outside " + min + ".." + max
                        + "; using " + clamped + " (default " + fallback + ").");
                return clamped;
            }
            return value;
        }

        long clampLong(String path, long value, long min, long max, long fallback) {
            if (value < min || value > max) {
                long clamped = Math.clamp(value, min, max);
                log.warn("config: " + path + " = " + value + " is outside " + min + ".." + max
                        + "; using " + clamped + " (default " + fallback + ").");
                return clamped;
            }
            return value;
        }

        double clampDouble(String path, double value, double min, double max, double fallback) {
            if (Double.isNaN(value)) {
                log.warn("config: " + path + " is not a number; using default " + fallback + ".");
                return fallback;
            }
            if (value < min || value > max) {
                double clamped = Math.clamp(value, min, max);
                log.warn("config: " + path + " = " + value + " is outside " + min + ".." + max
                        + "; using " + clamped + ".");
                return clamped;
            }
            return value;
        }

        <E extends Enum<E>> E enumValue(String path, String raw, java.util.Optional<E> parsed, E fallback) {
            if (parsed.isPresent()) {
                return parsed.get();
            }
            if (raw != null && !raw.isBlank()) {
                log.warn("config: " + path + " = '" + raw + "' is not recognised; using "
                        + fallback.name().toLowerCase(Locale.ROOT) + ".");
            }
            return fallback;
        }
    }
}

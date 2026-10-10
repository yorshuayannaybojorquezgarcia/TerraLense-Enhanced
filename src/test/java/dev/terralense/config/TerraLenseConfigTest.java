package dev.terralense.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.terralense.core.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Loads the **shipped** {@code config.yml} through the real Bukkit YAML loader. */
class TerraLenseConfigTest {
    private static final Log LOG = new Log(Logger.getLogger("TerraLenseConfigTest"));

    private static YamlConfiguration shippedConfig() throws IOException {
        try (InputStream in = TerraLenseConfigTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertNotNull(in, "config.yml is missing from resources");
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("the shipped config.yml loads without warnings-worthy corrections")
    void shippedConfigLoads() throws IOException {
        TerraLenseConfig config = TerraLenseConfig.load(shippedConfig(), LOG);
        assertNotNull(config);
        assertTrue(config.isEnabled());
    }

    @Test
    @DisplayName("shipped defaults match the documented values")
    void shippedDefaultsMatchDocumentation() throws IOException {
        TerraLenseConfig config = TerraLenseConfig.load(shippedConfig(), LOG);

        // 2 ticks (10 updates/second) so rotation feels responsive rather than laggy.
        assertEquals(2, config.updateIntervalTicks());
        assertTrue(config.joinMessage());
        assertFalse(config.debug());

        assertTrue(config.hudEnabled());
        assertEquals(HudPosition.TOP_RIGHT, config.hudPosition());
        // 96 gives 4px cells at the 24x24 grid, the combination verified to render square.
        assertEquals(96, config.hudSize());
        assertEquals(HudShape.CIRCLE, config.hudShape());
        assertEquals(8, config.hudMargin());

        assertEquals(MapOrientation.PLAYER_UP, config.orientation());
        // 8 blocks/cell covers 192x192 blocks at the 24-cell grid.
        assertEquals(8, config.defaultZoom());
        assertEquals(1, config.minZoom());
        assertEquals(16, config.maxZoom());

        assertTrue(config.heightShading());
        // 0.45 rather than 0.25: stronger relief is the main way to add apparent detail without
        // raising the grid resolution.
        assertEquals(0.45D, config.heightShadingStrength(), 0.0001D);
        assertFalse(config.biomeColors(), "biome colours are documented as not implemented");

        assertTrue(config.markerPlayers());
        assertEquals(128, config.playerRadarRange());
        assertTrue(config.markerWaypoints());
        assertTrue(config.markerDeath());
        assertEquals(86_400L, config.deathMarkerDurationSeconds());
        assertTrue(config.markerSpawn());

        assertEquals(ResourcePackMode.AUTO, config.resourcePackMode());
        assertEquals(2048, config.maxCachedChunks());
        assertEquals(256, config.maxQueuedRenders());
    }

    @Test
    @DisplayName("code defaults agree with the shipped file, so both paths behave alike")
    void codeDefaultsMatchShippedFile() throws IOException {
        TerraLenseConfig shipped = TerraLenseConfig.load(shippedConfig(), LOG);
        TerraLenseConfig fallback = TerraLenseConfig.defaults();

        assertEquals(shipped.updateIntervalTicks(), fallback.updateIntervalTicks());
        assertEquals(shipped.hudPosition(), fallback.hudPosition());
        assertEquals(shipped.hudShape(), fallback.hudShape());
        assertEquals(shipped.hudSize(), fallback.hudSize());
        assertEquals(shipped.orientation(), fallback.orientation());
        assertEquals(shipped.defaultZoom(), fallback.defaultZoom());
        assertEquals(shipped.resourcePackMode(), fallback.resourcePackMode());
        assertEquals(shipped.maxCachedChunks(), fallback.maxCachedChunks());
    }

    @Test
    @DisplayName("the shipped config declares the resource pack keys the code reads")
    void shippedConfigDeclaresPackKeys() throws IOException {
        // These are read directly from getConfig() rather than through the snapshot, so a missing
        // key would silently fall back at runtime.
        YamlConfiguration yaml = shippedConfig();
        assertTrue(yaml.contains("resource-pack.url"), "resource-pack.url must be present");
        assertTrue(yaml.contains("resource-pack.pack-format"),
                "resource-pack.pack-format must be present");
        assertEquals(88, yaml.getInt("resource-pack.pack-format"),
                "pack format should match Minecraft 26.2");
        assertEquals("", yaml.getString("resource-pack.url"),
                "url must ship empty so operators set it deliberately");
    }

    @Test
    @DisplayName("out-of-range values are clamped rather than rejected")
    void clampsOutOfRange() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("general.update-interval-ticks", 9999);
        yaml.set("hud.size", 4);
        yaml.set("terrain.height-shading.strength", 5.0D);
        yaml.set("markers.players.max-distance", 99999);

        TerraLenseConfig config = TerraLenseConfig.load(yaml, LOG);
        assertEquals(100, config.updateIntervalTicks());
        assertEquals(32, config.hudSize());
        assertEquals(1.0D, config.heightShadingStrength(), 0.0001D);
        assertEquals(512, config.playerRadarRange());
    }

    @Test
    @DisplayName("unrecognised words fall back to defaults instead of failing")
    void unknownValuesFallBack() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("hud.position", "middle-of-nowhere");
        yaml.set("hud.shape", "hexagon");
        yaml.set("map.orientation", "sideways");
        yaml.set("resource-pack.mode", "maybe");

        TerraLenseConfig config = TerraLenseConfig.load(yaml, LOG);
        assertEquals(HudPosition.TOP_RIGHT, config.hudPosition());
        assertEquals(HudShape.CIRCLE, config.hudShape());
        assertEquals(MapOrientation.PLAYER_UP, config.orientation());
        assertEquals(ResourcePackMode.AUTO, config.resourcePackMode());
    }

    @Test
    @DisplayName("inverted zoom bounds are swapped rather than left broken")
    void swapsInvertedZoomBounds() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("map.zoom.minimum", 8);
        yaml.set("map.zoom.maximum", 2);

        TerraLenseConfig config = TerraLenseConfig.load(yaml, LOG);
        assertTrue(config.minZoom() <= config.maxZoom());
        assertEquals(2, config.minZoom());
        assertEquals(8, config.maxZoom());
    }

    @Test
    @DisplayName("world blacklisting is case-insensitive")
    void worldBlacklistIgnoresCase() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.blacklist", java.util.List.of("World_Nether", "  Spaced  "));

        TerraLenseConfig config = TerraLenseConfig.load(yaml, LOG);
        assertFalse(config.isWorldEnabled("world_nether"));
        assertFalse(config.isWorldEnabled("WORLD_NETHER"));
        assertFalse(config.isWorldEnabled("spaced"));
        assertTrue(config.isWorldEnabled("world"));
    }

    @Test
    @DisplayName("disabling worlds globally overrides the blacklist")
    void worldsCanBeDisabledGlobally() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.enabled", false);
        TerraLenseConfig config = TerraLenseConfig.load(yaml, LOG);
        assertFalse(config.isWorldEnabled("anything"));
    }

    @Test
    @DisplayName("zoom clamping keeps player values inside the configured range")
    void clampZoomRespectsBounds() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("map.zoom.minimum", 2);
        yaml.set("map.zoom.maximum", 4);

        TerraLenseConfig config = TerraLenseConfig.load(yaml, LOG);
        assertEquals(2, config.clampZoom(1));
        assertEquals(4, config.clampZoom(99));
        assertEquals(3, config.clampZoom(3));
    }

    @Test
    @DisplayName("only pack-affecting settings change the pack fingerprint")
    void fingerprintTracksPackRelevantSettings() {
        YamlConfiguration base = new YamlConfiguration();
        base.set("hud.size", 96);
        String original = TerraLenseConfig.load(base, LOG).packRelevantFingerprint();

        YamlConfiguration resized = new YamlConfiguration();
        resized.set("hud.size", 128);
        assertFalse(original.equals(
                TerraLenseConfig.load(resized, LOG).packRelevantFingerprint()));

        // A non-visual setting must not force pack regeneration.
        YamlConfiguration unrelated = new YamlConfiguration();
        unrelated.set("hud.size", 96);
        unrelated.set("general.update-interval-ticks", 20);
        assertEquals(original,
                TerraLenseConfig.load(unrelated, LOG).packRelevantFingerprint());
    }

    @Test
    @DisplayName("render threads never exceed the available CPU count")
    void renderThreadsAreCapped() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("performance.max-render-threads", 999);
        TerraLenseConfig config = TerraLenseConfig.load(yaml, LOG);

        assertTrue(config.renderThreads() >= 1);
        assertTrue(config.renderThreads() <= Math.max(1,
                Math.min(8, Runtime.getRuntime().availableProcessors())));
    }
}

package dev.terralense;

import dev.terralense.api.TerraLenseAPI;
import dev.terralense.command.DeathCommand;
import dev.terralense.command.DebugCommand;
import dev.terralense.command.MapCommand;
import dev.terralense.command.ModeCommand;
import dev.terralense.command.PositionCommand;
import dev.terralense.command.ReloadCommand;
import dev.terralense.command.StatusCommand;
import dev.terralense.command.SubCommand;
import dev.terralense.command.TerraLenseCommand;
import dev.terralense.command.ToggleCommand;
import dev.terralense.command.WaypointCommand;
import dev.terralense.command.ZoomCommand;
import dev.terralense.compatibility.BukkitPlatformScheduler;
import dev.terralense.compatibility.PlatformScheduler;
import dev.terralense.compatibility.ServerPlatform;
import dev.terralense.config.TerraLenseConfig;
import dev.terralense.core.Log;
import dev.terralense.core.MinimapService;
import dev.terralense.core.ReloadResult;
import dev.terralense.core.UpdateChecker;
import dev.terralense.hud.FullscreenMapView;
import dev.terralense.hud.GlyphHudRenderer;
import dev.terralense.map.render.DefaultTerrainRenderer;
import dev.terralense.map.render.RenderPipeline;
import dev.terralense.marker.DeathMarkerProvider;
import dev.terralense.marker.DefaultMarkerRegistry;
import dev.terralense.marker.PlayerMarkerProvider;
import dev.terralense.marker.SpawnMarkerProvider;
import dev.terralense.marker.WaypointMarkerProvider;
import dev.terralense.performance.Metrics;
import dev.terralense.player.MapKeyListener;
import dev.terralense.player.PlayerListener;
import dev.terralense.player.SessionManager;
import dev.terralense.resourcepack.PackGenerator;
import dev.terralense.resourcepack.PackStatusListener;
import dev.terralense.resourcepack.ResourcePackService;
import dev.terralense.storage.Database;
import dev.terralense.storage.InMemoryPlayerDataStore;
import dev.terralense.storage.PlayerDataStore;
import dev.terralense.storage.PreferencesWriter;
import dev.terralense.storage.SqlPlayerDataStore;
import dev.terralense.storage.StorageService;
import dev.terralense.storage.WaypointRepository;
import dev.terralense.storage.WaypointService;
import dev.terralense.terrain.MaterialClassifier;
import dev.terralense.terrain.TerrainColorResolver;
import dev.terralense.terrain.TerrainSampler;
import dev.terralense.terrain.TerrainService;
import dev.terralense.terrain.cache.TerrainCache;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** TerraLense entry point. */
public final class TerraLensePlugin extends JavaPlugin {
    /** Resource pack format for the Minecraft version this release targets (26.2). */
    private static final int DEFAULT_PACK_FORMAT = 88;

    private Log log;
    private ServerPlatform platform;
    private PlatformScheduler scheduler;
    private Metrics metrics;
    private volatile TerraLenseConfig config;

    private Database database;
    private StorageService storage;
    private PlayerDataStore dataStore;
    private WaypointService waypointService;

    private TerrainCache terrainCache;
    private TerrainService terrainService;
    private RenderPipeline renderPipeline;
    private SessionManager sessions;
    private MinimapService minimapService;
    private ResourcePackService packService;
    private DefaultMarkerRegistry markerRegistry;
    private PlatformScheduler.TaskHandle updateTask;
    private UpdateChecker updateChecker;

    private PlayerMarkerProvider playerMarkers;
    private WaypointMarkerProvider waypointMarkers;
    private DeathMarkerProvider deathMarkers;
    private SpawnMarkerProvider spawnMarkers;

    @Override
    public void onEnable() {
        this.log = new Log(getLogger());
        this.platform = ServerPlatform.detect();
        this.metrics = new Metrics();

        saveDefaultConfig();
        this.config = loadConfigSnapshot();
        log.setDebug(config.debug());

        log.info("TerraLense " + getPluginMeta().getVersion() + " starting.");
        log.info("Platform: " + platform.displayName() + " "
                + getServer().getMinecraftVersion() + " on Java "
                + Runtime.version().feature() + ".");

        if (platform.isFolia()) {
            // The render pipeline reads world state through APIs the global scheduler cannot
            // safely reach under Folia's regionised threading.
            log.error("Folia is not supported. The minimap will stay disabled; the rest of "
                    + "your server is unaffected.");
            return;
        }

        this.scheduler = new BukkitPlatformScheduler(this);

        setupStorage();
        setupTerrain();
        setupMarkers();
        setupResourcePack();
        setupRendering();
        registerCommands();

        getServer().getPluginManager().registerEvents(
                new PlayerListener(sessions, terrainService, dataStore, waypointMarkers,
                        deathMarkers, packService, scheduler, this::config,
                        minimapService.delivery(), () -> updateChecker), this);
        getServer().getPluginManager().registerEvents(
                new PackStatusListener(this::config, packService, log), this);
        getServer().getPluginManager().registerEvents(
                new MapKeyListener(minimapService, this::config), this);

        TerraLenseAPI.install(markerRegistry);
        startUpdateLoop();

        log.info("Rendering engine: resource-pack glyph HUD, "
                + config.hudGridSize() + "x" + config.hudGridSize() + " cells, "
                + config.renderThreads() + " render thread(s).");
        log.info("Resource pack mode: " + config.resourcePackMode().configName()
                + (packService.isActive()
                        ? " (active)"
                        : " (inactive: " + packService.inactiveReason() + ")"));
        log.info("TerraLense enabled.");

        this.updateChecker = new UpdateChecker(getPluginMeta().getVersion(), log);
        if (config.updateCheck()) {
            updateChecker.checkAsync();
        }
    }

    /** Opens the database, falling back to an in-memory store on failure. */
    private void setupStorage() {
        waypointMarkers = new WaypointMarkerProvider();
        try {
            database = new Database(getDataFolder().toPath().resolve("terralense.db"));
            database.open();
            storage = new StorageService(database, log, 512);

            WaypointRepository repository = new WaypointRepository(database);
            PreferencesWriter preferences = new PreferencesWriter(database, storage);
            dataStore = new SqlPlayerDataStore(database, storage, preferences, repository, log);
            waypointService = WaypointService.persistent(repository, storage, waypointMarkers);

            log.info("Storage: SQLite, schema v" + database.schemaVersion() + ".");
        } catch (SQLException | RuntimeException ex) {
            log.error("Could not open the TerraLense database; running without persistence. "
                    + "Waypoints and preferences will not survive a restart.", ex);
            dataStore = new InMemoryPlayerDataStore();
            waypointService = WaypointService.inMemory(waypointMarkers);
        }
    }

    private void setupTerrain() {
        terrainCache = new TerrainCache(config.maxCachedChunks());
        terrainService = new TerrainService(terrainCache,
                new TerrainSampler(new MaterialClassifier()), scheduler, log);
    }

    private void setupMarkers() {
        markerRegistry = new DefaultMarkerRegistry(log);
        playerMarkers = new PlayerMarkerProvider(getServer(), config);
        deathMarkers = new DeathMarkerProvider(config);
        spawnMarkers = new SpawnMarkerProvider(getServer(), config);

        markerRegistry.register(playerMarkers);
        markerRegistry.register(waypointMarkers);
        markerRegistry.register(deathMarkers);
        markerRegistry.register(spawnMarkers);
    }

    private void setupResourcePack() {
        packService = new ResourcePackService(new PackGenerator(getPluginMeta().getVersion()),
                log, getDataFolder().toPath(), config);
        packService.initialise(deliverySettings(), serverSendsOwnPack(), packFormat());
        warnIfBehindBungeeCord();
    }

    /** Reads pack delivery settings. */
    private ResourcePackService.DeliverySettings deliverySettings() {
        return new ResourcePackService.DeliverySettings(
                getConfig().getString("resource-pack.url", ""),
                getConfig().getBoolean("resource-pack.self-host.enabled", true),
                getConfig().getString("resource-pack.self-host.bind-address", ""),
                getConfig().getInt("resource-pack.self-host.port", 8123),
                getConfig().getString("resource-pack.self-host.public-address", ""),
                getServer().getIp());
    }

    /** Whether the server is already configured to send its own resource pack. */
    private boolean serverSendsOwnPack() {
        String serverPack = getServer().getResourcePack();
        return serverPack != null && !serverPack.isBlank();
    }

    /** Warns when the server sits behind BungeeCord. */
    private void warnIfBehindBungeeCord() {
        if (!getServer().spigot().getSpigotConfig()
                .getBoolean("settings.bungeecord", false)) {
            return;
        }
        log.warn("BungeeCord mode is enabled. BungeeCord does not forward resource packs "
                + "from backend servers, so players connecting through the proxy will not "
                + "receive the minimap and will see empty boxes instead.");
        log.warn("Use Velocity (which does forward them), send the pack from the proxy, or "
                + "have players connect to this server directly.");
    }

    /** Pack format for the running client. */
    private int packFormat() {
        return getConfig().getInt("resource-pack.pack-format", DEFAULT_PACK_FORMAT);
    }

    private void setupRendering() {
        sessions = new SessionManager(getServer());
        renderPipeline = new RenderPipeline(config.renderThreads(),
                config.maxQueuedRenders(), metrics, log);

        TerrainColorResolver colors = TerrainColorResolver.create(
                config.heightShading(), config.heightShadingStrength());

        // Two fonts share one set of textures and differ only in ascent: one lifts the map above
        // the baseline (bottom anchors), one hangs it below (top anchors).
        GlyphHudRenderer bottomRenderer =
                new GlyphHudRenderer(Key.key(PackGenerator.fontKey()));
        GlyphHudRenderer topRenderer =
                new GlyphHudRenderer(Key.key(PackGenerator.topFontKey()));

        minimapService = new MinimapService(sessions, terrainService,
                new DefaultTerrainRenderer(colors), bottomRenderer, topRenderer, bottomRenderer,
                new FullscreenMapView(bottomRenderer, colors),
                renderPipeline, markerRegistry, scheduler, metrics, log, config);
    }

    private void registerCommands() {
        List<SubCommand> subCommands = List.of(
                new ToggleCommand(sessions, dataStore, minimapService.delivery()),
                new ZoomCommand(sessions, dataStore, this::config),
                new ModeCommand(sessions, dataStore),
                new MapCommand(minimapService),
                new PositionCommand(sessions, this::config),
                new WaypointCommand(waypointService, waypointMarkers, scheduler),
                new DeathCommand(deathMarkers),
                new StatusCommand(this::config, sessions, terrainCache, renderPipeline, metrics,
                        packService, dataStore, database, platform,
                        getPluginMeta().getVersion(), getServer().getMinecraftVersion(),
                        () -> updateChecker),
                new DebugCommand(log, metrics, sessions, storage, markerRegistry),
                new ReloadCommand(this::reload));

        PluginCommand command = getCommand("terralense");
        if (command == null) {
            log.error("The 'terralense' command is missing from plugin.yml; commands are "
                    + "unavailable.");
            return;
        }
        TerraLenseCommand executor = new TerraLenseCommand(new ArrayList<>(subCommands));
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void startUpdateLoop() {
        long interval = config.updateIntervalTicks();
        updateTask = scheduler.repeatOnServerThread(() -> {
            try {
                minimapService.tick();
            } catch (RuntimeException ex) {
                // The loop must survive anything: letting it die would silently stop every minimap
                // on the server.
                log.error("The minimap update loop hit an error.", ex);
            }
        }, interval, interval);
    }

    /** Reloads configuration in place. */
    private ReloadResult reload() {
        TerraLenseConfig previous = config;
        TerraLenseConfig updated;
        try {
            reloadConfig();
            updated = TerraLenseConfig.load(getConfig(), log);
        } catch (RuntimeException ex) {
            log.error("Could not reload config.yml.", ex);
            return ReloadResult.failure(ex.getMessage() == null
                    ? "config.yml could not be parsed" : ex.getMessage());
        }

        List<String> notes = new ArrayList<>();
        if (updated.renderThreads() != previous.renderThreads()) {
            notes.add("performance.max-render-threads needs a restart; still using "
                    + previous.renderThreads() + ".");
        }
        if (updated.maxCachedChunks() != previous.maxCachedChunks()) {
            notes.add("performance.max-cached-chunks needs a restart; still using "
                    + previous.maxCachedChunks() + ".");
        }

        boolean packChanged = packService.requiresRegeneration(updated);

        this.config = updated;
        log.setDebug(updated.debug());

        playerMarkers.applyConfig(updated);
        deathMarkers.applyConfig(updated);
        spawnMarkers.applyConfig(updated);
        packService.applyConfig(updated);
        minimapService.applyConfig(updated);
        // Give providers that previously failed another chance.
        markerRegistry.clearQuarantine();

        if (packChanged) {
            packService.initialise(deliverySettings(), serverSendsOwnPack(), packFormat());
        }
        if (updated.updateIntervalTicks() != previous.updateIntervalTicks()) {
            if (updateTask != null) {
                updateTask.cancel();
            }
            startUpdateLoop();
        }
        if (updated.updateCheck() && !previous.updateCheck() && updateChecker != null) {
            updateChecker.checkAsync();
        }
        return ReloadResult.success(packChanged, notes);
    }

    @Override
    public void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
        // Flush preferences while the storage executor is still accepting work.
        if (sessions != null && dataStore != null) {
            sessions.forEach(session ->
                    dataStore.savePreferences(session.playerId(), session.preferences()));
            sessions.clear();
        }
        if (minimapService != null) {
            // Remove boss bars before shutdown so they do not linger on screen.
            minimapService.delivery().clear();
        }
        if (renderPipeline != null) {
            renderPipeline.close();
        }
        // Release the HTTP port so a reload/restart can rebind it.
        if (packService != null) {
            packService.shutdown();
        }
        // Closed last so the flush above lands; this also closes the database.
        if (storage != null) {
            storage.close();
        } else if (database != null) {
            database.close();
        }
        TerraLenseAPI.uninstall();

        if (log != null) {
            log.info("TerraLense disabled.");
        }
    }

    public TerraLenseConfig config() {
        return config;
    }

    private TerraLenseConfig loadConfigSnapshot() {
        try {
            reloadConfig();
            return TerraLenseConfig.load(getConfig(), log);
        } catch (RuntimeException ex) {
            log.error("Could not read config.yml; using defaults.", ex);
            return TerraLenseConfig.defaults();
        }
    }
}

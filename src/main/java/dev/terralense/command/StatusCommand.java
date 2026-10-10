package dev.terralense.command;

import dev.terralense.compatibility.ServerPlatform;
import dev.terralense.config.TerraLenseConfig;
import dev.terralense.core.Messages;
import dev.terralense.core.UpdateChecker;
import dev.terralense.map.render.RenderPipeline;
import dev.terralense.performance.Metrics;
import dev.terralense.player.SessionManager;
import dev.terralense.resourcepack.PackHttpServer;
import dev.terralense.resourcepack.ResourcePackService;
import dev.terralense.storage.Database;
import dev.terralense.storage.PlayerDataStore;
import dev.terralense.terrain.cache.TerrainCache;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;

/** {@code /terralense status} - an operator-facing health summary. */
public final class StatusCommand implements SubCommand {
    private final Supplier<TerraLenseConfig> config;
    private final SessionManager sessions;
    private final TerrainCache cache;
    private final RenderPipeline pipeline;
    private final Metrics metrics;
    private final ResourcePackService packService;
    private final PlayerDataStore dataStore;
    private final Database database;
    private final ServerPlatform platform;
    private final String pluginVersion;
    private final String minecraftVersion;
    private final Supplier<UpdateChecker> updateChecker;

    public StatusCommand(Supplier<TerraLenseConfig> config,
                         SessionManager sessions,
                         TerrainCache cache,
                         RenderPipeline pipeline,
                         Metrics metrics,
                         ResourcePackService packService,
                         PlayerDataStore dataStore,
                         Database database,
                         ServerPlatform platform,
                         String pluginVersion,
                         String minecraftVersion,
                         Supplier<UpdateChecker> updateChecker) {
        this.config = config;
        this.updateChecker = updateChecker;
        this.sessions = sessions;
        this.cache = cache;
        this.pipeline = pipeline;
        this.metrics = metrics;
        this.packService = packService;
        this.dataStore = dataStore;
        this.database = database;
        this.platform = platform;
        this.pluginVersion = pluginVersion;
        this.minecraftVersion = minecraftVersion;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String permission() {
        return "terralense.status";
    }

    @Override
    public String usage() {
        return "status";
    }

    @Override
    public String description() {
        return "Show version, performance and resource pack state.";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        TerraLenseConfig current = config.get();

        UpdateChecker checker = updateChecker.get();
        boolean outdated = checker != null && checker.updateAvailable();

        sender.sendMessage(Messages.info("Status"));
        sender.sendMessage(Messages.detailLine("Version", outdated
                ? pluginVersion + " (update available: " + checker.latestVersion() + ")"
                : pluginVersion));
        sender.sendMessage(Messages.detailLine("Platform",
                platform.displayName() + " " + minecraftVersion));
        sender.sendMessage(Messages.detailLine("Enabled", Boolean.toString(current.isEnabled())));

        String packState = packService.isActive()
                ? "active (" + current.resourcePackMode().configName() + ")"
                : "inactive - " + packService.inactiveReason();
        sender.sendMessage(Messages.detailLine("Resource pack", packState));

        if (packService.isActive() && packService.packUrl() != null) {
            sender.sendMessage(Messages.detailLine("Pack URL", packService.packUrl()));
        }
        PackHttpServer http = packService.httpServer();
        if (http != null && http.isRunning()) {
            sender.sendMessage(Messages.detailLine("Pack server",
                    "built-in, port " + http.port() + ", " + http.servedCount() + " served"));
        }

        sender.sendMessage(Messages.detailLine("Active minimaps",
                sessions.activeCount() + " of " + sessions.onlinePlayers().size() + " online"));
        sender.sendMessage(Messages.detailLine("Render threads",
                Integer.toString(pipeline.threadCount())));
        sender.sendMessage(Messages.detailLine("Render queue",
                pipeline.queueSize() + " / " + pipeline.queueCapacity()));
        sender.sendMessage(Messages.detailLine("Average render",
                format(metrics.averageRenderMillis()) + " ms"));
        sender.sendMessage(Messages.detailLine("Peak render",
                format(metrics.peakRenderMillis()) + " ms"));
        sender.sendMessage(Messages.detailLine("Terrain cache",
                cache.size() + " / " + cache.maxEntries() + " tiles ("
                        + cache.approximateBytes() / 1024 + " KB)"));
        sender.sendMessage(Messages.detailLine("Cache hit rate",
                format(cache.hitRate() * 100.0D) + "%"));
        sender.sendMessage(Messages.detailLine("Updates skipped",
                format(metrics.skipRate() * 100.0D) + "% of ticks"));

        if (metrics.rendersFailed() > 0L || metrics.rendersDropped() > 0L) {
            sender.sendMessage(Messages.warn("Failed renders: " + metrics.rendersFailed()
                    + ", dropped frames: " + metrics.rendersDropped()));
        }
        if (dataStore.isPersistent() && database != null && database.isOpen()) {
            sender.sendMessage(Messages.detailLine("Storage",
                    "SQLite, schema v" + database.schemaVersion()));
        } else {
            sender.sendMessage(Messages.warn("Storage unavailable: waypoints and preferences "
                    + "are session-only and will not survive a restart."));
        }
        if (outdated) {
            sender.sendMessage(Messages.warn("TerraLense " + checker.latestVersion()
                    + " is available."));
            sender.sendMessage(Messages.prefixed(Messages.link(UpdateChecker.RESOURCE_URL)));
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}

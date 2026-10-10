package dev.terralense.command;

import dev.terralense.core.Log;
import dev.terralense.core.Messages;
import dev.terralense.marker.DefaultMarkerRegistry;
import dev.terralense.performance.Metrics;
import dev.terralense.player.MinimapSession;
import dev.terralense.player.SessionManager;
import dev.terralense.storage.StorageService;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /terralense debug} - toggles verbose logging and shows internals. */
public final class DebugCommand implements SubCommand {
    private final Log log;
    private final Metrics metrics;
    private final SessionManager sessions;

    /** Null when the database could not be opened; reported rather than hidden. */
    private final StorageService storage;
    private final DefaultMarkerRegistry markers;

    public DebugCommand(Log log,
                        Metrics metrics,
                        SessionManager sessions,
                        StorageService storage,
                        DefaultMarkerRegistry markers) {
        this.log = log;
        this.metrics = metrics;
        this.sessions = sessions;
        this.storage = storage;
        this.markers = markers;
    }

    @Override
    public String name() {
        return "debug";
    }

    @Override
    public String permission() {
        return "terralense.debug";
    }

    @Override
    public String usage() {
        return "debug [on|off|reset]";
    }

    @Override
    public String description() {
        return "Toggle debug logging and show diagnostics.";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length > 0) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "on", "true" -> {
                    log.setDebug(true);
                    sender.sendMessage(Messages.success("Debug logging enabled."));
                    return;
                }
                case "off", "false" -> {
                    log.setDebug(false);
                    sender.sendMessage(Messages.info("Debug logging disabled."));
                    return;
                }
                case "reset" -> {
                    metrics.reset();
                    sender.sendMessage(Messages.success("Metrics reset."));
                    return;
                }
                default -> {
                    sender.sendMessage(Messages.error("Usage: /terralense " + usage()));
                    return;
                }
            }
        }

        sender.sendMessage(Messages.info("Diagnostics"));
        sender.sendMessage(Messages.detailLine("Debug logging",
                log.isDebug() ? "on" : "off"));
        sender.sendMessage(Messages.detailLine("Renders completed",
                Long.toString(metrics.rendersCompleted())));
        sender.sendMessage(Messages.detailLine("Renders failed",
                Long.toString(metrics.rendersFailed())));
        sender.sendMessage(Messages.detailLine("Frames dropped",
                Long.toString(metrics.rendersDropped())));
        sender.sendMessage(Messages.detailLine("Terrain re-samples",
                Long.toString(metrics.terrainResamples())));
        sender.sendMessage(Messages.detailLine("Transform-only updates",
                Long.toString(metrics.transformOnlyUpdates())));
        sender.sendMessage(Messages.detailLine("Updates skipped",
                Long.toString(metrics.updatesSkipped())));
        sender.sendMessage(Messages.detailLine("Storage queue",
                storage == null ? "unavailable" : Integer.toString(storage.queuedTasks())));
        sender.sendMessage(Messages.detailLine("Marker providers",
                String.join(", ", markers.providerIds())));

        if (!markers.quarantinedProviders().isEmpty()) {
            sender.sendMessage(Messages.warn("Disabled after errors: "
                    + String.join(", ", markers.quarantinedProviders())));
        }

        if (sender instanceof Player player) {
            MinimapSession session = sessions.session(player.getUniqueId());
            if (session != null && session.viewport() != null) {
                sender.sendMessage(Messages.detailLine("Your zoom",
                        session.viewport().blocksPerCell() + " blocks/cell"));
                sender.sendMessage(Messages.detailLine("Your orientation",
                        session.preferences().orientation().configName()));
                sender.sendMessage(Messages.detailLine("Map rotation",
                        String.format(Locale.ROOT, "%.1f deg",
                                session.rotationEngine().current())));
                sender.sendMessage(Messages.detailLine("Chunks in view",
                        Integer.toString(session.viewport().chunkCount())));
            }
        }
    }

    @Override
    public java.util.List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return java.util.stream.Stream.of("on", "off", "reset")
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return java.util.List.of();
    }
}

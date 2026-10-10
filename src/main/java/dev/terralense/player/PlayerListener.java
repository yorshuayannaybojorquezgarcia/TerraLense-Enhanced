package dev.terralense.player;

import dev.terralense.compatibility.PlatformScheduler;
import dev.terralense.config.TerraLenseConfig;
import dev.terralense.core.Messages;
import dev.terralense.core.UpdateChecker;
import dev.terralense.hud.HudDelivery;
import dev.terralense.marker.DeathMarkerProvider;
import dev.terralense.marker.WaypointMarkerProvider;
import dev.terralense.resourcepack.ResourcePackService;
import dev.terralense.storage.PlayerDataStore;
import dev.terralense.terrain.TerrainService;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/** Wires server events into minimap state. */
public final class PlayerListener implements Listener {
    /** Beyond this squared distance a teleport is treated as a jump, not a step. */
    private static final double TELEPORT_RESET_DISTANCE_SQUARED = 1024.0D;

    private final SessionManager sessions;
    private final TerrainService terrain;
    private final PlayerDataStore store;
    private final WaypointMarkerProvider waypointMarkers;
    private final DeathMarkerProvider deathMarkers;
    private final ResourcePackService packService;
    private final PlatformScheduler scheduler;
    private final Supplier<TerraLenseConfig> config;
    private final HudDelivery hudDelivery;
    private final Supplier<UpdateChecker> updateChecker;

    public PlayerListener(SessionManager sessions,
                          TerrainService terrain,
                          PlayerDataStore store,
                          WaypointMarkerProvider waypointMarkers,
                          DeathMarkerProvider deathMarkers,
                          ResourcePackService packService,
                          PlatformScheduler scheduler,
                          Supplier<TerraLenseConfig> config,
                          HudDelivery hudDelivery,
                          Supplier<UpdateChecker> updateChecker) {
        this.hudDelivery = hudDelivery;
        this.updateChecker = updateChecker;
        this.sessions = sessions;
        this.terrain = terrain;
        this.store = store;
        this.waypointMarkers = waypointMarkers;
        this.deathMarkers = deathMarkers;
        this.packService = packService;
        this.scheduler = scheduler;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Start from configured defaults so the minimap works immediately, then apply stored
        // preferences when the async load returns.
        sessions.create(playerId, PlayerPreferences.defaultsFrom(config.get()));
        packService.send(player);

        store.load(playerId).thenAccept(data -> scheduler.runOnServerThread(() -> {
            MinimapSession session = sessions.session(playerId);
            if (session == null) {
                // The player left while the load was in flight.
                return;
            }
            if (data.preferences() != null) {
                applyStored(session, data.preferences());
            }
            waypointMarkers.put(playerId, data.waypoints());

            if (config.get().joinMessage() && player.isOnline()) {
                player.sendMessage(Messages.info("Minimap ready. Use /terralense for controls."));
            }
        }));

        notifyUpdate(player);
    }

    /** Tells operators about a pending update, once per join. */
    private void notifyUpdate(Player player) {
        if (!config.get().updateNotifyAdmins() || !player.hasPermission("terralense.admin")) {
            return;
        }
        UpdateChecker checker = updateChecker.get();
        if (checker == null || !checker.updateAvailable()) {
            return;
        }
        player.sendMessage(Messages.warn("TerraLense " + checker.latestVersion()
                + " is available (running " + checker.currentVersion() + ")."));
        player.sendMessage(Messages.prefixed(Messages.link(UpdateChecker.RESOURCE_URL)));
    }

    private void applyStored(MinimapSession session, PlayerPreferences stored) {
        stored.clampTo(config.get());
        PlayerPreferences live = session.preferences();
        live.setMinimapEnabled(stored.minimapEnabled());
        live.setOrientation(stored.orientation());
        live.setZoom(stored.zoom());
        live.setRadarEnabled(stored.radarEnabled());
        live.setOffsetNudge(stored.offsetNudge());
        // Loaded values are already persisted, so this is not a pending change.
        live.markClean();
        session.markTerrainDirty();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        MinimapSession session = sessions.remove(playerId);
        if (session != null) {
            // Flush before the session is discarded.
            store.savePreferences(playerId, session.preferences());
        }
        waypointMarkers.remove(playerId);
        deathMarkers.remove(playerId);
        // Release the player's boss bar so it cannot accumulate over uptime.
        hudDelivery.forget(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        MinimapSession session = sessions.session(event.getPlayer().getUniqueId());
        if (session != null) {
            // Reset rather than reuse: terrain from the previous dimension must never appear, not
            // even for a single frame.
            session.resetFor(event.getPlayer().getWorld().getUID());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        MinimapSession session = sessions.session(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to.getWorld() != null && !to.getWorld().equals(from.getWorld())) {
            session.resetFor(to.getWorld().getUID());
            return;
        }
        // Only a long jump needs invalidation.
        if (from.getWorld() != null && from.getWorld().equals(to.getWorld())
                && from.distanceSquared(to) > TELEPORT_RESET_DISTANCE_SQUARED) {
            session.markTerrainDirty();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location location = player.getLocation();
        UUID worldId = player.getWorld().getUID();
        long now = System.currentTimeMillis();

        deathMarkers.record(player.getUniqueId(), worldId,
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), now);
        store.recordDeath(player.getUniqueId(), worldId,
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), now);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        invalidate(event.getBlock().getWorld().getUID(),
                event.getBlock().getX(), event.getBlock().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        invalidate(event.getBlock().getWorld().getUID(),
                event.getBlock().getX(), event.getBlock().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        // Stops the cache holding tiles for a world that is no longer loaded.
        terrain.invalidateWorld(event.getWorld().getUID());
    }

    /**
     * The dirty region system: invalidates one chunk and flags only the players who can actually
     * see it.
     */
    private void invalidate(UUID worldId, int blockX, int blockZ) {
        terrain.invalidateBlock(worldId, blockX, blockZ);
        sessions.forEach(session -> {
            if (!worldId.equals(session.worldId()) || session.viewport() == null) {
                return;
            }
            if (session.viewport().containsBlock(blockX, blockZ)) {
                session.markTerrainDirty();
            }
        });
    }
}

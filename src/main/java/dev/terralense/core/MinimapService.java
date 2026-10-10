package dev.terralense.core;

import dev.terralense.api.marker.Marker;
import dev.terralense.api.marker.MarkerRegistry;
import dev.terralense.compatibility.PlatformScheduler;
import dev.terralense.config.HudAnchor;
import dev.terralense.config.HudShape;
import dev.terralense.config.MapOrientation;
import dev.terralense.config.TerraLenseConfig;
import dev.terralense.hud.FullscreenMapView;
import dev.terralense.hud.HudDelivery;
import dev.terralense.hud.HudFrame;
import dev.terralense.hud.HudMarker;
import dev.terralense.hud.HudRenderer;
import dev.terralense.map.MapImage;
import dev.terralense.map.MapViewport;
import dev.terralense.map.TerrainGrid;
import dev.terralense.map.render.RenderOptions;
import dev.terralense.map.render.RenderPipeline;
import dev.terralense.map.render.TerrainRenderer;
import dev.terralense.map.rotation.RotationEngine;
import dev.terralense.performance.Metrics;
import dev.terralense.player.MinimapSession;
import dev.terralense.player.PlayerPreferences;
import dev.terralense.player.SessionManager;
import dev.terralense.terrain.PendingSnapshots;
import dev.terralense.terrain.TerrainService;
import org.bukkit.World;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Drives the minimap update loop. */
public final class MinimapService {
    private final SessionManager sessions;
    private final TerrainService terrain;
    private final TerrainRenderer terrainRenderer;
    /** Used for bottom-anchored surfaces; glyphs sit above the text baseline. */
    private final HudRenderer hudRenderer;

    /** Used for top-anchored surfaces; glyphs hang below the text baseline. */
    private final HudRenderer topHudRenderer;

    /** Row-based renderer used by the sidebar, which stacks lines itself. */
    private final dev.terralense.hud.GlyphHudRenderer sidebarRenderer;
    private final RenderPipeline pipeline;
    private final MarkerRegistry markers;
    private final PlatformScheduler scheduler;
    private final Metrics metrics;
    private final Log log;
    private final FullscreenMapView fullscreenView;
    private final HudDelivery delivery = new HudDelivery();

    private volatile TerraLenseConfig config;

    public MinimapService(SessionManager sessions,
                          TerrainService terrain,
                          TerrainRenderer terrainRenderer,
                          HudRenderer hudRenderer,
                          HudRenderer topHudRenderer,
                          dev.terralense.hud.GlyphHudRenderer sidebarRenderer,
                          FullscreenMapView fullscreenView,
                          RenderPipeline pipeline,
                          MarkerRegistry markers,
                          PlatformScheduler scheduler,
                          Metrics metrics,
                          Log log,
                          TerraLenseConfig config) {
        this.topHudRenderer = topHudRenderer;
        this.sidebarRenderer = sidebarRenderer;
        this.fullscreenView = fullscreenView;
        this.sessions = sessions;
        this.terrain = terrain;
        this.terrainRenderer = terrainRenderer;
        this.hudRenderer = hudRenderer;
        this.pipeline = pipeline;
        this.markers = markers;
        this.scheduler = scheduler;
        this.metrics = metrics;
        this.log = log;
        this.config = config;
    }

    public void applyConfig(TerraLenseConfig updated) {
        this.config = updated;
        sessions.forEach(session -> {
            session.preferences().clampTo(updated);
            session.markHudDirty();
            session.markTerrainDirty();
        });
    }

    /** One update tick. */
    public void tick() {
        TerraLenseConfig current = config;
        if (!current.isEnabled()) {
            return;
        }
        for (Player player : sessions.onlinePlayers()) {
            try {
                updatePlayer(player, current);
            } catch (RuntimeException ex) {
                // One player's failure must not stop the others updating.
                log.debug(() -> "minimap update failed for " + player.getName()
                        + ": " + ex.getMessage());
            }
        }
    }

    private void updatePlayer(Player player, TerraLenseConfig current) {
        MinimapSession session = sessions.session(player.getUniqueId());
        if (session == null) {
            return;
        }
        PlayerPreferences prefs = session.preferences();
        if (!prefs.minimapEnabled() || !current.hudEnabled()) {
            return;
        }

        World world = player.getWorld();
        if (!current.isWorldEnabled(world.getName())) {
            return;
        }
        // Skip while a previous frame is still rendering, so a slow render cannot pile up frames
        // for the same player.
        if (session.isRenderInFlight()) {
            return;
        }

        Location location = player.getLocation();
        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();
        float yaw = location.getYaw();

        MinimapSession.UpdateDecision decision =
                session.evaluate(world.getUID(), blockX, blockZ, yaw, current);

        if (decision == MinimapSession.UpdateDecision.SKIP) {
            metrics.recordSkip();
            return;
        }
        if (decision == MinimapSession.UpdateDecision.RESEND) {
            // Keepalive: the surface is fading but nothing changed, so resend the existing frame
            // rather than rendering a new one.
            metrics.recordSkip();
            Component cached = session.lastFrame();
            if (cached != null) {
                delivery.send(player, cached, current.hudAnchor());
                session.recordSent(cached);
            }
            return;
        }

        MapViewport viewport = session.viewport();
        float rotation = session.rotationEngine().current();
        float arrowAngle = prefs.orientation() == MapOrientation.PLAYER_UP
                ? 0.0F
                : RotationEngine.arrowBearingForYaw(yaw);

        // Markers are gathered here, on the server thread, because providers may read world state.
        List<HudMarker> hudMarkers = collectMarkers(player, viewport, current, prefs);

        PendingSnapshots snapshots;
        if (decision == MinimapSession.UpdateDecision.RESAMPLE_TERRAIN) {
            metrics.recordTerrainResample();
            snapshots = terrain.captureSnapshots(world, viewport);
        } else {
            metrics.recordTransformOnly();
            snapshots = new PendingSnapshots(world.getUID(), world.getMinHeight(),
                    world.getMaxHeight() - 1);
        }

        UUID playerId = player.getUniqueId();
        HudShape shape = current.hudVisualStyle().effectiveShape(current.hudShape());
        MapOrientation orientation = prefs.orientation();
        int offsetCells = resolveOffset(current, prefs, viewport.gridSize());
        int borderArgb = current.hudVisualStyle().effectiveBorder(current.hudBorderArgb());
        HudAnchor anchor = current.hudAnchor();

        session.setRenderInFlight(true);
        boolean accepted = pipeline.submit(() ->
                renderAsync(playerId, snapshots, viewport, orientation, rotation,
                        shape, arrowAngle, hudMarkers, offsetCells, borderArgb, anchor));
        if (!accepted) {
            // Dropped by a saturated queue; clear the flag so the next tick can try again rather
            // than leaving the session stuck.
            session.setRenderInFlight(false);
            session.markTerrainDirty();
        }
    }

    /** Runs on a render worker. */
    private void renderAsync(UUID playerId,
                             PendingSnapshots snapshots,
                             MapViewport viewport,
                             MapOrientation orientation,
                             float rotation,
                             HudShape shape,
                             float arrowAngle,
                             List<HudMarker> hudMarkers,
                             int offsetCells,
                             int borderArgb,
                             HudAnchor anchor) {
        try {
            TerrainGrid grid = terrain.buildGrid(snapshots, viewport);
            // Smoothing softens the hard edges between terrain types; the border gives the map a
            // defined outline against the world behind it.
            RenderOptions options = new RenderOptions(viewport.gridSize(), orientation,
                    rotation, shape, true, borderArgb);
            MapImage image = terrainRenderer.render(grid, options);
            HudFrame frame = new HudFrame(arrowAngle, true, hudMarkers, offsetCells);

            if (anchor == HudAnchor.SIDEBAR) {
                // The sidebar stacks lines itself, so the map is rendered per row.
                List<Component> rows = sidebarRenderer.renderRows(image, frame);
                scheduler.runOnServerThread(() -> deliverRows(playerId, rows));
                return;
            }

            HudRenderer renderer = anchor == HudAnchor.BOSS_BAR ? topHudRenderer : hudRenderer;
            Component hud = renderer.render(image, frame);

            // Back to the server thread to touch the player.
            scheduler.runOnServerThread(() -> deliver(playerId, hud));
        } finally {
            // Always clear the flag, even on failure, so the session recovers.
            scheduler.runOnServerThread(() -> {
                MinimapSession session = sessions.session(playerId);
                if (session != null) {
                    session.setRenderInFlight(false);
                }
            });
        }
    }

    /** Delivers a row-based frame to the sidebar. */
    private void deliverRows(UUID playerId, List<Component> rows) {
        Player player = sessions.playerOf(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        delivery.sendRows(player, rows, SIDEBAR_TITLE);

        MinimapSession session = sessions.session(playerId);
        if (session != null) {
            // Marks the frame as sent so the update loop does not treat the surface as stale; the
            // sidebar itself never fades.
            session.recordSent(Component.empty());
        }
    }

    /** Sidebar heading. */
    private static final Component SIDEBAR_TITLE = Component.text("TerraLense",
            net.kyori.adventure.text.format.TextColor.color(0x4F, 0xD1, 0xA5));

    private void deliver(UUID playerId, Component hud) {
        Player player = sessions.playerOf(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        delivery.send(player, hud, config.hudAnchor());

        // Remember the frame so it can be resent before the action bar fades.
        MinimapSession session = sessions.session(playerId);
        if (session != null) {
            session.recordSent(hud);
        }
    }

    /** Resolves markers to map cells. */
    private List<HudMarker> collectMarkers(Player player,
                                           MapViewport viewport,
                                           TerraLenseConfig current,
                                           PlayerPreferences prefs) {
        List<HudMarker> resolved = new ArrayList<>();
        UUID worldId = player.getWorld().getUID();

        for (Marker marker : markers.collect(player.getUniqueId(), worldId)) {
            if (!includeMarker(marker, current, prefs)) {
                continue;
            }
            if (!viewport.containsBlock(marker.x(), marker.z())) {
                continue;
            }
            int cellX = viewport.cellXForBlock(marker.x());
            int cellZ = viewport.cellZForBlock(marker.z());
            if (!viewport.containsCell(cellX, cellZ)) {
                continue;
            }
            resolved.add(new HudMarker(cellX, cellZ, marker.glyphIndex(),
                    marker.color(), marker.priority()));
        }
        return resolved;
    }

    private boolean includeMarker(Marker marker, TerraLenseConfig current, PlayerPreferences prefs) {
        return switch (marker.type()) {
            case PLAYER -> current.markerPlayers() && prefs.radarEnabled();
            case WAYPOINT -> current.markerWaypoints();
            case DEATH -> current.markerDeath();
            case SPAWN -> current.markerSpawn();
            case POI, SELF -> true;
        };
    }

    /**
     * Renders and sends the enlarged map view for one player.
     *
     * @param zoom blocks per cell for the large view
     */
    public void requestFullscreen(Player player, int zoom) {
        TerraLenseConfig current = config;
        World world = player.getWorld();
        if (!current.isWorldEnabled(world.getName())) {
            player.sendMessage(Messages.warn("The map is not available in this world."));
            return;
        }

        MapViewport viewport = new MapViewport(player.getLocation().getBlockX(),
                player.getLocation().getBlockZ(), FullscreenMapView.GRID_SIZE, zoom);
        PendingSnapshots snapshots = terrain.captureSnapshots(world, viewport);
        float arrowAngle = RotationEngine.arrowBearingForYaw(player.getLocation().getYaw());
        UUID playerId = player.getUniqueId();

        boolean accepted = pipeline.submit(() -> {
            TerrainGrid grid = terrain.buildGrid(snapshots, viewport);
            Component view = fullscreenView.render(grid, viewport, arrowAngle);
            scheduler.runOnServerThread(() -> {
                Player target = sessions.playerOf(playerId);
                if (target != null && target.isOnline()) {
                    target.sendMessage(view);
                }
            });
        });
        if (!accepted) {
            player.sendMessage(Messages.warn("The map is busy right now; try again."));
        }
    }

    /** Width of the boss bar sprite in GUI pixels. */
    private static final int BOSS_BAR_WIDTH_PIXELS = 182;

    /** Works out the horizontal offset in cells. */
    private int resolveOffset(TerraLenseConfig current, PlayerPreferences prefs, int gridSize) {
        int base = current.hudOffsetCells() + prefs.offsetNudge();
        if (!current.hudPinToBarEdge() || current.hudAnchor() != HudAnchor.BOSS_BAR) {
            return base;
        }

        int cellPixels = Math.max(1, current.hudSize() / gridSize);
        int mapCells = gridSize;
        int barCells = BOSS_BAR_WIDTH_PIXELS / cellPixels;

        // Centre the map within the bar, then apply the player's nudge.
        int centredInBar = -(mapCells / 2);
        int desired = centredInBar + base;

        // Clamp to the bar's bounds so the map can never leave the visible area.
        int leftLimit = -(barCells / 2);
        int rightLimit = Math.max(leftLimit, barCells / 2 - mapCells);
        return Math.clamp(desired, leftLimit, rightLimit);
    }

    /** HUD delivery, exposed so listeners can release per-player boss bars. */
    public HudDelivery delivery() {
        return delivery;
    }

    public RenderPipeline pipeline() {
        return pipeline;
    }

    public Metrics metrics() {
        return metrics;
    }
}

package dev.terralense.marker;

import dev.terralense.api.marker.Marker;
import dev.terralense.api.marker.MarkerProvider;
import dev.terralense.api.marker.MarkerType;
import dev.terralense.api.marker.MarkerVisibility;
import dev.terralense.config.TerraLenseConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/** Nearby players, for the radar. */
public final class PlayerMarkerProvider implements MarkerProvider {
    private final Server server;
    private volatile TerraLenseConfig config;

    public PlayerMarkerProvider(Server server, TerraLenseConfig config) {
        this.server = server;
        this.config = config;
    }

    public void applyConfig(TerraLenseConfig updated) {
        this.config = updated;
    }

    @Override
    public String id() {
        return "terralense:players";
    }

    @Override
    public Collection<Marker> markersFor(UUID viewerId, UUID worldId) {
        TerraLenseConfig current = config;
        if (!current.markerPlayers()) {
            return List.of();
        }
        Player viewer = server.getPlayer(viewerId);
        if (viewer == null || !viewer.getWorld().getUID().equals(worldId)) {
            return List.of();
        }

        int maxDistance = current.playerRadarRange();
        long maxDistanceSquared = (long) maxDistance * maxDistance;
        List<Marker> markers = new ArrayList<>();

        for (Player other : server.getOnlinePlayers()) {
            if (other.getUniqueId().equals(viewerId)) {
                continue;
            }
            if (!other.getWorld().getUID().equals(worldId)) {
                continue;
            }
            if (isHidden(viewer, other)) {
                continue;
            }
            long distanceSquared = horizontalDistanceSquared(viewer, other);
            if (distanceSquared > maxDistanceSquared) {
                continue;
            }
            markers.add(Marker.builder(other.getUniqueId().toString(), MarkerType.PLAYER, worldId)
                    .position(other.getLocation().getBlockX(),
                            other.getLocation().getBlockY(),
                            other.getLocation().getBlockZ())
                    .visibility(MarkerVisibility.PUBLIC)
                    .label(other.getName())
                    .build());
        }
        return markers;
    }

    /** Any single condition here is enough to keep a player off the map. */
    private boolean isHidden(Player viewer, Player other) {
        if (!viewer.canSee(other)) {
            return true;
        }
        if (!viewer.isListed(other)) {
            return true;
        }
        if (other.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        return !other.hasPermission("terralense.radar");
    }

    private static long horizontalDistanceSquared(Player a, Player b) {
        // Horizontal only: the map is top-down, so vertical separation should not hide someone
        // standing directly below.
        long dx = a.getLocation().getBlockX() - b.getLocation().getBlockX();
        long dz = a.getLocation().getBlockZ() - b.getLocation().getBlockZ();
        return dx * dx + dz * dz;
    }
}

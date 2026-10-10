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
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** World spawn and the player's respawn point. */
public final class SpawnMarkerProvider implements MarkerProvider {
    private final Server server;
    private volatile TerraLenseConfig config;

    public SpawnMarkerProvider(Server server, TerraLenseConfig config) {
        this.server = server;
        this.config = config;
    }

    public void applyConfig(TerraLenseConfig updated) {
        this.config = updated;
    }

    @Override
    public String id() {
        return "terralense:spawn";
    }

    @Override
    public Collection<Marker> markersFor(UUID viewerId, UUID worldId) {
        if (!config.markerSpawn()) {
            return List.of();
        }
        Player viewer = server.getPlayer(viewerId);
        if (viewer == null) {
            return List.of();
        }
        World world = viewer.getWorld();
        if (!world.getUID().equals(worldId)) {
            return List.of();
        }

        List<Marker> markers = new ArrayList<>(2);

        Location worldSpawn = world.getSpawnLocation();
        markers.add(Marker.builder("world-spawn", MarkerType.SPAWN, worldId)
                .position(worldSpawn.getBlockX(), worldSpawn.getBlockY(), worldSpawn.getBlockZ())
                .visibility(MarkerVisibility.PUBLIC)
                .label("Spawn")
                .build());

        Location respawn = viewer.getRespawnLocation();
        if (respawn != null && respawn.getWorld() != null
                && respawn.getWorld().getUID().equals(worldId)) {
            markers.add(Marker.builder("respawn", MarkerType.SPAWN, worldId)
                    .position(respawn.getBlockX(), respawn.getBlockY(), respawn.getBlockZ())
                    .color(0x9BE8CF)
                    .visibility(MarkerVisibility.PRIVATE)
                    .owner(viewerId)
                    .label("Respawn point")
                    .build());
        }
        return markers;
    }
}

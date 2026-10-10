package dev.terralense.marker;

import dev.terralense.api.marker.Marker;
import dev.terralense.api.marker.MarkerProvider;
import dev.terralense.api.marker.MarkerType;
import dev.terralense.api.marker.MarkerVisibility;
import dev.terralense.config.TerraLenseConfig;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The player's most recent death location. */
public final class DeathMarkerProvider implements MarkerProvider {
    private final Map<UUID, DeathLocation> deaths = new ConcurrentHashMap<>();
    private volatile TerraLenseConfig config;

    public DeathMarkerProvider(TerraLenseConfig config) {
        this.config = config;
    }

    public void applyConfig(TerraLenseConfig updated) {
        this.config = updated;
    }

    @Override
    public String id() {
        return "terralense:death";
    }

    public void record(UUID playerId, UUID worldId, int x, int y, int z, long timestamp) {
        deaths.put(playerId, new DeathLocation(worldId, x, y, z, timestamp));
    }

    /** Clears a player's death marker, for {@code /terralense death clear}. */
    public boolean clear(UUID playerId) {
        return deaths.remove(playerId) != null;
    }

    public void remove(UUID playerId) {
        deaths.remove(playerId);
    }

    /** The stored death location, if present and not expired. */
    public Optional<DeathLocation> current(UUID playerId) {
        DeathLocation location = deaths.get(playerId);
        if (location == null) {
            return Optional.empty();
        }
        if (isExpired(location)) {
            deaths.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(location);
    }

    private boolean isExpired(DeathLocation location) {
        long durationSeconds = config.deathMarkerDurationSeconds();
        if (durationSeconds <= 0L) {
            // 0 means "never expire".
            return false;
        }
        long ageMillis = System.currentTimeMillis() - location.timestamp();
        return ageMillis > durationSeconds * 1000L;
    }

    @Override
    public Collection<Marker> markersFor(UUID viewerId, UUID worldId) {
        if (!config.markerDeath()) {
            return List.of();
        }
        return current(viewerId)
                .filter(location -> location.worldId().equals(worldId))
                .map(location -> List.of(
                        Marker.builder("death", MarkerType.DEATH, worldId)
                                .position(location.x(), location.y(), location.z())
                                .visibility(MarkerVisibility.PRIVATE)
                                .owner(viewerId)
                                .label("Death")
                                .build()))
                .orElseGet(List::of);
    }

    /** A recorded death. */
    public record DeathLocation(UUID worldId, int x, int y, int z, long timestamp) {
    }
}

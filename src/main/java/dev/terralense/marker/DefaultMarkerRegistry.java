package dev.terralense.marker;

import dev.terralense.api.marker.Marker;
import dev.terralense.api.marker.MarkerProvider;
import dev.terralense.api.marker.MarkerRegistry;
import dev.terralense.api.marker.MarkerVisibility;
import dev.terralense.core.Log;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Default {@link MarkerRegistry}. */
public final class DefaultMarkerRegistry implements MarkerRegistry {
    private final Map<String, MarkerProvider> providers = new ConcurrentHashMap<>();
    private final Set<String> quarantined = ConcurrentHashMap.newKeySet();
    private final Log log;

    public DefaultMarkerRegistry(Log log) {
        this.log = log;
    }

    @Override
    public void register(MarkerProvider provider) {
        String id = provider.id();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("provider id must not be blank");
        }
        if (providers.putIfAbsent(id, provider) != null) {
            throw new IllegalStateException("a marker provider is already registered with id " + id);
        }
        log.debug(() -> "registered marker provider " + id);
    }

    @Override
    public boolean unregister(String providerId) {
        quarantined.remove(providerId);
        return providers.remove(providerId) != null;
    }

    @Override
    public Collection<String> providerIds() {
        return Set.copyOf(providers.keySet());
    }

    @Override
    public List<Marker> collect(UUID viewer, UUID worldId) {
        List<Marker> collected = new ArrayList<>();
        for (Map.Entry<String, MarkerProvider> entry : providers.entrySet()) {
            String id = entry.getKey();
            if (quarantined.contains(id)) {
                continue;
            }
            try {
                Collection<Marker> markers = entry.getValue().markersFor(viewer, worldId);
                if (markers == null) {
                    continue;
                }
                for (Marker marker : markers) {
                    if (marker == null) {
                        continue;
                    }
                    // World separation and visibility are enforced here so a provider cannot leak
                    // markers across worlds or players.
                    if (!worldId.equals(marker.worldId())) {
                        continue;
                    }
                    if (!marker.isVisibleTo(viewer)) {
                        continue;
                    }
                    collected.add(marker);
                }
            } catch (RuntimeException | LinkageError ex) {
                quarantined.add(id);
                log.error("Marker provider '" + id + "' failed and has been disabled until "
                        + "the next reload.", ex);
            }
        }
        return collected;
    }

    /** Clears quarantine so providers get another chance after a reload. */
    public void clearQuarantine() {
        quarantined.clear();
    }

    /** Ids of providers currently disabled after a failure. */
    public Set<String> quarantinedProviders() {
        return Set.copyOf(quarantined);
    }

    /** Registers a provider serving a fixed public marker set, used for POIs. */
    public void registerStatic(String id, Collection<Marker> markers) {
        List<Marker> snapshot = List.copyOf(markers);
        register(new MarkerProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Collection<Marker> markersFor(UUID viewer, UUID worldId) {
                List<Marker> matching = new ArrayList<>();
                for (Marker marker : snapshot) {
                    if (marker.worldId().equals(worldId)
                            && marker.visibility() == MarkerVisibility.PUBLIC) {
                        matching.add(marker);
                    }
                }
                return matching;
            }
        });
    }
}

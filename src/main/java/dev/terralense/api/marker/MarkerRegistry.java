package dev.terralense.api.marker;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Registry of {@link MarkerProvider}s. */
public interface MarkerRegistry {
    /**
     * Registers a provider.
     *
     * @throws IllegalStateException if a provider with the same id exists
     */
    void register(MarkerProvider provider);

    /**
     * Removes a provider.
     *
     * @return true if a provider was removed
     */
    boolean unregister(String providerId);

    /** Ids of all registered providers. */
    Collection<String> providerIds();

    /** Collects visible markers for a viewer from every provider. */
    List<Marker> collect(UUID viewer, UUID worldId);
}

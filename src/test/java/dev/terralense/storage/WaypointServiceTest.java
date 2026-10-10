package dev.terralense.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.terralense.api.marker.MarkerVisibility;
import dev.terralense.marker.WaypointMarkerProvider;
import dev.terralense.storage.WaypointService.AddOutcome;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the session-only fallback behaves like the persistent service, since the point of the
 * fallback is that commands work identically when the database is unavailable.
 */
class WaypointServiceTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID WORLD = UUID.randomUUID();

    private WaypointMarkerProvider markers;
    private WaypointService service;

    @BeforeEach
    void setUp() {
        markers = new WaypointMarkerProvider();
        service = WaypointService.inMemory(markers);
    }

    private static Waypoint waypoint(UUID owner, String name) {
        return new Waypoint(0L, owner, name, WORLD, 10, 64, 20, 0x4FD1A5, "diamond",
                MarkerVisibility.PRIVATE, System.currentTimeMillis());
    }

    @Test
    @DisplayName("the fallback reports that it is not persistent")
    void reportsNotPersistent() {
        assertFalse(service.isPersistent());
    }

    @Test
    @DisplayName("adding a waypoint succeeds and is listed")
    void addAndList() throws ExecutionException, InterruptedException {
        assertEquals(AddOutcome.CREATED, service.add(waypoint(OWNER, "Base")).get());
        assertEquals(1, service.list(OWNER).get().size());
    }

    @Test
    @DisplayName("duplicate names are rejected case-insensitively, matching SQL behaviour")
    void rejectsDuplicates() throws ExecutionException, InterruptedException {
        assertEquals(AddOutcome.CREATED, service.add(waypoint(OWNER, "Base")).get());
        assertEquals(AddOutcome.DUPLICATE, service.add(waypoint(OWNER, "base")).get());
        assertEquals(1, service.list(OWNER).get().size());
    }

    @Test
    @DisplayName("the per-player cap is enforced")
    void enforcesCap() throws ExecutionException, InterruptedException {
        for (int i = 0; i < WaypointService.MAX_PER_PLAYER; i++) {
            assertEquals(AddOutcome.CREATED, service.add(waypoint(OWNER, "wp" + i)).get());
        }
        assertEquals(AddOutcome.LIMIT_REACHED, service.add(waypoint(OWNER, "overflow")).get());
        assertEquals(WaypointService.MAX_PER_PLAYER, service.list(OWNER).get().size());
    }

    @Test
    @DisplayName("owners are isolated from each other")
    void ownersAreIsolated() throws ExecutionException, InterruptedException {
        service.add(waypoint(OWNER, "Shared")).get();
        assertEquals(AddOutcome.CREATED, service.add(waypoint(OTHER, "Shared")).get());
        assertEquals(1, service.list(OWNER).get().size());
        assertEquals(1, service.list(OTHER).get().size());
    }

    @Test
    @DisplayName("removal is case-insensitive and scoped to the owner")
    void removeIsScoped() throws ExecutionException, InterruptedException {
        service.add(waypoint(OWNER, "Base")).get();
        assertFalse(service.remove(OTHER, "Base").get(), "another player must not remove it");
        assertTrue(service.remove(OWNER, "BASE").get());
        assertTrue(service.list(OWNER).get().isEmpty());
    }

    @Test
    @DisplayName("removing a missing waypoint reports false")
    void removeMissing() throws ExecutionException, InterruptedException {
        assertFalse(service.remove(OWNER, "Nothing").get());
    }

    @Test
    @DisplayName("find locates a waypoint case-insensitively")
    void findIgnoresCase() throws ExecutionException, InterruptedException {
        service.add(waypoint(OWNER, "SkyBase")).get();
        assertTrue(service.find(OWNER, "skybase").get().isPresent());
        assertTrue(service.find(OTHER, "skybase").get().isEmpty());
    }

    @Test
    @DisplayName("the marker cache is updated so the map reflects changes immediately")
    void updatesMarkerCache() throws ExecutionException, InterruptedException {
        service.add(waypoint(OWNER, "Base")).get();
        assertEquals(1, markers.cached(OWNER).size());

        service.remove(OWNER, "Base").get();
        assertTrue(markers.cached(OWNER).isEmpty());
    }

    @Test
    @DisplayName("concurrent adds cannot exceed the cap")
    void concurrentAddsRespectCap() throws InterruptedException {
        int threads = 8;
        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int offset = t * 100;
            workers[t] = new Thread(() -> {
                for (int i = 0; i < 100; i++) {
                    service.add(waypoint(OWNER, "wp" + (offset + i)));
                }
            });
            workers[t].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        assertEquals(WaypointService.MAX_PER_PLAYER,
                service.list(OWNER).join().size(),
                "the cap must hold under concurrent commands");
    }
}

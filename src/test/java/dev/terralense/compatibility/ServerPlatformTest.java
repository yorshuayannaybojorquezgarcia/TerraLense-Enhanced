package dev.terralense.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regression tests for platform detection. */
class ServerPlatformTest {
    @Test
    @DisplayName("the regionised scheduler API is present in plain Paper, so it cannot identify Folia")
    void schedulerApiIsNotAFoliaMarker() {
        // This assertion documents *why* the probe must not use this class: it resolves fine
        // against plain paper-api, which is what the test classpath provides.
        assertTrue(classPresent("io.papermc.paper.threadedregions.scheduler.RegionScheduler"),
                "RegionScheduler is expected on the Paper API classpath");
    }

    @Test
    @DisplayName("the Folia server implementation class is absent on Paper")
    void foliaImplementationClassAbsent() {
        assertFalse(classPresent("io.papermc.paper.threadedregions.RegionizedServer"),
                "RegionizedServer must not exist on plain Paper");
    }

    @Test
    @DisplayName("detection reports Paper when only the Paper API is present")
    void detectsPaper() {
        assertEquals(ServerPlatform.PAPER, ServerPlatform.detect());
        assertFalse(ServerPlatform.detect().isFolia());
    }

    @Test
    @DisplayName("display names are human readable")
    void displayNames() {
        assertEquals("Paper", ServerPlatform.PAPER.displayName());
        assertEquals("Folia", ServerPlatform.FOLIA.displayName());
        assertTrue(ServerPlatform.FOLIA.isFolia());
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}

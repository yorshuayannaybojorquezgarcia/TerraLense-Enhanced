package dev.terralense.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UpdateCheckerTest {

    @ParameterizedTest
    @CsvSource({
            "0.2.0, 0.1.0",
            "1.0.0, 0.9.9",
            "0.1.1, 0.1.0",
            "1.10.0, 1.9.0",
            "0.1.0, 0.0.9",
    })
    @DisplayName("a newer version compares greater")
    void newerIsGreater(String latest, String current) {
        assertTrue(UpdateChecker.compareVersions(latest, current) > 0,
                latest + " should be newer than " + current);
    }

    @Test
    @DisplayName("1.10 is newer than 1.9, not older")
    void comparesNumericallyNotLexically() {
        // String comparison would put "1.9" above "1.10", which is the classic bug here.
        assertTrue(UpdateChecker.compareVersions("1.10.0", "1.9.0") > 0);
    }

    @ParameterizedTest
    @CsvSource({
            "0.1.0, 0.1.0",
            "0.1, 0.1.0",
            "1.0.0, 1.0",
            "v0.1.0, 0.1.0",
    })
    @DisplayName("equivalent versions compare equal")
    void equivalentAreEqual(String left, String right) {
        assertEquals(0, UpdateChecker.compareVersions(left, right));
    }

    @Test
    @DisplayName("a pre-release suffix does not count as an update")
    void suffixIgnored() {
        // Otherwise a published 0.1.0 would look newer than a running 0.1.0-SNAPSHOT forever.
        assertEquals(0, UpdateChecker.compareVersions("0.1.0", "0.1.0-SNAPSHOT"));
        assertEquals(0, UpdateChecker.compareVersions("0.1.0-beta", "0.1.0"));
    }

    @Test
    @DisplayName("an older published version is not reported as an update")
    void olderIsNegative() {
        assertTrue(UpdateChecker.compareVersions("0.1.0", "0.2.0") < 0);
    }

    @Test
    @DisplayName("the name field is read from the version response")
    void parsesName() {
        String json = """
                {
                  "downloads": 0,
                  "name": "0.1.0",
                  "resource": 138116,
                  "id": 649569
                }
                """;
        assertEquals("0.1.0", UpdateChecker.parseName(json));
    }

    @Test
    @DisplayName("a malformed response yields null instead of throwing")
    void malformedIsNull() {
        assertNull(UpdateChecker.parseName(null));
        assertNull(UpdateChecker.parseName(""));
        assertNull(UpdateChecker.parseName("not json"));
        assertNull(UpdateChecker.parseName("{\"name\":"));
        assertNull(UpdateChecker.parseName("{\"other\": \"0.1.0\"}"));
    }

    @Test
    @DisplayName("an absurdly long name is rejected")
    void overlongNameRejected() {
        String json = "{\"name\": \"" + "9".repeat(500) + "\"}";
        assertNull(UpdateChecker.parseName(json));
    }

    @Test
    @DisplayName("garbage version strings do not throw")
    void garbageDoesNotThrow() {
        assertEquals(0, UpdateChecker.compareVersions("", ""));
        assertEquals(0, UpdateChecker.compareVersions(null, null));
        assertEquals(0, UpdateChecker.compareVersions("abc", "def"));
    }
}

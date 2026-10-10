package dev.terralense.api.marker;

import java.util.Locale;
import java.util.Optional;

/** Who can see a marker. */
public enum MarkerVisibility {
    /** Only the owner. */
    PRIVATE,

    /** Everyone on the server. */
    PUBLIC;

    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<MarkerVisibility> fromString(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "private", "personal", "own" -> Optional.of(PRIVATE);
            case "public", "shared", "server" -> Optional.of(PUBLIC);
            default -> Optional.empty();
        };
    }
}

package dev.terralense.config;

import java.util.Locale;
import java.util.Optional;

/** Outline shape used to mask the rendered minimap. */
public enum HudShape {
    SQUARE,
    ROUNDED_SQUARE,
    CIRCLE;

    public String configName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<HudShape> fromConfig(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(" ", "");
        return switch (key) {
            case "square", "box", "rect" -> Optional.of(SQUARE);
            case "rounded-square", "roundedsquare", "rounded" -> Optional.of(ROUNDED_SQUARE);
            case "circle", "round", "circular" -> Optional.of(CIRCLE);
            default -> Optional.empty();
        };
    }
}

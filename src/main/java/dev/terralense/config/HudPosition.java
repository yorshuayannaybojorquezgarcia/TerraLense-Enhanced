package dev.terralense.config;

import java.util.Locale;
import java.util.Optional;

/** Screen corner the minimap HUD is anchored to. */
public enum HudPosition {
    TOP_LEFT(true, true),
    TOP_RIGHT(false, true),
    BOTTOM_LEFT(true, false),
    BOTTOM_RIGHT(false, false);

    private final boolean left;
    private final boolean top;

    HudPosition(boolean left, boolean top) {
        this.left = left;
        this.top = top;
    }

    public boolean isLeft() {
        return left;
    }

    public boolean isTop() {
        return top;
    }

    /** The canonical spelling used in {@code config.yml}, e.g. {@code top-right}. */
    public String configName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<HudPosition> fromConfig(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(" ", "");
        return switch (key) {
            case "top-left", "topleft" -> Optional.of(TOP_LEFT);
            case "top-right", "topright" -> Optional.of(TOP_RIGHT);
            case "bottom-left", "bottomleft" -> Optional.of(BOTTOM_LEFT);
            case "bottom-right", "bottomright" -> Optional.of(BOTTOM_RIGHT);
            default -> Optional.empty();
        };
    }
}

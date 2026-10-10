package dev.terralense.config;

import java.util.Locale;
import java.util.Optional;

/** Visual presets; explicit hud.shape and hud.border remain available for classic mode. */
public enum HudVisualStyle {
    MODERN, CLASSIC, MINIMAL;

    public static Optional<HudVisualStyle> fromConfig(String raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public HudShape effectiveShape(HudShape configured) {
        return switch (this) {
            case MODERN -> HudShape.CIRCLE;
            case CLASSIC -> configured;
            case MINIMAL -> HudShape.CIRCLE;
        };
    }

    public int effectiveBorder(int configured) {
        return switch (this) {
            case MODERN -> configured == 0 ? 0 : 0xFF172A35;
            case CLASSIC -> configured;
            case MINIMAL -> 0;
        };
    }
}

package dev.terralense.config;

import java.util.Locale;
import java.util.Optional;

/** Which client UI surface the HUD is drawn on. */
public enum HudAnchor {
    /** The action bar, just above the hotbar. */
    ACTION_BAR,

    /** The boss bar, at the top of the screen. */
    BOSS_BAR,

    /** The scoreboard sidebar, upper right of the screen. */
    SIDEBAR;

    public String configName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** True when this surface fades on its own and needs periodic resending. */
    public boolean fades() {
        return this == ACTION_BAR;
    }

    /** Whether this surface is anchored to a screen edge rather than centred. */
    public boolean isEdgeAnchored() {
        return this == SIDEBAR;
    }

    public static Optional<HudAnchor> fromConfig(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(" ", "");
        return switch (key) {
            case "action-bar", "actionbar", "bottom", "hotbar" -> Optional.of(ACTION_BAR);
            case "boss-bar", "bossbar", "top" -> Optional.of(BOSS_BAR);
            case "sidebar", "scoreboard", "right", "corner" -> Optional.of(SIDEBAR);
            default -> Optional.empty();
        };
    }
}

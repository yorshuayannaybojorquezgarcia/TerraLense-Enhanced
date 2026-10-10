package dev.terralense.config;

import java.util.Locale;
import java.util.Optional;

/** How the terrain is oriented relative to the player's facing direction. */
public enum MapOrientation {
    /** North stays at the top; only the player arrow rotates. */
    NORTH_UP("north"),

    /** The player's facing direction stays at the top; the terrain rotates. */
    PLAYER_UP("player");

    private final String configName;

    MapOrientation(String configName) {
        this.configName = configName;
    }

    /** Short spelling accepted by {@code /terralense mode <north|player>}. */
    public String configName() {
        return configName;
    }

    public MapOrientation opposite() {
        return this == NORTH_UP ? PLAYER_UP : NORTH_UP;
    }

    public static Optional<MapOrientation> fromConfig(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(" ", "");
        return switch (key) {
            case "north", "north-up", "northup", "fixed" -> Optional.of(NORTH_UP);
            case "player", "player-up", "playerup", "rotating", "rotate" -> Optional.of(PLAYER_UP);
            default -> Optional.empty();
        };
    }
}

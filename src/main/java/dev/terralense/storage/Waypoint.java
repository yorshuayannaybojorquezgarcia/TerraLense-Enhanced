package dev.terralense.storage;

import dev.terralense.api.marker.MarkerVisibility;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A persisted waypoint.
 *
 * @param id database row id, or {@code 0} before insertion
 * @param owner owning player
 * @param name user-supplied name, already validated
 * @param worldId world the waypoint lives in
 * @param color packed RGB tint
 * @param icon marker icon key
 * @param visibility who may see it
 * @param createdAt epoch millis
 */
public record Waypoint(long id,
                       UUID owner,
                       String name,
                       UUID worldId,
                       int x,
                       int y,
                       int z,
                       int color,
                       String icon,
                       MarkerVisibility visibility,
                       long createdAt) {
    /** Maximum accepted name length. */
    public static final int MAX_NAME_LENGTH = 32;

    /** Accepted name characters. */
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    /** Y values outside the tallest supported world are rejected. */
    private static final int MIN_Y = -2048;
    private static final int MAX_Y = 2048;

    /** Horizontal limit matching Minecraft's world border maximum. */
    private static final int MAX_HORIZONTAL = 30_000_000;

    public Waypoint {
        if (owner == null || worldId == null) {
            throw new IllegalArgumentException("owner and worldId are required");
        }
        if (!isValidName(name)) {
            throw new IllegalArgumentException("invalid waypoint name: " + name);
        }
        if (!isValidPosition(x, y, z)) {
            throw new IllegalArgumentException("waypoint position out of range");
        }
        color &= 0x00FF_FFFF;
        icon = (icon == null || icon.isBlank()) ? "diamond" : icon;
        visibility = visibility == null ? MarkerVisibility.PRIVATE : visibility;
    }

    /** Validates a user-supplied waypoint name. */
    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    /** Validates coordinates against Minecraft's addressable range. */
    public static boolean isValidPosition(int x, int y, int z) {
        return y >= MIN_Y && y <= MAX_Y
                && Math.abs(x) <= MAX_HORIZONTAL
                && Math.abs(z) <= MAX_HORIZONTAL;
    }

    /** A copy carrying the database-assigned id. */
    public Waypoint withId(long newId) {
        return new Waypoint(newId, owner, name, worldId, x, y, z, color, icon, visibility, createdAt);
    }
}

package dev.terralense.api.marker;

import java.util.Objects;
import java.util.UUID;

/** A point of interest drawn on the minimap. */
public final class Marker {
    private final String id;
    private final MarkerType type;
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final int color;
    private final int glyphIndex;
    private final int priority;
    private final MarkerVisibility visibility;
    private final UUID owner;
    private final String label;

    private Marker(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.worldId = builder.worldId;
        this.x = builder.x;
        this.y = builder.y;
        this.z = builder.z;
        this.color = builder.color;
        this.glyphIndex = builder.glyphIndex;
        this.priority = builder.priority;
        this.visibility = builder.visibility;
        this.owner = builder.owner;
        this.label = builder.label;
    }

    public static Builder builder(String id, MarkerType type, UUID worldId) {
        return new Builder(id, type, worldId);
    }

    public String id() {
        return id;
    }

    public MarkerType type() {
        return type;
    }

    public UUID worldId() {
        return worldId;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    /** Packed RGB tint. */
    public int color() {
        return color;
    }

    public int glyphIndex() {
        return glyphIndex;
    }

    public int priority() {
        return priority;
    }

    public MarkerVisibility visibility() {
        return visibility;
    }

    /** Owning player, or {@code null} for server-owned markers. */
    public UUID owner() {
        return owner;
    }

    /** Optional human-readable label. */
    public String label() {
        return label;
    }

    /** Whether the given viewer is allowed to see this marker. */
    public boolean isVisibleTo(UUID viewer) {
        if (visibility == MarkerVisibility.PUBLIC) {
            return true;
        }
        return owner != null && owner.equals(viewer);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Marker marker
                && id.equals(marker.id)
                && type == marker.type
                && worldId.equals(marker.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, worldId);
    }

    @Override
    public String toString() {
        return "Marker[" + type + " " + id + " @" + x + "," + y + "," + z + "]";
    }

    /** Fluent builder; applies type defaults so callers only set what differs. */
    public static final class Builder {
        private final String id;
        private final MarkerType type;
        private final UUID worldId;
        private int x;
        private int y;
        private int z;
        private int color;
        private int glyphIndex;
        private int priority;
        private MarkerVisibility visibility = MarkerVisibility.PRIVATE;
        private UUID owner;
        private String label;

        private Builder(String id, MarkerType type, UUID worldId) {
            this.id = Objects.requireNonNull(id, "id");
            this.type = Objects.requireNonNull(type, "type");
            this.worldId = Objects.requireNonNull(worldId, "worldId");
            this.color = type.defaultColor();
            this.glyphIndex = type.glyphIndex();
            this.priority = type.priority();
        }

        public Builder position(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder color(int rgb) {
            this.color = rgb & 0x00FF_FFFF;
            return this;
        }

        public Builder glyphIndex(int glyphIndex) {
            if (glyphIndex < 0 || glyphIndex > 255) {
                throw new IllegalArgumentException("glyphIndex out of range: " + glyphIndex);
            }
            this.glyphIndex = glyphIndex;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder visibility(MarkerVisibility visibility) {
            this.visibility = Objects.requireNonNull(visibility, "visibility");
            return this;
        }

        public Builder owner(UUID owner) {
            this.owner = owner;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Marker build() {
            if (visibility == MarkerVisibility.PRIVATE && owner == null) {
                throw new IllegalStateException("a private marker requires an owner");
            }
            return new Marker(this);
        }
    }
}

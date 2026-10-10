package dev.terralense.map;

import dev.terralense.config.HudShape;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Precomputed per-cell visibility mask for a HUD shape. */
public final class ViewportMask {
    private static final Map<HudShape, Map<Integer, ViewportMask>> CACHE = new EnumMap<>(HudShape.class);

    static {
        for (HudShape shape : HudShape.values()) {
            CACHE.put(shape, new ConcurrentHashMap<>());
        }
    }

    private final int size;
    private final boolean[] visible;

    /** Cells on the outline, used to draw a border ring. */
    private final boolean[] border;

    private ViewportMask(int size, boolean[] visible, boolean[] border) {
        this.size = size;
        this.visible = visible;
        this.border = border;
    }

    /** True when the cell sits on the shape's outline. */
    public boolean isBorder(int x, int y) {
        if (x < 0 || y < 0 || x >= size || y >= size) {
            return false;
        }
        return border[y * size + x];
    }

    /** Returns a cached mask for the given shape and grid size. */
    public static ViewportMask of(HudShape shape, int size) {
        return CACHE.get(shape).computeIfAbsent(size, s -> build(shape, s));
    }

    private static ViewportMask build(HudShape shape, int size) {
        boolean[] visible = new boolean[size * size];
        double center = (size - 1) / 2.0D;
        double radius = size / 2.0D;
        // Corner radius for rounded-square: a quarter of the edge reads as "rounded" without
        // looking like a circle.
        double cornerRadius = size / 4.0D;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                visible[y * size + x] = switch (shape) {
                    case SQUARE -> true;
                    case CIRCLE -> {
                        double dx = x - center;
                        double dy = y - center;
                        yield Math.sqrt(dx * dx + dy * dy) <= radius - 0.5D;
                    }
                    case ROUNDED_SQUARE -> insideRoundedSquare(x, y, size, cornerRadius);
                };
            }
        }

        // A visible cell is on the border when any orthogonal neighbour is outside the shape.
        boolean[] border = new boolean[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!visible[y * size + x]) {
                    continue;
                }
                boolean edge = x == 0 || y == 0 || x == size - 1 || y == size - 1
                        || !visible[y * size + (x - 1)]
                        || !visible[y * size + (x + 1)]
                        || !visible[(y - 1) * size + x]
                        || !visible[(y + 1) * size + x];
                border[y * size + x] = edge;
            }
        }
        return new ViewportMask(size, visible, border);
    }

    private static boolean insideRoundedSquare(int x, int y, int size, double cornerRadius) {
        double maxIndex = size - 1;
        // Distance past the straight edges, only non-zero inside a corner box.
        double overX = Math.max(0.0D, Math.max(cornerRadius - x, x - (maxIndex - cornerRadius)));
        double overY = Math.max(0.0D, Math.max(cornerRadius - y, y - (maxIndex - cornerRadius)));
        if (overX == 0.0D || overY == 0.0D) {
            return true;
        }
        return Math.sqrt(overX * overX + overY * overY) <= cornerRadius;
    }

    public int size() {
        return size;
    }

    public boolean isVisible(int x, int y) {
        if (x < 0 || y < 0 || x >= size || y >= size) {
            return false;
        }
        return visible[y * size + x];
    }

    /** Number of visible cells; used by tests and metrics. */
    public int visibleCount() {
        int count = 0;
        for (boolean v : visible) {
            if (v) {
                count++;
            }
        }
        return count;
    }
}

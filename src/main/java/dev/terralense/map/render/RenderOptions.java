package dev.terralense.map.render;

import dev.terralense.config.HudShape;
import dev.terralense.config.MapOrientation;

/**
 * Immutable per-render parameters.
 *
 * @param outputSize edge length of the produced image, in cells
 * @param orientation whether terrain rotates with the player
 * @param rotation terrain rotation in degrees, already smoothed
 * @param shape mask applied to the output
 */
public record RenderOptions(int outputSize,
                            MapOrientation orientation,
                            float rotation,
                            HudShape shape,
                            boolean smoothing,
                            int borderArgb) {
    public RenderOptions {
        if (outputSize <= 0 || outputSize > 512) {
            throw new IllegalArgumentException("outputSize out of range: " + outputSize);
        }
        if (orientation == null || shape == null) {
            throw new IllegalArgumentException("orientation and shape are required");
        }
        if (!Float.isFinite(rotation)) {
            throw new IllegalArgumentException("rotation must be finite");
        }
    }

    /** Options with smoothing and border disabled, for exact-colour rendering. */
    public RenderOptions(int outputSize,
                         MapOrientation orientation,
                         float rotation,
                         HudShape shape) {
        this(outputSize, orientation, rotation, shape, false, 0);
    }

    /** Options with smoothing but no border. */
    public RenderOptions(int outputSize,
                         MapOrientation orientation,
                         float rotation,
                         HudShape shape,
                         boolean smoothing) {
        this(outputSize, orientation, rotation, shape, smoothing, 0);
    }

    /** True when the terrain itself must be rotated. */
    public boolean rotatesTerrain() {
        return orientation == MapOrientation.PLAYER_UP;
    }
}

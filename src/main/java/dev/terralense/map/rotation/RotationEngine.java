package dev.terralense.map.rotation;

/** Yaw normalisation and smoothing for map orientation. */
public final class RotationEngine {
    /** Below this angular difference the map is treated as settled. */
    public static final float SETTLED_EPSILON = 0.35F;

    private final double smoothing;
    private float currentAngle;
    private boolean initialised;

    /**
     * @param smoothing fraction of the remaining delta applied per update, in (0, 1].
     */
    public RotationEngine(double smoothing) {
        if (!(smoothing > 0.0D) || smoothing > 1.0D) {
            throw new IllegalArgumentException("smoothing must be in (0, 1], got " + smoothing);
        }
        this.smoothing = smoothing;
    }

    /** Normalises any angle into {@code [0, 360)}. */
    public static float normalize(float degrees) {
        if (!Float.isFinite(degrees)) {
            return 0.0F;
        }
        float wrapped = degrees % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        // A tiny negative input can round to exactly 360 after the add above.
        return wrapped == 360.0F ? 0.0F : wrapped;
    }

    /** Shortest signed difference {@code to - from}, in {@code (-180, 180]}. */
    public static float shortestDelta(float from, float to) {
        float delta = (normalize(to) - normalize(from)) % 360.0F;
        if (delta > 180.0F) {
            delta -= 360.0F;
        } else if (delta <= -180.0F) {
            delta += 360.0F;
        }
        return delta;
    }

    /** Converts player yaw into the terrain sampling angle for player-up mode. */
    public static float terrainRotationForYaw(float yaw) {
        return normalize(yaw + 180.0F);
    }

    /** Converts player yaw into the on-screen bearing of the player arrow when north stays up. */
    public static float arrowBearingForYaw(float yaw) {
        // Yaw 0 = south = pointing down the screen (180 degrees of bearing).
        return normalize(yaw + 180.0F);
    }

    /** Advances smoothing one step toward {@code targetDegrees} and returns the new angle. */
    public float approach(float targetDegrees) {
        float target = normalize(targetDegrees);
        if (!initialised) {
            initialised = true;
            currentAngle = target;
            return currentAngle;
        }
        if (smoothing >= 1.0D) {
            currentAngle = target;
            return currentAngle;
        }
        float delta = shortestDelta(currentAngle, target);
        if (Math.abs(delta) < SETTLED_EPSILON) {
            currentAngle = target;
            return currentAngle;
        }
        currentAngle = normalize(currentAngle + (float) (delta * smoothing));
        return currentAngle;
    }

    /** The angle currently displayed. */
    public float current() {
        return initialised ? currentAngle : 0.0F;
    }

    /** True when the displayed angle has reached its target. */
    public boolean isSettled(float targetDegrees) {
        return initialised
                && Math.abs(shortestDelta(currentAngle, targetDegrees)) < SETTLED_EPSILON;
    }

    /** Drops smoothing state, e.g. after a teleport or world change. */
    public void reset() {
        initialised = false;
        currentAngle = 0.0F;
    }

    /** Snaps immediately to an angle without interpolating. */
    public void snapTo(float degrees) {
        initialised = true;
        currentAngle = normalize(degrees);
    }
}

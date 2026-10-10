package dev.terralense.hud;

import java.util.List;

/**
 * Everything needed to draw one HUD frame besides the terrain pixels.
 *
 * @param arrowAngle on-screen bearing of the player arrow, in degrees
 * @param showArrow whether the centre arrow is drawn
 * @param markers markers to overlay, already filtered and positioned
 * @param horizontalOffsetCells horizontal placement in cells, relative to the action bar's centre;
 *     negative moves left
 */
public record HudFrame(float arrowAngle,
                       boolean showArrow,
                       List<HudMarker> markers,
                       int horizontalOffsetCells) {
    public HudFrame {
        markers = markers == null ? List.of() : List.copyOf(markers);
    }

    /** A frame with no horizontal offset, centred on the action bar. */
    public HudFrame(float arrowAngle, boolean showArrow, List<HudMarker> markers) {
        this(arrowAngle, showArrow, markers, 0);
    }

    public static HudFrame of(float arrowAngle) {
        return new HudFrame(arrowAngle, true, List.of(), 0);
    }
}

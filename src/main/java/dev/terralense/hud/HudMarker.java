package dev.terralense.hud;

/**
 * A marker resolved to map-cell coordinates, ready to draw.
 *
 * @param cellX column in the rendered image
 * @param cellY row in the rendered image
 * @param glyphIndex which marker glyph to use
 * @param argb tint colour
 * @param priority higher wins when two markers land on the same cell
 */
public record HudMarker(int cellX, int cellY, int glyphIndex, int argb, int priority) {
}

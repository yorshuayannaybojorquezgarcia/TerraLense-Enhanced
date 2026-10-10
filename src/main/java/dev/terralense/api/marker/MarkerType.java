package dev.terralense.api.marker;

/** Built-in marker kinds and their default appearance. */
public enum MarkerType {
    /** The viewing player; drawn as the rotating arrow, not a marker glyph. */
    SELF(0, 0xFFFFFF, 1000),

    /** Another player shown by the radar. */
    PLAYER(1, 0x6FD3F5, 600),

    /** A saved waypoint. */
    WAYPOINT(0, 0x4FD1A5, 500),

    /** Where the player last died. */
    DEATH(3, 0xE05C5C, 700),

    /** World or bed spawn. */
    SPAWN(2, 0xF5C46B, 400),

    /** A server-defined point of interest. */
    POI(0, 0xB08FE8, 300);

    private final int glyphIndex;
    private final int defaultColor;
    private final int priority;

    MarkerType(int glyphIndex, int defaultColor, int priority) {
        this.glyphIndex = glyphIndex;
        this.defaultColor = defaultColor;
        this.priority = priority;
    }

    public int glyphIndex() {
        return glyphIndex;
    }

    public int defaultColor() {
        return defaultColor;
    }

    public int priority() {
        return priority;
    }
}

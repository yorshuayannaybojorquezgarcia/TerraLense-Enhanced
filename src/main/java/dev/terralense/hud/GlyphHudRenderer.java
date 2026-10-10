package dev.terralense.hud;

import dev.terralense.map.MapImage;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Builds the minimap as a single styled {@link Component} of tinted font glyphs. */
public final class GlyphHudRenderer implements HudRenderer {
    private final Key fontKey;

    public GlyphHudRenderer(Key fontKey) {
        this.fontKey = fontKey;
    }

    /**
     * Renders each map row as its own component.
     *
     * @return one component per map row, top row first
     */
    public java.util.List<Component> renderRows(MapImage image, HudFrame frame) {
        int size = image.size();
        Component[][] overlay = buildOverlayLookup(image, frame);
        Style base = Style.style()
                .font(fontKey)
                .decoration(TextDecoration.ITALIC, false)
                .build();

        java.util.List<Component> rows = new java.util.ArrayList<>(size);
        for (int y = 0; y < size; y++) {
            Map<Integer, TextColor> colorCache = new HashMap<>();
            TextComponent.Builder row = Component.text().style(base);
            char rowGlyph = HudGlyphs.rowGlyph(y);

            for (int x = 0; x < size; x++) {
                Component overlayCell = overlay == null ? null : overlay[y][x];
                if (overlayCell != null) {
                    row.append(overlayCell);
                    row.append(Component.text(String.valueOf(HudGlyphs.KERN_GLYPH)).style(base));
                    continue;
                }
                int argb = image.get(x, y);
                if (isTransparent(argb)) {
                    // Blank cells still need to occupy their column.
                    row.append(Component.text(
                            String.valueOf(HudGlyphs.forwardSpaceGlyph(0))).style(base));
                    continue;
                }
                TextColor color = colorCache.computeIfAbsent(argb & 0x00FF_FFFF,
                        TextColor::color);
                row.append(Component.text(cellRun(rowGlyph, 1), color));
            }
            rows.add(row.build());
        }
        return rows;
    }

    @Override
    public Component render(MapImage image, HudFrame frame) {
        int size = image.size();
        Component[][] overlay = buildOverlayLookup(image, frame);
        // Horizontal placement, in cells, relative to the action bar's centre.
        int offsetCells = frame.horizontalOffsetCells();

        Map<Integer, TextColor> colorCache = new HashMap<>();
        // The font and italic-off are set once on the root so every child glyph inherits them;
        // setting them per component would bloat the packet.
        Style base = Style.style()
                .font(fontKey)
                .decoration(TextDecoration.ITALIC, false)
                .build();
        TextComponent.Builder root = Component.text().style(base);

        for (int y = 0; y < size; y++) {
            appendRow(root, image, overlay, y, size, base, colorCache, offsetCells);
        }
        return root.build();
    }

    /** Emits one map row. */
    private void appendRow(TextComponent.Builder root,
                           MapImage image,
                           Component[][] overlay,
                           int y,
                           int size,
                           Style base,
                           Map<Integer, TextColor> colorCache,
                           int offsetCells) {
        char rowGlyph = HudGlyphs.rowGlyph(y);
        int cursor = 0;
        int emitted = 0;

        // Shift the whole row horizontally before drawing any cells.
        if (offsetCells != 0) {
            appendShift(root, offsetCells, base);
        }

        int x = 0;
        while (x < size) {
            Component overlayCell = overlay == null ? null : overlay[y][x];
            if (overlayCell != null) {
                cursor += advanceTo(root, x, cursor, base);
                root.append(overlayCell);
                // Overlays occupy a cell too, so they need the same kern.
                root.append(Component.text(String.valueOf(HudGlyphs.KERN_GLYPH)).style(base));
                cursor++;
                emitted++;
                x++;
                continue;
            }

            int argb = image.get(x, y);
            if (isTransparent(argb)) {
                x++;
                continue;
            }

            // Extend a run of identical colour with no overlay.
            int runEnd = x + 1;
            while (runEnd < size
                    && image.get(runEnd, y) == argb
                    && (overlay == null || overlay[y][runEnd] == null)) {
                runEnd++;
            }
            int runLength = runEnd - x;

            cursor += advanceTo(root, x, cursor, base);
            TextColor color = colorCache.computeIfAbsent(argb & 0x00FF_FFFF, TextColor::color);
            // Each cell is followed by a -1px kern so columns touch instead of leaving a one-pixel
            // seam.
            root.append(Component.text(cellRun(rowGlyph, runLength), color));
            cursor += runLength;
            emitted += runLength;
            x = runEnd;
        }

        // Rewind the drawn cells and the leading offset, so every row starts from the same origin
        // and the component's net advance stays ~0.
        int travelled = cursor + Math.max(0, offsetCells);
        if (travelled > 0) {
            appendRewind(root, travelled, base);
        }
        if (offsetCells < 0) {
            // A negative offset moved left, so undo it by moving right.
            appendShift(root, -offsetCells, base);
        }
    }

    /** Moves the cursor horizontally by a signed number of cells. */
    private void appendShift(TextComponent.Builder root, int cells, Style base) {
        if (cells == 0) {
            return;
        }
        if (cells > 0) {
            root.append(Component.text(
                    powersOfTwo(cells, HudGlyphs.forwardSpaceGlyphCount(),
                            HudGlyphs::forwardSpaceGlyph)).style(base));
        } else {
            root.append(Component.text(
                    powersOfTwo(-cells, HudGlyphs.spaceGlyphCount(),
                            HudGlyphs::spaceGlyph)).style(base));
        }
    }

    /**
     * Emits forward spacing so the next glyph lands on column {@code target}.
     *
     * @return how far the cursor advanced, in cells
     */
    private int advanceTo(TextComponent.Builder root, int target, int cursor, Style base) {
        int gap = target - cursor;
        if (gap <= 0) {
            return 0;
        }
        root.append(Component.text(
                powersOfTwo(gap, HudGlyphs.forwardSpaceGlyphCount(),
                        HudGlyphs::forwardSpaceGlyph)).style(base));
        return gap;
    }

    /** Emits negative-space glyphs to return the cursor to column zero. */
    private void appendRewind(TextComponent.Builder root, int distance, Style base) {
        if (distance <= 0) {
            return;
        }
        String rewind = powersOfTwo(distance, HudGlyphs.spaceGlyphCount(),
                HudGlyphs::spaceGlyph);
        if (!rewind.isEmpty()) {
            root.append(Component.text(rewind).style(base));
        }
    }

    /** Builds a run of {@code count} cells, each followed by a kern glyph. */
    private static String cellRun(char rowGlyph, int count) {
        StringBuilder out = new StringBuilder(count * 2);
        for (int i = 0; i < count; i++) {
            out.append(rowGlyph).append(HudGlyphs.KERN_GLYPH);
        }
        return out.toString();
    }

    /** Expresses a cell distance as the fewest power-of-two spacing glyphs. */
    private static String powersOfTwo(int distance, int glyphCount,
                                      java.util.function.IntFunction<Character> glyph) {
        StringBuilder out = new StringBuilder();
        int remaining = distance;
        for (int index = glyphCount - 1; index >= 0 && remaining > 0; index--) {
            int step = 1 << index;
            while (remaining >= step) {
                out.append(glyph.apply(index).charValue());
                remaining -= step;
            }
        }
        return out.toString();
    }

    /**
     * Pre-resolves markers and the player arrow into a per-cell lookup so the row loop stays
     * O(width) instead of scanning the marker list per cell.
     */
    private Component[][] buildOverlayLookup(MapImage image, HudFrame frame) {
        int size = image.size();
        boolean hasArrow = frame.showArrow();
        if (!hasArrow && frame.markers().isEmpty()) {
            return null;
        }

        Component[][] overlay = new Component[size][size];
        int[] priorities = new int[size * size];
        java.util.Arrays.fill(priorities, Integer.MIN_VALUE);

        for (HudMarker marker : frame.markers()) {
            int x = marker.cellX();
            int y = marker.cellY();
            if (x < 0 || y < 0 || x >= size || y >= size) {
                continue;
            }
            int index = y * size + x;
            if (marker.priority() <= priorities[index]) {
                continue;
            }
            int shape = marker.glyphIndex();
            if (shape < 0 || shape >= HudGlyphs.MARKER_SHAPES) {
                // Unknown shapes draw nothing rather than picking a wrong glyph.
                continue;
            }
            priorities[index] = marker.priority();
            // Row is part of the glyph identity, because vertical position is baked into the
            // texture.
            overlay[y][x] = Component.text(
                    String.valueOf(HudGlyphs.markerGlyph(shape, y)),
                    TextColor.color(marker.argb() & 0x00FF_FFFF));
        }

        if (hasArrow) {
            // The local player always wins the centre cell.
            int center = size / 2;
            int step = HudGlyphs.arrowStepForAngle(frame.arrowAngle());
            overlay[center][center] = Component.text(
                    String.valueOf(HudGlyphs.arrowGlyph(step, center)),
                    TextColor.color(0xFFFFFF));
        }
        return overlay;
    }

    private static boolean isTransparent(int argb) {
        return ((argb >>> 24) & 0xFF) == 0;
    }
}

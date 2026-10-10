package dev.terralense.resourcepack;

import dev.terralense.hud.HudGlyphs;

/** Procedurally draws the font textures used by the HUD. */
final class GlyphArt {
    /** Texture pixels per cell. */
    static final int CELL_PIXELS = PackLayout.CELL_TEXTURE_PIXELS;

    private GlyphArt() {
    }

    /**
     * A row glyph: one opaque band in a transparent column.
     *
     * @param row map row this glyph represents, 0 at the top
     * @param totalRows rows in the texture, including bottom padding
     */
    static int[] rowCell(int row, int totalRows) {
        int width = CELL_PIXELS;
        int height = totalRows * CELL_PIXELS;
        int[] pixels = new int[width * height];

        int bandTop = row * CELL_PIXELS;
        for (int y = bandTop; y < bandTop + CELL_PIXELS; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = 0xFFFF_FFFF;
            }
        }
        return pixels;
    }

    /**
     * An icon glyph placed on a specific row band.
     *
     * @param icon the icon shape, {@code size} square
     * @param iconSize edge length of {@code icon}
     * @param row map row to place it on
     * @param totalRows rows in the texture
     */
    static int[] placeIcon(int[] icon, int iconSize, int row, int totalRows) {
        int width = CELL_PIXELS;
        int height = totalRows * CELL_PIXELS;
        int[] pixels = new int[width * height];

        // Centre the icon on the band; it may overhang vertically, which is fine and is what makes
        // markers legible at cell scale.
        int bandCenterY = row * CELL_PIXELS + CELL_PIXELS / 2;
        int startY = bandCenterY - iconSize / 2;
        int startX = width / 2 - iconSize / 2;

        for (int iy = 0; iy < iconSize; iy++) {
            int destY = startY + iy;
            if (destY < 0 || destY >= height) {
                continue;
            }
            for (int ix = 0; ix < iconSize; ix++) {
                int destX = startX + ix;
                if (destX < 0 || destX >= width) {
                    continue;
                }
                int argb = icon[iy * iconSize + ix];
                if (((argb >>> 24) & 0xFF) != 0) {
                    pixels[destY * width + destX] = argb;
                }
            }
        }
        return pixels;
    }

    /** The player arrow rotated to a given step, anti-aliased by supersampling. */
    static int[] arrow(int size, int step) {
        double angle = Math.toRadians(step * (360.0D / HudGlyphs.ARROW_STEPS));
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double center = (size - 1) / 2.0D;
        int samples = 3;

        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int covered = 0;
                int outlined = 0;
                for (int sy = 0; sy < samples; sy++) {
                    for (int sx = 0; sx < samples; sx++) {
                        double px = x + (sx + 0.5D) / samples - 0.5D;
                        double py = y + (sy + 0.5D) / samples - 0.5D;
                        double dx = px - center;
                        double dy = py - center;
                        double localX = dx * cos - dy * sin;
                        double localY = dx * sin + dy * cos;
                        double nx = localX / center;
                        double ny = localY / center;
                        if (insideArrow(nx, ny)) {
                            covered++;
                        } else if (insideArrow(nx * 0.78D, ny * 0.78D)) {
                            outlined++;
                        }
                    }
                }
                if (covered > 0) {
                    int alpha = (int) Math.round(255.0D * covered / (samples * samples));
                    pixels[y * size + x] = (alpha << 24) | 0x00F7_FCFD;
                } else if (outlined > 0) {
                    int alpha = (int) Math.round(230.0D * outlined / (samples * samples));
                    pixels[y * size + x] = (alpha << 24) | 0x0010_2939;
                }
            }
        }
        return pixels;
    }

    /**
     * Containment test for a triangular arrow in normalised -1..1 space, tip at the top (negative Y
     * is up on screen).
     */
    private static boolean insideArrow(double nx, double ny) {
        if (ny > 0.75D || ny < -0.95D) {
            return false;
        }
        double halfWidth = (ny + 0.9D) * 0.42D;
        if (Math.abs(nx) > halfWidth) {
            return false;
        }
        // Carve a tail notch so the arrow reads directionally.
        return !(ny > 0.35D && Math.abs(nx) < (ny - 0.35D) * 0.95D);
    }

    /** A filled diamond, used for waypoints. */
    static int[] diamond(int size) {
        int[] pixels = new int[size * size];
        double center = (size - 1) / 2.0D;
        double radius = center + 0.25D;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (Math.abs(x - center) + Math.abs(y - center) <= radius) {
                    pixels[y * size + x] = 0xFFFF_FFFF;
                }
            }
        }
        return pixels;
    }

    /** A hollow ring, used for spawn. */
    static int[] ring(int size) {
        int[] pixels = new int[size * size];
        double center = (size - 1) / 2.0D;
        double outer = center + 0.2D;
        double inner = outer * 0.5D;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x - center;
                double dy = y - center;
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance <= outer && distance >= inner) {
                    pixels[y * size + x] = 0xFFFF_FFFF;
                }
            }
        }
        return pixels;
    }

    /** A cross, used for death markers. */
    static int[] cross(int size) {
        int[] pixels = new int[size * size];
        double center = (size - 1) / 2.0D;
        double thickness = Math.max(1.0D, size / 8.0D);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x - center;
                double dy = y - center;
                boolean onDiagonal = Math.abs(dx - dy) <= thickness
                        || Math.abs(dx + dy) <= thickness;
                double reach = center + 0.2D;
                if (onDiagonal && Math.abs(dx) <= reach && Math.abs(dy) <= reach) {
                    pixels[y * size + x] = 0xFFFF_FFFF;
                }
            }
        }
        return pixels;
    }

    /** A small dot, used for other players. */
    static int[] dot(int size) {
        int[] pixels = new int[size * size];
        double center = (size - 1) / 2.0D;
        double radius = Math.max(1.0D, size * 0.3D);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x - center;
                double dy = y - center;
                if (Math.sqrt(dx * dx + dy * dy) <= radius) {
                    pixels[y * size + x] = 0xFFFF_FFFF;
                }
            }
        }
        return pixels;
    }
}

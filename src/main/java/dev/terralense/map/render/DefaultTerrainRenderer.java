package dev.terralense.map.render;

import dev.terralense.map.MapImage;
import dev.terralense.map.TerrainGrid;
import dev.terralense.map.ViewportMask;
import dev.terralense.terrain.TerrainCategory;
import dev.terralense.terrain.TerrainColorResolver;

/**
 * Default terrain renderer: samples a terrain grid into a masked, shaded image, applying map
 * rotation where required.
 */
public final class DefaultTerrainRenderer implements TerrainRenderer {
    private final TerrainColorResolver colors;

    public DefaultTerrainRenderer(TerrainColorResolver colors) {
        this.colors = colors;
    }

    @Override
    public MapImage render(TerrainGrid grid, RenderOptions options) {
        int outputSize = options.outputSize();
        MapImage image = new MapImage(outputSize);
        ViewportMask mask = ViewportMask.of(options.shape(), outputSize);

        double outCenter = (outputSize - 1) / 2.0D;
        double srcCenter = (grid.size() - 1) / 2.0D;

        // Precompute the rotation once per render rather than per pixel.
        boolean rotate = options.rotatesTerrain();
        double radians = Math.toRadians(options.rotation());
        double cos = rotate ? Math.cos(radians) : 1.0D;
        double sin = rotate ? Math.sin(radians) : 0.0D;
        double scale = grid.size() / (double) outputSize;

        for (int y = 0; y < outputSize; y++) {
            double dy = (y - outCenter) * scale;
            for (int x = 0; x < outputSize; x++) {
                if (!mask.isVisible(x, y)) {
                    image.set(x, y, 0);
                    continue;
                }
                double dx = (x - outCenter) * scale;

                double srcX;
                double srcZ;
                if (rotate) {
                    // Standard rotation matrix.
                    srcX = srcCenter + dx * cos - dy * sin;
                    srcZ = srcCenter + dx * sin + dy * cos;
                } else {
                    srcX = srcCenter + dx;
                    srcZ = srcCenter + dy;
                }

                int cellX = (int) Math.round(srcX);
                int cellZ = (int) Math.round(srcZ);
                if (!grid.contains(cellX, cellZ)) {
                    image.set(x, y, 0);
                    continue;
                }

                TerrainCategory category = grid.categoryAt(cellX, cellZ);
                if (category == TerrainCategory.UNKNOWN) {
                    image.set(x, y, 0);
                    continue;
                }
                int argb = colors.resolve(category, slopeAt(grid, cellX, cellZ));
                if (options.smoothing()) {
                    argb = blendWithNeighbours(grid, cellX, cellZ, argb);
                }
                // The border is drawn last so it is never blended away, giving the map a defined
                // edge against the world behind it.
                if (options.borderArgb() != 0 && mask.isBorder(x, y)) {
                    argb = options.borderArgb();
                }
                image.set(x, y, argb);
            }
        }
        return image;
    }

    /** Softens hard cell edges by averaging a cell with its four neighbours. */
    private int blendWithNeighbours(TerrainGrid grid, int x, int z, int centre) {
        // Centre weighted 4x so terrain stays legible rather than turning to mush.
        int weight = 4;
        int r = ((centre >>> 16) & 0xFF) * weight;
        int g = ((centre >>> 8) & 0xFF) * weight;
        int b = (centre & 0xFF) * weight;
        int total = weight;

        for (int i = 0; i < 4; i++) {
            int nx = x + (i == 0 ? -1 : i == 1 ? 1 : 0);
            int nz = z + (i == 2 ? -1 : i == 3 ? 1 : 0);
            if (!grid.contains(nx, nz)) {
                continue;
            }
            TerrainCategory neighbour = grid.categoryAt(nx, nz);
            if (neighbour == TerrainCategory.UNKNOWN) {
                continue;
            }
            int argb = colors.resolve(neighbour, slopeAt(grid, nx, nz));
            r += (argb >>> 16) & 0xFF;
            g += (argb >>> 8) & 0xFF;
            b += argb & 0xFF;
            total++;
        }
        return 0xFF00_0000
                | ((r / total) << 16)
                | ((g / total) << 8)
                | (b / total);
    }

    /** Relief shading value for a cell. */
    private static int slopeAt(TerrainGrid grid, int x, int z) {
        int here = grid.heightAt(x, z);
        int total = 0;
        int samples = 0;

        // North neighbour: reveals slopes running east-west.
        if (grid.contains(x, z - 1)
                && grid.categoryAt(x, z - 1) != TerrainCategory.UNKNOWN) {
            total += here - grid.heightAt(x, z - 1);
            samples++;
        }
        // West neighbour: reveals slopes running north-south, which a north-only comparison misses
        // entirely.
        if (grid.contains(x - 1, z)
                && grid.categoryAt(x - 1, z) != TerrainCategory.UNKNOWN) {
            total += here - grid.heightAt(x - 1, z);
            samples++;
        }
        if (samples == 0) {
            return 0;
        }
        // Average so a cell with one neighbour is not shaded twice as strongly.
        return total / samples;
    }
}

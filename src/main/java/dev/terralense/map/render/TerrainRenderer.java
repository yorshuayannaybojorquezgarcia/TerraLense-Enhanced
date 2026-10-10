package dev.terralense.map.render;

import dev.terralense.map.MapImage;
import dev.terralense.map.TerrainGrid;

/** Turns sampled terrain into pixels. */
public interface TerrainRenderer {
    /** Renders a terrain grid into an image. */
    MapImage render(TerrainGrid grid, RenderOptions options);
}

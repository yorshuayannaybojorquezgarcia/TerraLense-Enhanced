package dev.terralense.hud;

import dev.terralense.config.HudShape;
import dev.terralense.config.MapOrientation;
import dev.terralense.map.MapImage;
import dev.terralense.map.MapViewport;
import dev.terralense.map.TerrainGrid;
import dev.terralense.map.render.DefaultTerrainRenderer;
import dev.terralense.map.render.RenderOptions;
import dev.terralense.terrain.TerrainColorResolver;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Renders an enlarged map for the fullscreen view. */
public final class FullscreenMapView {
    /** Grid resolution for the large view. */
    public static final int GRID_SIZE = 24;

    private final GlyphHudRenderer glyphRenderer;
    private final TerrainColorResolver colors;

    public FullscreenMapView(GlyphHudRenderer glyphRenderer, TerrainColorResolver colors) {
        this.glyphRenderer = glyphRenderer;
        this.colors = colors;
    }

    /**
     * Renders the large map.
     *
     * @param grid terrain already sampled for the wider viewport
     * @param viewport the viewport the grid was sampled for
     * @param arrowAngle on-screen bearing of the player arrow
     */
    public Component render(TerrainGrid grid, MapViewport viewport, float arrowAngle) {
        // North-up: a large reference map is easier to read with a fixed orientation, and it
        // matches how players describe coordinates.
        MapImage image = new DefaultTerrainRenderer(colors).render(grid,
                new RenderOptions(GRID_SIZE, MapOrientation.NORTH_UP, 0.0F, HudShape.SQUARE));

        Component map = glyphRenderer.render(image, new HudFrame(arrowAngle, true, List.of()));

        return Component.text()
                .append(Component.text("TerraLense map", TextColor.color(0x4FD1A5),
                        TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Centre " + viewport.centerBlockX() + ", "
                                + viewport.centerBlockZ() + "  |  covering "
                                + viewport.spanBlocks() + "x" + viewport.spanBlocks()
                                + " blocks  |  north is up",
                        TextColor.color(0xD9E2DF)))
                .append(Component.newline())
                .append(map)
                .build();
    }
}

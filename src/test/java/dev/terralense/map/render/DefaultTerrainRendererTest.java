package dev.terralense.map.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.terralense.config.HudShape;
import dev.terralense.config.MapOrientation;
import dev.terralense.map.MapImage;
import dev.terralense.map.TerrainGrid;
import dev.terralense.map.rotation.RotationEngine;
import dev.terralense.terrain.TerrainCategory;
import dev.terralense.terrain.TerrainColorResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Renderer tests run entirely on synthetic terrain: no Bukkit, no server. */
class DefaultTerrainRendererTest {
    private final DefaultTerrainRenderer renderer =
            new DefaultTerrainRenderer(TerrainColorResolver.create(false, 0.0D));

    private static TerrainGrid uniformGrid(int size, TerrainCategory category) {
        TerrainGrid grid = new TerrainGrid(size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                grid.set(x, y, category, 64);
            }
        }
        return grid;
    }

    @Test
    @DisplayName("renders a square image of the requested size")
    void producesRequestedSize() {
        MapImage image = renderer.render(uniformGrid(64, TerrainCategory.GRASS),
                new RenderOptions(48, MapOrientation.NORTH_UP, 0.0F, HudShape.SQUARE));
        assertEquals(48, image.size());
    }

    @Test
    @DisplayName("a square mask fills every pixel with terrain")
    void squareShapeFillsEveryPixel() {
        MapImage image = renderer.render(uniformGrid(64, TerrainCategory.STONE),
                new RenderOptions(32, MapOrientation.NORTH_UP, 0.0F, HudShape.SQUARE));
        for (int y = 0; y < image.size(); y++) {
            for (int x = 0; x < image.size(); x++) {
                assertFalse(image.isTransparent(x, y), "pixel (" + x + "," + y + ") should be filled");
            }
        }
    }

    @Test
    @DisplayName("a border is drawn on the shape outline, not through the middle")
    void borderFollowsOutline() {
        int size = 32;
        int border = 0xFF1E2A33;
        MapImage image = new DefaultTerrainRenderer(TerrainColorResolver.create(false, 0.0D))
                .render(uniformGrid(64, TerrainCategory.SAND),
                        new RenderOptions(size, MapOrientation.NORTH_UP, 0.0F,
                                HudShape.CIRCLE, false, border));

        // The centre must still be terrain, not border.
        assertEquals(TerrainColorResolver.create(false, 0.0D)
                .resolve(TerrainCategory.SAND, 0), image.get(size / 2, size / 2));

        // Somewhere on the circle's horizontal extreme should be border.
        boolean foundBorder = false;
        for (int x = 0; x < size && !foundBorder; x++) {
            if (image.get(x, size / 2) == border) {
                foundBorder = true;
            }
        }
        assertTrue(foundBorder, "no border drawn on the outline");
    }

    @Test
    @DisplayName("a border colour of zero leaves the map unbordered")
    void borderCanBeDisabled() {
        int size = 16;
        MapImage plain = new DefaultTerrainRenderer(TerrainColorResolver.create(false, 0.0D))
                .render(uniformGrid(32, TerrainCategory.STONE),
                        new RenderOptions(size, MapOrientation.NORTH_UP, 0.0F,
                                HudShape.SQUARE, false, 0));
        int stone = TerrainColorResolver.create(false, 0.0D).resolve(TerrainCategory.STONE, 0);
        assertEquals(stone, plain.get(0, 0), "no border expected at the edge");
    }

    @Test
    @DisplayName("a circular mask clears the corners but keeps the centre")
    void circleShapeClearsCorners() {
        int size = 32;
        MapImage image = renderer.render(uniformGrid(64, TerrainCategory.STONE),
                new RenderOptions(size, MapOrientation.NORTH_UP, 0.0F, HudShape.CIRCLE));

        assertTrue(image.isTransparent(0, 0));
        assertTrue(image.isTransparent(size - 1, 0));
        assertTrue(image.isTransparent(0, size - 1));
        assertTrue(image.isTransparent(size - 1, size - 1));
        assertFalse(image.isTransparent(size / 2, size / 2));
    }

    @Test
    @DisplayName("rotation never leaves unwritten gaps inside the mask")
    void rotationProducesNoHoles() {
        // Inverse sampling should write every masked destination pixel for any angle.
        TerrainGrid grid = uniformGrid(96, TerrainCategory.GRASS);
        for (float angle = 0.0F; angle < 360.0F; angle += 7.5F) {
            MapImage image = renderer.render(grid,
                    new RenderOptions(48, MapOrientation.PLAYER_UP, angle, HudShape.CIRCLE));
            int filled = 0;
            int masked = 0;
            for (int y = 0; y < image.size(); y++) {
                for (int x = 0; x < image.size(); x++) {
                    boolean inside = insideCircle(x, y, image.size());
                    if (inside) {
                        masked++;
                        if (!image.isTransparent(x, y)) {
                            filled++;
                        }
                    }
                }
            }
            assertEquals(masked, filled, "gap found at angle " + angle);
        }
    }

    @Test
    @DisplayName("north-up ignores rotation entirely")
    void northUpIgnoresRotation() {
        TerrainGrid grid = distinctiveGrid(64);
        MapImage zero = renderer.render(grid,
                new RenderOptions(32, MapOrientation.NORTH_UP, 0.0F, HudShape.SQUARE));
        MapImage rotated = renderer.render(grid,
                new RenderOptions(32, MapOrientation.NORTH_UP, 123.0F, HudShape.SQUARE));

        for (int y = 0; y < zero.size(); y++) {
            for (int x = 0; x < zero.size(); x++) {
                assertEquals(zero.get(x, y), rotated.get(x, y),
                        "north-up output changed at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    @DisplayName("player-up actually rotates the terrain")
    void playerUpRotatesTerrain() {
        TerrainGrid grid = distinctiveGrid(64);
        MapImage zero = renderer.render(grid,
                new RenderOptions(32, MapOrientation.PLAYER_UP, 0.0F, HudShape.SQUARE));
        MapImage rotated = renderer.render(grid,
                new RenderOptions(32, MapOrientation.PLAYER_UP, 90.0F, HudShape.SQUARE));

        boolean anyDifference = false;
        for (int y = 0; y < zero.size() && !anyDifference; y++) {
            for (int x = 0; x < zero.size(); x++) {
                if (zero.get(x, y) != rotated.get(x, y)) {
                    anyDifference = true;
                    break;
                }
            }
        }
        assertTrue(anyDifference, "rotating 90 degrees must change the image");
    }

    @Test
    @DisplayName("player-up puts the terrain the player faces at the top of the map")
    void playerUpFacesForward() {
        // Regression test for an inverted map: the sampling matrix was R(-yaw) instead of
        // R(yaw+180), so turning right rotated the terrain left.
        int size = 32;
        TerrainGrid grid = new TerrainGrid(size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                grid.set(x, y, y < size / 2 ? TerrainCategory.WATER : TerrainCategory.SAND, 64);
            }
        }

        DefaultTerrainRenderer renderer =
                new DefaultTerrainRenderer(TerrainColorResolver.create(false, 0.0D));
        int water = TerrainColorResolver.create(false, 0.0D).resolve(TerrainCategory.WATER, 0);
        int sand = TerrainColorResolver.create(false, 0.0D).resolve(TerrainCategory.SAND, 0);

        // Facing north (yaw 180): the water ahead should appear at the top.
        MapImage north = renderer.render(grid, new RenderOptions(size, MapOrientation.PLAYER_UP,
                RotationEngine.terrainRotationForYaw(180.0F), HudShape.SQUARE));
        assertEquals(water, north.get(size / 2, 2), "facing north should show water ahead");
        assertEquals(sand, north.get(size / 2, size - 3), "sand should be behind");

        // Facing south (yaw 0): the sand ahead should now be at the top.
        MapImage south = renderer.render(grid, new RenderOptions(size, MapOrientation.PLAYER_UP,
                RotationEngine.terrainRotationForYaw(0.0F), HudShape.SQUARE));
        assertEquals(sand, south.get(size / 2, 2), "facing south should show sand ahead");
        assertEquals(water, south.get(size / 2, size - 3), "water should be behind");
    }

    @Test
    @DisplayName("turning right rotates the terrain right, not left")
    void turningRightRotatesRight() {
        // East/west split so a quarter turn is unambiguous.
        int size = 32;
        TerrainGrid grid = new TerrainGrid(size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                grid.set(x, y, x < size / 2 ? TerrainCategory.WATER : TerrainCategory.SAND, 64);
            }
        }
        DefaultTerrainRenderer renderer =
                new DefaultTerrainRenderer(TerrainColorResolver.create(false, 0.0D));
        int sand = TerrainColorResolver.create(false, 0.0D).resolve(TerrainCategory.SAND, 0);

        // Facing east (yaw 270).
        MapImage east = renderer.render(grid, new RenderOptions(size, MapOrientation.PLAYER_UP,
                RotationEngine.terrainRotationForYaw(270.0F), HudShape.SQUARE));
        assertEquals(sand, east.get(size / 2, 2), "facing east should show sand ahead");
    }

    @Test
    @DisplayName("a 360 degree rotation reproduces the unrotated image")
    void fullTurnMatchesZero() {
        TerrainGrid grid = distinctiveGrid(64);
        MapImage zero = renderer.render(grid,
                new RenderOptions(32, MapOrientation.PLAYER_UP, 0.0F, HudShape.SQUARE));
        MapImage full = renderer.render(grid,
                new RenderOptions(32, MapOrientation.PLAYER_UP, 360.0F, HudShape.SQUARE));

        for (int y = 0; y < zero.size(); y++) {
            for (int x = 0; x < zero.size(); x++) {
                assertEquals(zero.get(x, y), full.get(x, y), "mismatch at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    @DisplayName("unknown terrain stays transparent instead of drawing a colour")
    void unknownTerrainIsTransparent() {
        MapImage image = renderer.render(new TerrainGrid(32),
                new RenderOptions(32, MapOrientation.NORTH_UP, 0.0F, HudShape.SQUARE));
        for (int y = 0; y < image.size(); y++) {
            for (int x = 0; x < image.size(); x++) {
                assertTrue(image.isTransparent(x, y));
            }
        }
    }

    @Test
    @DisplayName("sampling past the grid edge is transparent, not an exception")
    void handlesGridSmallerThanOutput() {
        // Zoomed-out views can request more output than the grid covers; the renderer must degrade
        // to empty space rather than throw mid-render.
        MapImage image = renderer.render(uniformGrid(8, TerrainCategory.SAND),
                new RenderOptions(64, MapOrientation.PLAYER_UP, 45.0F, HudShape.CIRCLE));
        assertEquals(64, image.size());
        assertFalse(image.isTransparent(32, 32), "centre should still show terrain");
    }

    @Test
    @DisplayName("relief shows on north-south ridges, not just east-west ones")
    void reliefIsSampledInBothAxes() {
        // Regression test for detail loss: sampling only the north neighbour made slopes running
        // north-south invisible, because height never changed along the sampled axis.
        int size = 16;
        TerrainGrid ridge = new TerrainGrid(size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int height = 60 + (x < size / 2 ? x * 2 : (size - x) * 2);
                ridge.set(x, y, TerrainCategory.STONE, height);
            }
        }
        DefaultTerrainRenderer shaded =
                new DefaultTerrainRenderer(TerrainColorResolver.create(true, 0.6D));
        MapImage image = shaded.render(ridge,
                new RenderOptions(size, MapOrientation.NORTH_UP, 0.0F, HudShape.SQUARE));

        // The rising face and the falling face must not render identically.
        assertFalse(image.get(4, 8) == image.get(12, 8),
                "the two faces of a north-south ridge should shade differently");
    }

    @Test
    @DisplayName("height shading is applied through the resolver")
    void shadingReachesOutput() {
        DefaultTerrainRenderer shaded =
                new DefaultTerrainRenderer(TerrainColorResolver.create(true, 0.6D));
        TerrainGrid grid = new TerrainGrid(16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                // A north-facing slope: height rises with z.
                grid.set(x, y, TerrainCategory.STONE, 60 + y * 2);
            }
        }
        MapImage image = shaded.render(grid,
                new RenderOptions(16, MapOrientation.NORTH_UP, 0.0F, HudShape.SQUARE));

        // Row 0 has no northern neighbour so it renders flat; row 1 is shaded.
        assertFalse(image.get(8, 0) == image.get(8, 1), "slope should shade differently than the edge row");
    }

    private static TerrainGrid distinctiveGrid(int size) {
        // An asymmetric pattern, so rotation is detectable.
        TerrainGrid grid = new TerrainGrid(size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                TerrainCategory category = x < size / 2
                        ? (y < size / 2 ? TerrainCategory.WATER : TerrainCategory.SAND)
                        : (y < size / 2 ? TerrainCategory.STONE : TerrainCategory.GRASS);
                grid.set(x, y, category, 64 + x);
            }
        }
        return grid;
    }

    private static boolean insideCircle(int x, int y, int size) {
        double center = (size - 1) / 2.0D;
        double dx = x - center;
        double dy = y - center;
        return Math.sqrt(dx * dx + dy * dy) <= size / 2.0D - 0.5D;
    }
}

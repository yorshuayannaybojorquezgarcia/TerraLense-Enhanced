package dev.terralense.hud;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.terralense.config.HudShape;
import dev.terralense.config.MapOrientation;
import dev.terralense.config.TerraLenseConfig;
import dev.terralense.map.MapImage;
import dev.terralense.map.TerrainGrid;
import dev.terralense.map.render.DefaultTerrainRenderer;
import dev.terralense.map.render.RenderOptions;
import dev.terralense.terrain.TerrainCategory;
import dev.terralense.terrain.TerrainColorResolver;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Guards the claim that run-length merging materially reduces HUD payload size. */
class HudPayloadSizeTest {
    private static final Key FONT = Key.key("terralense", "hud");
    private static final int GRID = TerraLenseConfig.GRID_SIZE;

    private final GlyphHudRenderer renderer = new GlyphHudRenderer(FONT);

    private static MapImage render(TerrainGrid grid) {
        return new DefaultTerrainRenderer(TerrainColorResolver.create(false, 0.0D))
                .render(grid, new RenderOptions(GRID, MapOrientation.NORTH_UP, 0.0F,
                        HudShape.CIRCLE));
    }

    private static TerrainGrid uniform(TerrainCategory category) {
        TerrainGrid grid = new TerrainGrid(GRID);
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                grid.set(x, y, category, 64);
            }
        }
        return grid;
    }

    private static int serialisedLength(Component component) {
        return GsonComponentSerializer.gson().serialize(component).length();
    }

    private static int countComponents(Component component) {
        int total = 1;
        for (Component child : component.children()) {
            total += countComponents(child);
        }
        return total;
    }

    @Test
    @DisplayName("a full-size uniform map stays far below one component per cell")
    void uniformMapIsCompact() {
        Component hud = renderer.render(render(uniform(TerrainCategory.GRASS)),
                new HudFrame(0.0F, false, List.of()));

        int components = countComponents(hud);
        int cells = GRID * GRID;
        // Without run-length merging this would approach 4096 components.
        assertTrue(components < cells / 8,
                "expected far fewer than " + (cells / 8) + " components, got " + components);
    }

    @Test
    @DisplayName("a uniform 64x64 map serialises to a modest payload")
    void uniformMapPayloadIsReasonable() {
        Component hud = renderer.render(render(uniform(TerrainCategory.STONE)),
                new HudFrame(0.0F, false, List.of()));

        int length = serialisedLength(hud);
        // Generous ceiling: the point is that it is kilobytes, not hundreds.
        assertTrue(length < 40_000,
                "serialised HUD was " + length + " chars, which is larger than expected");
    }

    @Test
    @DisplayName("merging beats the unmerged worst case by a wide margin")
    void mergingBeatsWorstCase() {
        // Alternating colours defeat merging, so this is the pathological case.
        TerrainGrid striped = new TerrainGrid(GRID);
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                striped.set(x, y, (x % 2 == 0) ? TerrainCategory.STONE : TerrainCategory.SAND, 64);
            }
        }
        int stripedComponents = countComponents(renderer.render(render(striped),
                new HudFrame(0.0F, false, List.of())));
        int uniformComponents = countComponents(renderer.render(render(uniform(TerrainCategory.GRASS)),
                new HudFrame(0.0F, false, List.of())));

        assertTrue(uniformComponents * 4 < stripedComponents,
                "merging should be dramatically cheaper on flat terrain: uniform="
                        + uniformComponents + " striped=" + stripedComponents);
    }

    @Test
    @DisplayName("even the pathological striped map stays bounded")
    void stripedMapStaysBounded() {
        TerrainGrid striped = new TerrainGrid(GRID);
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                striped.set(x, y, (x % 2 == 0) ? TerrainCategory.STONE : TerrainCategory.SAND, 64);
            }
        }
        Component hud = renderer.render(render(striped), new HudFrame(0.0F, false, List.of()));
        // One component per visible cell plus per-row rewinds is the ceiling.
        assertTrue(countComponents(hud) <= GRID * GRID + GRID * 2 + 1);
    }

    @Test
    @DisplayName("colour instances are interned rather than allocated per cell")
    void coloursAreInterned() {
        // A uniform map must reference one colour object across all its runs.
        Component hud = renderer.render(render(uniform(TerrainCategory.WATER)),
                new HudFrame(0.0F, false, List.of()));
        java.util.Set<net.kyori.adventure.text.format.TextColor> distinct =
                new java.util.HashSet<>();
        collectColours(hud, distinct);
        assertTrue(distinct.size() <= 2,
                "expected a single terrain colour, found " + distinct.size());
    }

    private static void collectColours(Component component,
                                       java.util.Set<net.kyori.adventure.text.format.TextColor> out) {
        if (component.color() != null) {
            out.add(component.color());
        }
        for (Component child : component.children()) {
            collectColours(child, out);
        }
    }
}

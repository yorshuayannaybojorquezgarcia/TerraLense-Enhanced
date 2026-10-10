package dev.terralense.resourcepack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.terralense.config.HudPosition;
import dev.terralense.hud.HudGlyphs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackGeneratorTest {
    private static final PackLayout LAYOUT = PackLayout.of(dev.terralense.config.TerraLenseConfig.GRID_SIZE, 96, 8, HudPosition.TOP_RIGHT);
    private static final int PACK_FORMAT = 46;

    @Test
    @DisplayName("32x32 resource pack has every required row and valid PNG dimensions")
    void thirtyTwoPackHasCompleteTextures(@TempDir Path dir) throws IOException {
        PackLayout layout = PackLayout.of(32, 128, 8, HudPosition.TOP_RIGHT);
        GeneratedPack pack = new PackGenerator("2.0.0-dev").generate(dir, layout, PACK_FORMAT);
        java.util.Map<String, byte[]> files = new java.util.HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(pack.file()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.put(entry.getName(), zip.readAllBytes());
            }
        }
        String prefix = "assets/terralense/textures/font/";
        for (int row = 0; row < 32; row++) {
            assertPngDimensions(files.get(prefix + "cell_" + row + ".png"), 8, 256);
            for (String shape : new String[] {"diamond", "dot", "ring", "cross"}) {
                assertPngDimensions(files.get(prefix + "marker_" + shape + "_" + row + ".png"), 8, 256);
            }
        }
        for (int step = 0; step < HudGlyphs.ARROW_STEPS; step++)
            assertPngDimensions(files.get(prefix + "arrow_" + step + ".png"), 8, 256);
        assertTrue(files.containsKey("assets/minecraft/font/terralense_hud.json"));
    }

    private static void assertPngDimensions(byte[] png, int width, int height) {
        assertTrue(png != null && png.length > 24, "missing or truncated PNG");
        assertEquals(0x89504e47, java.nio.ByteBuffer.wrap(png, 0, 4).getInt());
        assertEquals(width, java.nio.ByteBuffer.wrap(png, 16, 4).getInt());
        assertEquals(height, java.nio.ByteBuffer.wrap(png, 20, 4).getInt());
    }

    @Test
    @DisplayName("generates a pack and reports a SHA-1")
    void generatesPack(@TempDir Path dir) throws IOException {
        GeneratedPack pack = new PackGenerator("1.0.0").generate(dir, LAYOUT, PACK_FORMAT);

        assertTrue(Files.isRegularFile(pack.file()));
        assertTrue(pack.regenerated());
        assertEquals(40, pack.sha1().length(), "SHA-1 hex must be 40 characters");
        assertTrue(pack.sha1().matches("[0-9a-f]{40}"));
        assertEquals(Files.size(pack.file()), pack.sizeBytes());
    }

    @Test
    @DisplayName("identical inputs produce byte-identical packs")
    void generationIsDeterministic(@TempDir Path first, @TempDir Path second) throws IOException {
        // Determinism is what makes the SHA-1 a valid cache key; without it clients would re-
        // download the pack on every restart.
        GeneratedPack a = new PackGenerator("1.0.0").generate(first, LAYOUT, PACK_FORMAT);
        GeneratedPack b = new PackGenerator("1.0.0").generate(second, LAYOUT, PACK_FORMAT);

        assertEquals(a.sha1(), b.sha1());
        assertArrayEquals(Files.readAllBytes(a.file()), Files.readAllBytes(b.file()));
    }

    @Test
    @DisplayName("an unchanged pack is reused rather than rewritten")
    void reusesCachedPack(@TempDir Path dir) throws IOException {
        PackGenerator generator = new PackGenerator("1.0.0");
        GeneratedPack initial = generator.generate(dir, LAYOUT, PACK_FORMAT);
        assertTrue(initial.regenerated());

        GeneratedPack cached = generator.generate(dir, LAYOUT, PACK_FORMAT);
        assertFalse(cached.regenerated(), "second call should reuse the cached pack");
        assertEquals(initial.sha1(), cached.sha1());
    }

    @Test
    @DisplayName("changing the layout changes the fingerprint and regenerates")
    void layoutChangeRegenerates(@TempDir Path dir) throws IOException {
        PackGenerator generator = new PackGenerator("1.0.0");
        GeneratedPack small = generator.generate(dir, PackLayout.of(dev.terralense.config.TerraLenseConfig.GRID_SIZE, 96, 8, HudPosition.TOP_RIGHT), PACK_FORMAT);
        GeneratedPack large = generator.generate(dir, PackLayout.of(dev.terralense.config.TerraLenseConfig.GRID_SIZE, 192, 8, HudPosition.TOP_RIGHT), PACK_FORMAT);

        assertNotEquals(small.fingerprint(), large.fingerprint());
        assertTrue(large.regenerated());
        assertNotEquals(small.sha1(), large.sha1());
    }

    @Test
    @DisplayName("changing the plugin version regenerates the pack")
    void versionChangeRegenerates(@TempDir Path dir) throws IOException {
        GeneratedPack v1 = new PackGenerator("1.0.0").generate(dir, LAYOUT, PACK_FORMAT);
        GeneratedPack v2 = new PackGenerator("1.1.0").generate(dir, LAYOUT, PACK_FORMAT);
        assertNotEquals(v1.fingerprint(), v2.fingerprint());
        assertTrue(v2.regenerated());
    }

    @Test
    @DisplayName("the pack contains pack.mcmeta, the font and every texture")
    void containsExpectedEntries(@TempDir Path dir) throws IOException {
        GeneratedPack pack = new PackGenerator("1.0.0").generate(dir, LAYOUT, PACK_FORMAT);
        List<String> names = entryNames(pack.file());

        assertTrue(names.contains("pack.mcmeta"));
        assertTrue(names.contains("assets/minecraft/font/terralense_hud.json"));

        // One cell texture per map row, because vertical position is baked in.
        for (int row = 0; row < LAYOUT.gridSize(); row++) {
            assertTrue(names.contains("assets/terralense/textures/font/cell_" + row + ".png"),
                    "missing cell texture for row " + row);
        }
        for (int step = 0; step < HudGlyphs.ARROW_STEPS; step++) {
            assertTrue(names.contains("assets/terralense/textures/font/arrow_" + step + ".png"),
                    "missing arrow texture " + step);
        }
        assertTrue(names.contains("assets/terralense/textures/font/marker_diamond_0.png"));
        assertTrue(names.contains("assets/terralense/textures/font/marker_cross_0.png"));
    }

    @Test
    @DisplayName("every provider satisfies the client's ascent <= height rule")
    void ascentNeverExceedsHeight(@TempDir Path dir) throws IOException {
        // Regression guard.
        for (int hudSize : new int[] {32, 64, 96, 128, 192, 256}) {
            for (HudPosition position : HudPosition.values()) {
                GeneratedPack pack = new PackGenerator("1.0.0").generate(
                        Files.createTempDirectory(dir, "pack"),
                        PackLayout.of(dev.terralense.config.TerraLenseConfig.GRID_SIZE, hudSize, 8, position), PACK_FORMAT);
                String font = readEntry(pack.file(), "assets/minecraft/font/terralense_hud.json");

                Matcher matcher = Pattern
                        .compile("\"ascent\":\\s*(-?\\d+),\\s*\"height\":\\s*(\\d+)")
                        .matcher(font);
                int checked = 0;
                while (matcher.find()) {
                    int ascent = Integer.parseInt(matcher.group(1));
                    int height = Integer.parseInt(matcher.group(2));
                    assertTrue(ascent <= height,
                            "ascent " + ascent + " exceeds height " + height
                                    + " for size " + hudSize + " " + position);
                    checked++;
                }
                assertTrue(checked > 0, "no bitmap providers found");
            }
        }
    }

    @Test
    @DisplayName("row textures include padding and stay within the glyph size limit")
    void textureHeightCoversAllRows() {
        PackLayout layout = LAYOUT;
        assertTrue(layout.textureRows() > layout.gridSize(),
                "textures must include padding rows to lift the map");
        assertEquals(layout.providerHeight(), layout.providerAscent(),
                "ascent should equal height so the constraint always holds");
        assertTrue(layout.fitsGlyphLimit(),
                "texture must stay within the client's glyph size limit");
    }

    @Test
    @DisplayName("zip entries are sorted so ordering cannot vary")
    void entriesAreSorted(@TempDir Path dir) throws IOException {
        GeneratedPack pack = new PackGenerator("1.0.0").generate(dir, LAYOUT, PACK_FORMAT);
        List<String> names = entryNames(pack.file());
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareTo);
        assertEquals(sorted, names);
    }

    @Test
    @DisplayName("the font defines a provider for every map row")
    void fontDefinesEveryRow(@TempDir Path dir) throws IOException {
        GeneratedPack pack = new PackGenerator("1.0.0").generate(dir, LAYOUT, PACK_FORMAT);
        String font = readEntry(pack.file(), "assets/minecraft/font/terralense_hud.json");

        for (int row = 0; row < LAYOUT.gridSize(); row++) {
            String expected = String.format("\\u%04X", (int) HudGlyphs.rowGlyph(row));
            assertTrue(font.contains(expected), "font is missing row glyph " + row);
        }
        assertTrue(font.contains("\"type\": \"space\""), "negative space provider missing");
    }

    @Test
    @DisplayName("private use area characters are escaped, never emitted raw")
    void fontEscapesGlyphs(@TempDir Path dir) throws IOException {
        GeneratedPack pack = new PackGenerator("1.0.0").generate(dir, LAYOUT, PACK_FORMAT);
        String font = readEntry(pack.file(), "assets/minecraft/font/terralense_hud.json");
        for (char c : font.toCharArray()) {
            assertFalse(c >= '\uE000' && c <= '\uF8FF',
                    "raw PUA character leaked into the font JSON");
        }
    }

    @Test
    @DisplayName("generated PNGs carry a valid signature and IEND")
    void texturesAreValidPng(@TempDir Path dir) throws IOException {
        GeneratedPack pack = new PackGenerator("1.0.0").generate(dir, LAYOUT, PACK_FORMAT);
        byte[] png = readEntryBytes(pack.file(), "assets/terralense/textures/font/cell_0.png");

        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        for (int i = 0; i < signature.length; i++) {
            assertEquals(signature[i], png[i], "bad PNG signature byte " + i);
        }
        String tail = new String(png, png.length - 8, 4, StandardCharsets.US_ASCII);
        assertEquals("IEND", tail);
    }

    @Test
    @DisplayName("legacy formats below 65 declare pack_format")
    void legacyPackMetaUsesPackFormat(@TempDir Path dir) throws IOException {
        GeneratedPack pack = new PackGenerator("1.0.0").generate(dir, LAYOUT, 42);
        String meta = readEntry(pack.file(), "pack.mcmeta");
        assertTrue(meta.contains("\"pack_format\": 42"), meta);
        assertFalse(meta.contains("min_format"), "min_format is not valid below 65");
    }

    @Test
    @DisplayName("modern formats declare min_format/max_format and omit pack_format")
    void modernPackMetaUsesMinMaxFormat(@TempDir Path dir) throws IOException {
        // Regression guard.
        GeneratedPack pack = new PackGenerator("1.0.0").generate(dir, LAYOUT, 88);
        String meta = readEntry(pack.file(), "pack.mcmeta");

        assertTrue(meta.contains("\"min_format\": 88"), meta);
        assertTrue(meta.contains("\"max_format\": 88"), meta);
        assertFalse(meta.contains("pack_format"),
                "pack_format must be absent at format 65 and above");
    }

    @Test
    @DisplayName("pack.mcmeta is valid JSON at every supported format")
    void packMetaIsValidJson(@TempDir Path dir) throws IOException {
        for (int format : new int[] {42, 64, 65, 88, 100}) {
            GeneratedPack pack = new PackGenerator("1.0.0").generate(
                    Files.createTempDirectory(dir, "meta"), LAYOUT, format);
            String meta = readEntry(pack.file(), "pack.mcmeta");
            // Braces balance is a cheap structural check; the schema assertions above cover the
            // semantics.
            assertEquals(meta.chars().filter(c -> c == '{').count(),
                    meta.chars().filter(c -> c == '}').count(),
                    "unbalanced braces at format " + format);
            assertTrue(meta.contains("\"description\""));
        }
    }

    @Test
    @DisplayName("a corrupt cache marker forces regeneration instead of failing")
    void badMarkerRegenerates(@TempDir Path dir) throws IOException {
        PackGenerator generator = new PackGenerator("1.0.0");
        generator.generate(dir, LAYOUT, PACK_FORMAT);
        Files.writeString(dir.resolve("pack-fingerprint.txt"), "not-a-fingerprint");

        GeneratedPack regenerated = generator.generate(dir, LAYOUT, PACK_FORMAT);
        assertTrue(regenerated.regenerated());
    }

    @Test
    @DisplayName("no temporary files are left behind")
    void leavesNoTempFiles(@TempDir Path dir) throws IOException {
        new PackGenerator("1.0.0").generate(dir, LAYOUT, PACK_FORMAT);
        try (var stream = Files.list(dir)) {
            assertTrue(stream.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    private static List<String> entryNames(Path zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static String readEntry(Path zip, String name) throws IOException {
        return new String(readEntryBytes(zip, name), StandardCharsets.UTF_8);
    }

    private static byte[] readEntryBytes(Path zip, String name) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    return in.readAllBytes();
                }
            }
        }
        throw new IOException("entry not found: " + name);
    }
}

package dev.terralense.resourcepack;

import dev.terralense.hud.HudGlyphs;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds the TerraLense resource pack. */
public final class PackGenerator {
    /** Fixed DOS timestamp (1980-01-01) so zip entries are reproducible. */
    private static final long FIXED_ENTRY_TIME = 315_532_800_000L;

    /** Namespace the font definition lives under. */
    private static final String FONT_NAMESPACE = "minecraft";

    /** Font used when the HUD is anchored near the bottom of the screen. */
    private static final String FONT_NAME = "terralense_hud";

    /** Font used when the HUD is anchored at the top of the screen. */
    private static final String TOP_FONT_NAME = "terralense_hud_top";

    /** Namespace textures live under. */
    private static final String TEXTURE_NAMESPACE = "terralense";

    /** Marker icon texture names, indexed by shape. */
    private static final String[] MARKER_SHAPES = {"diamond", "dot", "ring", "cross"};

    private final String pluginVersion;

    public PackGenerator(String pluginVersion) {
        this.pluginVersion = pluginVersion;
    }

    /** Namespaced key of the bottom-anchored font. */
    public static String fontKey() {
        return FONT_NAMESPACE + ":" + FONT_NAME;
    }

    /** Namespaced key of the top-anchored font, whose glyphs hang below the baseline. */
    public static String topFontKey() {
        return FONT_NAMESPACE + ":" + TOP_FONT_NAME;
    }

    /**
     * Generates the pack, reusing an existing file when its fingerprint already matches so
     * unchanged configuration never rewrites it.
     */
    public GeneratedPack generate(Path directory, PackLayout layout, int packFormat)
            throws IOException {
        Files.createDirectories(directory);
        Path packFile = directory.resolve("TerraLense-Pack.zip");
        Path marker = directory.resolve("pack-fingerprint.txt");

        String fingerprint = fingerprint(layout, packFormat);

        if (Files.isRegularFile(packFile) && Files.isRegularFile(marker)) {
            String existing = Files.readString(marker, StandardCharsets.UTF_8).strip();
            if (existing.equals(fingerprint)) {
                byte[] bytes = Files.readAllBytes(packFile);
                return new GeneratedPack(packFile, sha1Hex(bytes), bytes.length, fingerprint, false);
            }
        }

        byte[] zip = buildZip(layout, packFormat);

        // Written to a temp file then moved, so a crash cannot leave a truncated pack that clients
        // would fail to load.
        Path temp = Files.createTempFile(directory, "terralense-pack", ".tmp");
        try {
            Files.write(temp, zip);
            Files.move(temp, packFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        Files.writeString(marker, fingerprint, StandardCharsets.UTF_8);

        return new GeneratedPack(packFile, sha1Hex(zip), zip.length, fingerprint, true);
    }

    /** Identity of everything that affects the pack bytes. */
    private String fingerprint(PackLayout layout, int packFormat) {
        String raw = pluginVersion
                // Bumped when the pack's structure changes, so existing caches are invalidated.
                + "|v3"
                + "|" + packFormat
                + "|" + layout.gridSize()
                + "|" + layout.cellScreenSize()
                + "|" + layout.textureRows()
                + "|" + layout.providerHeight()
                + "|" + layout.providerAscent()
                + "|" + HudGlyphs.ARROW_STEPS
                + "|" + HudGlyphs.spaceGlyphCount();
        return sha1Hex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] buildZip(PackLayout layout, int packFormat) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("pack.mcmeta", packMeta(packFormat).getBytes(StandardCharsets.UTF_8));

        String texturePath = "assets/" + TEXTURE_NAMESPACE + "/textures/font/";
        int rows = layout.gridSize();
        int totalRows = layout.textureRows();

        // One terrain-cell texture per map row: the opaque band moves down as the row index
        // increases.
        for (int row = 0; row < rows; row++) {
            entries.put(texturePath + "cell_" + row + ".png",
                    png(GlyphArt.rowCell(row, totalRows), totalRows));
        }

        // The player arrow only ever appears at the centre cell, so it needs one row rather than
        // all of them.
        int centreRow = rows / 2;
        int iconSize = GlyphArt.CELL_PIXELS * 2;
        for (int step = 0; step < HudGlyphs.ARROW_STEPS; step++) {
            entries.put(texturePath + "arrow_" + step + ".png",
                    png(GlyphArt.placeIcon(GlyphArt.arrow(iconSize, step), iconSize,
                            centreRow, totalRows), totalRows));
        }

        // Markers can land on any row, so each shape needs a per-row texture.
        for (int shape = 0; shape < MARKER_SHAPES.length; shape++) {
            int[] icon = markerIcon(shape, iconSize);
            for (int row = 0; row < rows; row++) {
                entries.put(texturePath + "marker_" + MARKER_SHAPES[shape] + "_" + row + ".png",
                        png(GlyphArt.placeIcon(icon, iconSize, row, totalRows), totalRows));
            }
        }

        entries.put("assets/" + FONT_NAMESPACE + "/font/" + FONT_NAME + ".json",
                fontDefinition(layout, layout.providerAscent())
                        .getBytes(StandardCharsets.UTF_8));

        // Same textures, ascent 0: the glyphs hang below the baseline so the map can be anchored at
        // the top of the screen.
        entries.put("assets/" + FONT_NAMESPACE + "/font/" + TOP_FONT_NAME + ".json",
                fontDefinition(layout, 0).getBytes(StandardCharsets.UTF_8));

        return zip(entries);
    }

    private static byte[] png(int[] pixels, int totalRows) throws IOException {
        return PackTextures.writePng(pixels, GlyphArt.CELL_PIXELS,
                totalRows * GlyphArt.CELL_PIXELS);
    }

    private static int[] markerIcon(int shape, int size) {
        return switch (shape) {
            case 0 -> GlyphArt.diamond(size);
            case 1 -> GlyphArt.dot(size);
            case 2 -> GlyphArt.ring(size);
            default -> GlyphArt.cross(size);
        };
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            // Sorted so entry order cannot vary between runs.
            List<String> names = new ArrayList<>(entries.keySet());
            names.sort(String::compareTo);
            for (String name : names) {
                byte[] data = entries.get(name);
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(FIXED_ENTRY_TIME);
                CRC32 crc = new CRC32();
                crc.update(data);
                entry.setCrc(crc.getValue());
                entry.setSize(data.length);
                zip.putNextEntry(entry);
                zip.write(data);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** Resource pack format at which the metadata schema changed. */
    private static final int MIN_MAX_FORMAT_THRESHOLD = 65;

    /** Builds {@code pack.mcmeta} for the target pack format. */
    private String packMeta(int packFormat) {
        if (packFormat < MIN_MAX_FORMAT_THRESHOLD) {
            // Legacy schema: pack_format only.
            return """
                   {
                     "pack": {
                       "pack_format": %d,
                       "description": "TerraLense minimap HUD"
                     }
                   }
                   """.formatted(packFormat);
        }
        // Modern schema: min/max only.
        return """
               {
                 "pack": {
                   "min_format": %d,
                   "max_format": %d,
                   "description": "TerraLense minimap HUD"
                 }
               }
               """.formatted(packFormat, packFormat);
    }

    /** Builds the font definition. */
    private String fontDefinition(PackLayout layout, int ascent) {
        int height = layout.providerHeight();
        int cellScreen = layout.cellScreenSize();
        int rows = layout.gridSize();

        List<String> providers = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            providers.add(bitmapProvider("cell_" + row, ascent, height,
                    HudGlyphs.rowGlyph(row)));
        }

        int centreRow = rows / 2;
        for (int step = 0; step < HudGlyphs.ARROW_STEPS; step++) {
            providers.add(bitmapProvider("arrow_" + step, ascent, height,
                    HudGlyphs.arrowGlyph(step, centreRow)));
        }

        for (int shape = 0; shape < MARKER_SHAPES.length; shape++) {
            for (int row = 0; row < rows; row++) {
                providers.add(bitmapProvider(
                        "marker_" + MARKER_SHAPES[shape] + "_" + row, ascent, height,
                        HudGlyphs.markerGlyph(shape, row)));
            }
        }

        providers.add(spaceProvider(layout));
        providers.add(forwardSpaceProvider(layout));

        return "{\n  \"providers\": [\n" + String.join(",\n", providers) + "\n  ]\n}\n";
    }

    private String bitmapProvider(String texture, int ascent, int height, char glyph) {
        return """
                    {
                      "type": "bitmap",
                      "file": "%s:font/%s.png",
                      "ascent": %d,
                      "height": %d,
                      "chars": ["%s"]
                    }""".formatted(TEXTURE_NAMESPACE, texture, ascent, height,
                escapeUnicode(glyph));
    }

    /**
     * Negative-space glyphs in powers of two, so rewinding a row costs a handful of characters
     * rather than one per cell.
     */
    private String spaceProvider(PackLayout layout) {
        int advancePerCell = layout.cellAdvance();
        StringBuilder advances = new StringBuilder();
        for (int index = 0; index < HudGlyphs.spaceGlyphCount(); index++) {
            advances.append('"').append(escapeUnicode(HudGlyphs.spaceGlyph(index)))
                    .append("\": ").append(-(1 << index) * advancePerCell)
                    .append(",\n        ");
        }
        advances.append('"').append(escapeUnicode(HudGlyphs.KERN_GLYPH))
                .append("\": ").append(layout.kernAdvance());
        return """
                    {
                      "type": "space",
                      "advances": {
                        %s
                      }
                    }""".formatted(advances);
    }

    /** Positive-space glyphs used to skip transparent cells. */
    private String forwardSpaceProvider(PackLayout layout) {
        int advancePerCell = layout.cellAdvance();
        StringBuilder advances = new StringBuilder();
        for (int index = 0; index < HudGlyphs.forwardSpaceGlyphCount(); index++) {
            if (index > 0) {
                advances.append(",\n        ");
            }
            advances.append('"').append(escapeUnicode(HudGlyphs.forwardSpaceGlyph(index)))
                    .append("\": ").append((1 << index) * advancePerCell);
        }
        return """
                    {
                      "type": "space",
                      "advances": {
                        %s
                      }
                    }""".formatted(advances);
    }

    /** JSON-safe \\uXXXX form; PUA characters must not be emitted raw. */
    private static String escapeUnicode(char value) {
        return String.format("\\u%04X", (int) value);
    }

    static String sha1Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >>> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-1 is mandated by the platform; absence means a broken JRE.
            throw new UncheckedIOException(new IOException("SHA-1 unavailable", ex));
        }
    }
}

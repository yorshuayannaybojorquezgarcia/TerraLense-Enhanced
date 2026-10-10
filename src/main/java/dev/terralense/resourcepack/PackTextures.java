package dev.terralense.resourcepack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Minimal deterministic PNG writer for the generated font textures. */
final class PackTextures {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
    };

    private PackTextures() {
    }

    /**
     * Encodes an ARGB pixel array as a PNG.
     *
     * @param argb row-major pixels, length {@code width * height}
     * @param width image width
     * @param height image height
     */
    static byte[] writePng(int[] argb, int width, int height) throws IOException {
        if (argb.length != width * height) {
            throw new IllegalArgumentException("pixel array does not match dimensions");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(PNG_SIGNATURE);

        // IHDR: 8-bit RGBA, no interlacing.
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        writeInt(ihdr, width);
        writeInt(ihdr, height);
        ihdr.write(8);
        ihdr.write(6);
        ihdr.write(0);
        ihdr.write(0);
        ihdr.write(0);
        writeChunk(out, "IHDR", ihdr.toByteArray());

        writeChunk(out, "IDAT", deflate(rawScanlines(argb, width, height)));
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    /** Builds raw scanlines with a zero (None) filter byte per row. */
    private static byte[] rawScanlines(int[] argb, int width, int height) {
        byte[] raw = new byte[height * (1 + width * 4)];
        int cursor = 0;
        for (int y = 0; y < height; y++) {
            raw[cursor++] = 0;
            int rowStart = y * width;
            for (int x = 0; x < width; x++) {
                int pixel = argb[rowStart + x];
                raw[cursor++] = (byte) ((pixel >>> 16) & 0xFF);
                raw[cursor++] = (byte) ((pixel >>> 8) & 0xFF);
                raw[cursor++] = (byte) (pixel & 0xFF);
                raw[cursor++] = (byte) ((pixel >>> 24) & 0xFF);
            }
        }
        return raw;
    }

    private static byte[] deflate(byte[] data) {
        // Fixed level and no dictionary so output is reproducible.
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, false);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int written = deflater.deflate(buffer);
                out.write(buffer, 0, written);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data)
            throws IOException {
        writeInt(out, data.length);
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}

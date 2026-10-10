package dev.terralense.map;

/** A square ARGB pixel buffer produced by the renderer. */
public final class MapImage {
    private final int size;
    private final int[] pixels;

    public MapImage(int size) {
        if (size <= 0 || size > 512) {
            throw new IllegalArgumentException("image size out of range: " + size);
        }
        this.size = size;
        this.pixels = new int[size * size];
    }

    public int size() {
        return size;
    }

    /** Packed 0xAARRGGBB value at a pixel. */
    public int get(int x, int y) {
        return pixels[index(x, y)];
    }

    public void set(int x, int y, int argb) {
        pixels[index(x, y)] = argb;
    }

    /** True when the pixel is fully transparent. */
    public boolean isTransparent(int x, int y) {
        return ((pixels[index(x, y)] >>> 24) & 0xFF) == 0;
    }

    /** Direct view of the buffer, row-major. */
    public int[] rawPixels() {
        return pixels;
    }

    private int index(int x, int y) {
        if (x < 0 || y < 0 || x >= size || y >= size) {
            throw new IndexOutOfBoundsException("(" + x + "," + y + ") outside " + size + "x" + size);
        }
        return y * size + x;
    }
}

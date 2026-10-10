package dev.terralense.map;

import dev.terralense.terrain.TerrainCategory;

/** A square grid of sampled terrain cells centred on the player. */
public final class TerrainGrid {
    private final int size;
    private final TerrainCategory[] categories;
    private final int[] heights;

    public TerrainGrid(int size) {
        if (size <= 0 || size > 512) {
            throw new IllegalArgumentException("grid size out of range: " + size);
        }
        this.size = size;
        this.categories = new TerrainCategory[size * size];
        this.heights = new int[size * size];
        java.util.Arrays.fill(this.categories, TerrainCategory.UNKNOWN);
    }

    public int size() {
        return size;
    }

    public void set(int x, int y, TerrainCategory category, int height) {
        int index = index(x, y);
        categories[index] = category == null ? TerrainCategory.UNKNOWN : category;
        heights[index] = height;
    }

    public TerrainCategory categoryAt(int x, int y) {
        if (!contains(x, y)) {
            return TerrainCategory.UNKNOWN;
        }
        return categories[index(x, y)];
    }

    public int heightAt(int x, int y) {
        return contains(x, y) ? heights[index(x, y)] : 0;
    }

    public boolean contains(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size;
    }

    private int index(int x, int y) {
        if (!contains(x, y)) {
            throw new IndexOutOfBoundsException("(" + x + "," + y + ") outside " + size + "x" + size);
        }
        return y * size + x;
    }
}

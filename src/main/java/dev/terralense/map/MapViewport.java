package dev.terralense.map;

/** Converts between world block coordinates and minimap cell coordinates. */
public record MapViewport(int centerBlockX, int centerBlockZ, int gridSize, int blocksPerCell) {
    public MapViewport {
        if (gridSize <= 0 || gridSize > 512) {
            throw new IllegalArgumentException("gridSize out of range: " + gridSize);
        }
        if (blocksPerCell <= 0 || blocksPerCell > 64) {
            throw new IllegalArgumentException("blocksPerCell out of range: " + blocksPerCell);
        }
    }

    /** Width of the covered area, in blocks. */
    public int spanBlocks() {
        return gridSize * blocksPerCell;
    }

    /** Westmost block covered. */
    public int minBlockX() {
        return centerBlockX - spanBlocks() / 2;
    }

    /** Northmost block covered. */
    public int minBlockZ() {
        return centerBlockZ - spanBlocks() / 2;
    }

    public int maxBlockX() {
        return minBlockX() + spanBlocks() - 1;
    }

    public int maxBlockZ() {
        return minBlockZ() + spanBlocks() - 1;
    }

    /** World X of the western edge of a map cell. */
    public int blockXForCell(int cellX) {
        return minBlockX() + cellX * blocksPerCell;
    }

    /** World Z of the northern edge of a map cell. */
    public int blockZForCell(int cellZ) {
        return minBlockZ() + cellZ * blocksPerCell;
    }

    /** Cell X containing a world X, using floor division so negative coordinates map correctly. */
    public int cellXForBlock(int blockX) {
        return Math.floorDiv(blockX - minBlockX(), blocksPerCell);
    }

    public int cellZForBlock(int blockZ) {
        return Math.floorDiv(blockZ - minBlockZ(), blocksPerCell);
    }

    public boolean containsCell(int cellX, int cellZ) {
        return cellX >= 0 && cellZ >= 0 && cellX < gridSize && cellZ < gridSize;
    }

    public boolean containsBlock(int blockX, int blockZ) {
        return blockX >= minBlockX() && blockX <= maxBlockX()
                && blockZ >= minBlockZ() && blockZ <= maxBlockZ();
    }

    /** Lowest chunk X overlapping the viewport. */
    public int minChunkX() {
        return minBlockX() >> 4;
    }

    public int maxChunkX() {
        return maxBlockX() >> 4;
    }

    public int minChunkZ() {
        return minBlockZ() >> 4;
    }

    public int maxChunkZ() {
        return maxBlockZ() >> 4;
    }

    /** Number of chunks that must be available to fill the viewport. */
    public int chunkCount() {
        return (maxChunkX() - minChunkX() + 1) * (maxChunkZ() - minChunkZ() + 1);
    }

    /** Whether a re-sample is needed after the centre moved. */
    public boolean requiresResample(int newCenterBlockX, int newCenterBlockZ) {
        return cellXForBlock(newCenterBlockX) != cellXForBlock(centerBlockX)
                || cellZForBlock(newCenterBlockZ) != cellZForBlock(centerBlockZ);
    }

    /** A copy centred on a new position. */
    public MapViewport centeredOn(int blockX, int blockZ) {
        return new MapViewport(blockX, blockZ, gridSize, blocksPerCell);
    }

    /** A copy at a different zoom level. */
    public MapViewport withZoom(int newBlocksPerCell) {
        return new MapViewport(centerBlockX, centerBlockZ, gridSize, newBlocksPerCell);
    }

    /**
     * Distance in cells from the viewport centre, used to decide whether a marker is close enough
     * to draw.
     */
    public double cellDistanceFromCenter(int blockX, int blockZ) {
        double dx = (blockX - centerBlockX) / (double) blocksPerCell;
        double dz = (blockZ - centerBlockZ) / (double) blocksPerCell;
        return Math.sqrt(dx * dx + dz * dz);
    }
}

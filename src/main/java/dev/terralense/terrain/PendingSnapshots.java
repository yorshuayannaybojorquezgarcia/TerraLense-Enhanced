package dev.terralense.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChunkSnapshot;

/** Chunk snapshots captured on the server thread, awaiting async sampling. */
public final class PendingSnapshots {
    private final UUID worldId;
    private final int minHeight;
    private final int maxHeight;
    private final List<Entry> entries = new ArrayList<>();

    public PendingSnapshots(UUID worldId, int minHeight, int maxHeight) {
        this.worldId = worldId;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }

    void add(ChunkKey key, ChunkSnapshot snapshot) {
        entries.add(new Entry(key, snapshot));
    }

    public UUID worldId() {
        return worldId;
    }

    public int minHeight() {
        return minHeight;
    }

    public int maxHeight() {
        return maxHeight;
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** One captured chunk. */
    public record Entry(ChunkKey key, ChunkSnapshot snapshot) {
    }
}

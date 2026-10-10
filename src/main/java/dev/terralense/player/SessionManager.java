package dev.terralense.player;

import dev.terralense.config.TerraLenseConfig;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/** Tracks a {@link MinimapSession} per online player. */
public final class SessionManager {
    /** Rotation smoothing factor. */
    private static final double ROTATION_SMOOTHING = 0.35D;

    private final Map<UUID, MinimapSession> sessions = new ConcurrentHashMap<>();
    private final Server server;

    public SessionManager(Server server) {
        this.server = server;
    }

    /** Creates a session for a joining player. */
    public MinimapSession create(UUID playerId, PlayerPreferences preferences) {
        MinimapSession session = new MinimapSession(playerId, preferences, ROTATION_SMOOTHING);
        sessions.put(playerId, session);
        return session;
    }

    public MinimapSession session(UUID playerId) {
        return sessions.get(playerId);
    }

    /** Removes a session, returning it so preferences can be flushed. */
    public MinimapSession remove(UUID playerId) {
        return sessions.remove(playerId);
    }

    public boolean has(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public int activeCount() {
        return sessions.size();
    }

    public void forEach(Consumer<MinimapSession> action) {
        sessions.values().forEach(action);
    }

    public Collection<MinimapSession> all() {
        return sessions.values();
    }

    /** Online players, from the server. */
    public Collection<? extends Player> onlinePlayers() {
        return server.getOnlinePlayers();
    }

    public Player playerOf(UUID playerId) {
        return server.getPlayer(playerId);
    }

    /** Re-clamps every session's preferences after a config reload. */
    public void reapply(TerraLenseConfig config) {
        sessions.values().forEach(session -> {
            session.preferences().clampTo(config);
            session.markHudDirty();
        });
    }

    public void clear() {
        sessions.clear();
    }
}

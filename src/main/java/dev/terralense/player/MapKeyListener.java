package dev.terralense.player;

import dev.terralense.config.TerraLenseConfig;
import dev.terralense.core.MinimapService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** Opens the large map from a keypress instead of a command. */
public final class MapKeyListener implements Listener {
    /** Maximum gap between presses for them to count as a double press. */
    private static final long DOUBLE_PRESS_WINDOW_MILLIS = 400L;

    /** Minimum gap between two map opens, to bound render cost. */
    private static final long COOLDOWN_MILLIS = 1_000L;

    private final MinimapService minimaps;
    private final Supplier<TerraLenseConfig> config;

    private final Map<UUID, Long> lastPress = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastOpen = new ConcurrentHashMap<>();

    public MapKeyListener(MinimapService minimaps, Supplier<TerraLenseConfig> config) {
        this.minimaps = minimaps;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        TerraLenseConfig current = config.get();
        if (!current.mapKeyEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("terralense.use")) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long previous = lastPress.put(playerId, now);

        boolean doublePressed = previous != null
                && now - previous <= DOUBLE_PRESS_WINDOW_MILLIS;
        if (!doublePressed) {
            // Single press: leave the vanilla swap alone.
            return;
        }
        // Consume the gesture so a third press starts a fresh pair.
        lastPress.remove(playerId);

        Long opened = lastOpen.get(playerId);
        if (opened != null && now - opened < COOLDOWN_MILLIS) {
            // Still cancel, so a rapid double press does not swap hands either.
            event.setCancelled(true);
            return;
        }
        lastOpen.put(playerId, now);

        // Suppress the hand swap only for the press that opened the map.
        event.setCancelled(true);
        minimaps.requestFullscreen(player, current.fullscreenZoom());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Bounded by online players rather than growing over uptime.
        UUID playerId = event.getPlayer().getUniqueId();
        lastPress.remove(playerId);
        lastOpen.remove(playerId);
    }
}

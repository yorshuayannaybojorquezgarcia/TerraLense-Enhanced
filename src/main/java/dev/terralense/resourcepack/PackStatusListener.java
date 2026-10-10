package dev.terralense.resourcepack;

import dev.terralense.config.TerraLenseConfig;
import dev.terralense.core.Log;
import dev.terralense.core.Messages;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Tells a player once if the HUD cannot render because they declined or failed to load the resource
 * pack.
 */
public final class PackStatusListener implements Listener {
    private final Supplier<TerraLenseConfig> config;
    private final ResourcePackService packService;
    private final Log log;

    /** Players already told this session, so the notice is not repeated. */
    private final Set<UUID> notified = ConcurrentHashMap.newKeySet();

    public PackStatusListener(Supplier<TerraLenseConfig> config,
                              ResourcePackService packService,
                              Log log) {
        this.config = config;
        this.packService = packService;
        this.log = log;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();

        switch (status) {
            case SUCCESSFULLY_LOADED -> {
                // A successful load clears the flag so a later failure can notify again, e.g. after
                // the pack is regenerated.
                notified.remove(player.getUniqueId());
                log.debug(() -> player.getName() + " loaded the TerraLense pack");
            }
            case DECLINED -> notifyOnce(player,
                    "You declined the resource pack, so the minimap is off. "
                            + "Rejoin and accept it to enable the minimap.");
            case FAILED_DOWNLOAD -> notifyOnce(player,
                    "The minimap resource pack could not be downloaded, so the minimap "
                            + "is off. Let an administrator know.");
            case FAILED_RELOAD, DISCARDED, INVALID_URL -> notifyOnce(player,
                    "The minimap resource pack could not be applied, so the minimap is off.");
            default -> {
                // ACCEPTED / DOWNLOADED are progress states, not outcomes.
            }
        }
    }

    private void notifyOnce(Player player, String message) {
        // Nothing to explain if TerraLense is not trying to deliver a pack.
        if (!packService.isActive() || !config.get().resourcePackPrompt()) {
            return;
        }
        if (!notified.add(player.getUniqueId())) {
            return;
        }
        log.debug(() -> "notified " + player.getName() + " about the resource pack");
        player.sendMessage(Messages.warn(message));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Bounded by online players rather than growing over uptime.
        notified.remove(event.getPlayer().getUniqueId());
    }
}

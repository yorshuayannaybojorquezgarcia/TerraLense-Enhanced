package dev.terralense.command;

import dev.terralense.core.Messages;
import dev.terralense.hud.HudDelivery;
import dev.terralense.player.MinimapSession;
import dev.terralense.player.SessionManager;
import dev.terralense.storage.PlayerDataStore;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /terralense toggle} - turns a player's minimap on or off. */
public final class ToggleCommand implements SubCommand {
    private final SessionManager sessions;
    private final PlayerDataStore store;
    private final HudDelivery delivery;

    public ToggleCommand(SessionManager sessions, PlayerDataStore store, HudDelivery delivery) {
        this.sessions = sessions;
        this.store = store;
        this.delivery = delivery;
    }

    @Override
    public String name() {
        return "toggle";
    }

    @Override
    public String permission() {
        return "terralense.toggle";
    }

    @Override
    public String usage() {
        return "toggle";
    }

    @Override
    public String description() {
        return "Show or hide your minimap.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        MinimapSession session = sessions.session(player.getUniqueId());
        if (session == null) {
            sender.sendMessage(Messages.error("Your minimap session is not ready yet."));
            return;
        }
        boolean enabled = !session.preferences().minimapEnabled();
        session.preferences().setMinimapEnabled(enabled);
        session.markTerrainDirty();
        store.savePreferences(player.getUniqueId(), session.preferences());

        if (enabled) {
            sender.sendMessage(Messages.success("Minimap enabled."));
        } else {
            // Clear both surfaces immediately rather than leaving a stale frame or an orphaned boss
            // bar on screen.
            delivery.hide(player);
            sender.sendMessage(Messages.info("Minimap disabled."));
        }
    }
}

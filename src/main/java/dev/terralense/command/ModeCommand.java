package dev.terralense.command;

import dev.terralense.config.MapOrientation;
import dev.terralense.core.Messages;
import dev.terralense.player.MinimapSession;
import dev.terralense.player.SessionManager;
import dev.terralense.storage.PlayerDataStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /terralense mode <north|player>}. */
public final class ModeCommand implements SubCommand {
    private final SessionManager sessions;
    private final PlayerDataStore store;

    public ModeCommand(SessionManager sessions, PlayerDataStore store) {
        this.sessions = sessions;
        this.store = store;
    }

    @Override
    public String name() {
        return "mode";
    }

    @Override
    public String permission() {
        return "terralense.use";
    }

    @Override
    public String usage() {
        return "mode <north|player>";
    }

    @Override
    public String description() {
        return "Choose whether north or your facing direction stays up.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        MinimapSession session = sessions.session(player.getUniqueId());
        if (session == null) {
            sender.sendMessage(Messages.error("Your minimap session is not ready yet."));
            return;
        }

        MapOrientation orientation;
        if (args.length < 1) {
            // No argument: flip, which is the common intent.
            orientation = session.preferences().orientation().opposite();
        } else {
            Optional<MapOrientation> parsed = MapOrientation.fromConfig(args[0]);
            if (parsed.isEmpty()) {
                sender.sendMessage(Messages.error("Unknown mode '" + args[0]
                        + "'. Use 'north' or 'player'."));
                return;
            }
            orientation = parsed.get();
        }

        session.preferences().setOrientation(orientation);
        // Orientation changes the transform, not the terrain data.
        session.markHudDirty();
        session.rotationEngine().reset();
        store.savePreferences(player.getUniqueId(), session.preferences());

        sender.sendMessage(Messages.success(orientation == MapOrientation.PLAYER_UP
                ? "Map now rotates with you."
                : "Map now keeps north at the top."));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        for (MapOrientation orientation : MapOrientation.values()) {
            if (orientation.configName().startsWith(args[0].toLowerCase(Locale.ROOT))) {
                options.add(orientation.configName());
            }
        }
        return options;
    }
}

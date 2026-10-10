package dev.terralense.command;

import dev.terralense.core.Messages;
import dev.terralense.marker.DeathMarkerProvider;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /terralense death [clear]}. */
public final class DeathCommand implements SubCommand {
    private final DeathMarkerProvider deaths;

    public DeathCommand(DeathMarkerProvider deaths) {
        this.deaths = deaths;
    }

    @Override
    public String name() {
        return "death";
    }

    @Override
    public String permission() {
        return "terralense.use";
    }

    @Override
    public String usage() {
        return "death [clear]";
    }

    @Override
    public String description() {
        return "Show or clear your last death location.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
            if (deaths.clear(player.getUniqueId())) {
                sender.sendMessage(Messages.success("Death marker cleared."));
            } else {
                sender.sendMessage(Messages.info("You have no death marker."));
            }
            return;
        }

        deaths.current(player.getUniqueId()).ifPresentOrElse(location -> {
            org.bukkit.World world = player.getServer().getWorld(location.worldId());
            String worldName = world == null ? "an unloaded world" : world.getName();
            sender.sendMessage(Messages.detail("Last death",
                    location.x() + ", " + location.y() + ", " + location.z() + " in " + worldName));

            if (world != null && world.getUID().equals(player.getWorld().getUID())) {
                int distance = (int) Math.round(Math.hypot(
                        location.x() - player.getLocation().getBlockX(),
                        location.z() - player.getLocation().getBlockZ()));
                sender.sendMessage(Messages.detail("Distance", distance + " blocks away"));
            }
        }, () -> sender.sendMessage(Messages.info("You have no recorded death location.")));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1 && "clear".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("clear");
        }
        return List.of();
    }
}

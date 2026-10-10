package dev.terralense.command;

import dev.terralense.core.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Root {@code /terralense} command: dispatch and tab completion only. */
public final class TerraLenseCommand implements CommandExecutor, TabCompleter {
    private final List<SubCommand> subCommands;

    public TerraLenseCommand(List<SubCommand> subCommands) {
        this.subCommands = List.copyOf(subCommands);
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String name = args[0].toLowerCase(Locale.ROOT);
        SubCommand target = find(name);
        if (target == null) {
            sender.sendMessage(Messages.error("Unknown subcommand '" + args[0]
                    + "'. Try /" + label + " help."));
            return true;
        }
        if (!target.permission().isEmpty() && !sender.hasPermission(target.permission())) {
            sender.sendMessage(Messages.error("You do not have permission to do that."));
            return true;
        }
        if (target.playerOnly() && !(sender instanceof org.bukkit.entity.Player)) {
            sender.sendMessage(Messages.error("That command can only be used by a player."));
            return true;
        }

        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        try {
            target.execute(sender, subArgs);
        } catch (RuntimeException ex) {
            // A command must never surface a raw stack trace to a player.
            sender.sendMessage(Messages.error("That command failed. Check the console for details."));
            throw ex;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String label,
                                      String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (SubCommand sub : visibleTo(sender)) {
                if (sub.name().startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    names.add(sub.name());
                }
            }
            names.add("help");
            return names;
        }

        SubCommand target = find(args[0].toLowerCase(Locale.ROOT));
        if (target == null
                || (!target.permission().isEmpty() && !sender.hasPermission(target.permission()))) {
            return List.of();
        }
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        return target.complete(sender, subArgs);
    }

    private SubCommand find(String name) {
        for (SubCommand sub : subCommands) {
            if (sub.name().equals(name) || sub.aliases().contains(name)) {
                return sub;
            }
        }
        return null;
    }

    private List<SubCommand> visibleTo(CommandSender sender) {
        List<SubCommand> visible = new ArrayList<>();
        for (SubCommand sub : subCommands) {
            if (sub.permission().isEmpty() || sender.hasPermission(sub.permission())) {
                visible.add(sub);
            }
        }
        return visible;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Messages.info("Minimap commands:"));
        for (SubCommand sub : visibleTo(sender)) {
            sender.sendMessage(Messages.detailLine("/" + label + " " + sub.usage(),
                    sub.description()));
        }
    }
}

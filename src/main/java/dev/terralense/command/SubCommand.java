package dev.terralense.command;

import java.util.List;
import org.bukkit.command.CommandSender;

/** One {@code /terralense} subcommand. */
public interface SubCommand {
    /** Primary name, lowercase. */
    String name();

    /** Alternative names. */
    default List<String> aliases() {
        return List.of();
    }

    /** Permission required, or empty for none. */
    String permission();

    /** Usage fragment shown in help, e.g. {@code zoom <1-8>}. */
    String usage();

    /** One-line description for help output. */
    String description();

    /** Whether a console sender must be rejected. */
    default boolean playerOnly() {
        return true;
    }

    void execute(CommandSender sender, String[] args);

    /** Tab completions for the given partial arguments. */
    default List<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }
}

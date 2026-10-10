package dev.terralense.command;

import dev.terralense.core.Messages;
import dev.terralense.core.ReloadResult;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;

/** {@code /terralense reload}. */
public final class ReloadCommand implements SubCommand {
    private final Supplier<ReloadResult> reloader;

    public ReloadCommand(Supplier<ReloadResult> reloader) {
        this.reloader = reloader;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permission() {
        return "terralense.reload";
    }

    @Override
    public String usage() {
        return "reload";
    }

    @Override
    public String description() {
        return "Reload configuration without restarting.";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ReloadResult result = reloader.get();

        if (!result.success()) {
            sender.sendMessage(Messages.error("Reload failed: " + result.message()
                    + " The previous configuration is still active."));
            return;
        }
        sender.sendMessage(Messages.success("Configuration reloaded."));

        if (result.packRegenerated()) {
            sender.sendMessage(Messages.info("The resource pack changed and was regenerated. "
                    + "Players need to rejoin, or be re-sent the pack, to see it."));
        }
        for (String note : result.notes()) {
            sender.sendMessage(Messages.warn(note));
        }
    }
}

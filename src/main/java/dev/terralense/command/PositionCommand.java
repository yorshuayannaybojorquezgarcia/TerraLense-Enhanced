package dev.terralense.command;

import dev.terralense.config.TerraLenseConfig;
import dev.terralense.core.Messages;
import dev.terralense.player.MinimapSession;
import dev.terralense.player.SessionManager;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /terralense position <left|right|center|reset|value>} - nudges the map horizontally. */
public final class PositionCommand implements SubCommand {
    /** Cells moved per nudge. */
    private static final int STEP = 2;

    /** Nominal GUI width in GUI pixels for each GUI scale, at a 1920-wide window. */
    private static final Map<Integer, Integer> GUI_WIDTH_BY_SCALE = Map.of(
            1, 1920,
            2, 960,
            3, 640,
            4, 480);

    private final SessionManager sessions;
    private final Supplier<TerraLenseConfig> config;

    public PositionCommand(SessionManager sessions, Supplier<TerraLenseConfig> config) {
        this.sessions = sessions;
        this.config = config;
    }

    @Override
    public String name() {
        return "position";
    }

    @Override
    public List<String> aliases() {
        return List.of("pos", "move");
    }

    @Override
    public String permission() {
        return "terralense.use";
    }

    @Override
    public String usage() {
        return "position <corner|left|right|center>";
    }

    @Override
    public String description() {
        return "Move the minimap left or right on your screen.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        MinimapSession session = sessions.session(player.getUniqueId());
        if (session == null) {
            sender.sendMessage(Messages.error("Your minimap session is not ready yet."));
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(Messages.detail("Map offset",
                    session.preferences().offsetNudge() + " cells"));
            sender.sendMessage(Messages.info("Use position corner <your GUI scale> to snap "
                    + "to the left edge, then left or right to fine-tune."));
            return;
        }

        int current = session.preferences().offsetNudge();
        int updated;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "left", "l" -> updated = current - STEP;
            case "right", "r" -> updated = current + STEP;
            case "center", "centre", "reset", "middle" -> updated = 0;
            case "corner", "topleft", "edge" -> {
                Integer scale = parseGuiScale(args);
                if (scale == null) {
                    sender.sendMessage(Messages.error("Tell me your GUI scale: "
                            + "/terralense position corner <1-4>"));
                    sender.sendMessage(Messages.info("It is in Options, Video Settings, "
                            + "GUI Scale. 'Auto' shows the number in brackets."));
                    return;
                }
                updated = leftEdgeOffset(scale);
                sender.sendMessage(Messages.info("Snapped to the left edge for GUI scale "
                        + scale + ". Fine-tune with /terralense position left or right."));
            }
            default -> {
                try {
                    updated = Integer.parseInt(args[0]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Messages.error("Use corner <1-4>, left, right, "
                            + "center, or a number of cells."));
                    return;
                }
            }
        }

        session.preferences().setOffsetNudge(updated);
        // Only the HUD layout changed, so the terrain does not need re-sampling.
        session.markHudDirty();

        int applied = session.preferences().offsetNudge();
        sender.sendMessage(Messages.success("Map offset: " + applied + " cells."
                + (applied == updated ? "" : " (clamped)")));
    }

    /** Cell offset that puts the map's left edge near the screen's left edge. */
    private int leftEdgeOffset(int guiScale) {
        int guiWidth = GUI_WIDTH_BY_SCALE.get(guiScale);
        int cellPixels = Math.max(2, config.get().hudSize() / config.get().hudGridSize());
        int marginPixels = config.get().hudMargin();
        return -Math.max(0, (guiWidth / 2 - marginPixels) / cellPixels);
    }

    /** Reads a GUI scale argument, or {@code null} when absent or out of range. */
    private static Integer parseGuiScale(String[] args) {
        if (args.length < 2) {
            return null;
        }
        try {
            int scale = Integer.parseInt(args[1]);
            return GUI_WIDTH_BY_SCALE.containsKey(scale) ? scale : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("corner", "left", "right", "center")
                    .stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].toLowerCase(Locale.ROOT).startsWith("corner")) {
            return List.of("1", "2", "3", "4").stream()
                    .filter(option -> option.startsWith(args[1]))
                    .toList();
        }
        return List.of();
    }
}

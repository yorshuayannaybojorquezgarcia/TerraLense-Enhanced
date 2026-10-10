package dev.terralense.hud;

import dev.terralense.config.HudAnchor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** Sends the rendered HUD to a player on the configured UI surface. */
public final class HudDelivery {
    /** Objective name; short and namespaced so it cannot clash with other plugins. */
    private static final String OBJECTIVE_NAME = "terralense";

    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    /** Sends a frame on the given surface. */
    public void send(Player player, Component frame, HudAnchor anchor) {
        switch (anchor) {
            case ACTION_BAR -> {
                // Drop anything left over from a previous configuration.
                removeBar(player);
                removeSidebar(player);
                player.sendActionBar(frame);
            }
            case BOSS_BAR -> {
                removeSidebar(player);
                sendBossBar(player, frame);
            }
            // The sidebar is line-based, so it is driven by sendRows instead.
            case SIDEBAR -> { }
        }
    }

    /** Draws the map into the scoreboard sidebar, one row per line. */
    public void sendRows(Player player, List<Component> rows, Component title) {
        removeBar(player);
        player.sendActionBar(Component.empty());

        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
                id -> player.getServer().getScoreboardManager().getNewScoreboard());

        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.displayName(title);
        }

        // Scores order lines top-down when counted downward, so the first row needs the highest
        // score.
        int score = rows.size();
        for (int i = 0; i < rows.size(); i++) {
            String entry = entryKey(i);
            Team team = board.getTeam(entry);
            if (team == null) {
                team = board.registerNewTeam(entry);
                team.addEntry(entry);
            }
            // The row content lives in the prefix: entry names are identity only and cannot carry
            // per-line components.
            team.prefix(rows.get(i));
            objective.getScore(entry).setScore(score--);
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    /** Unique, invisible entry name for a row. */
    private static String entryKey(int row) {
        return "\u00A7" + Integer.toHexString(row % 16) + "\u00A7r";
    }

    private void removeSidebar(Player player) {
        Scoreboard board = boards.remove(player.getUniqueId());
        if (board == null) {
            return;
        }
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            objective.unregister();
        }
        // Restore the server's main scoreboard so other plugins are not left broken.
        player.setScoreboard(player.getServer().getScoreboardManager().getMainScoreboard());
    }

    private void sendBossBar(Player player, Component frame) {
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            // Progress 0 hides the coloured fill, leaving only the map text.
            bar = BossBar.bossBar(frame, 0.0F, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
            return;
        }
        // Update in place; recreating the bar each frame causes visible flicker.
        bar.name(frame);
    }

    /** Clears the HUD from both surfaces, e.g. when a player disables the minimap. */
    public void hide(Player player) {
        removeBar(player);
        removeSidebar(player);
        player.sendActionBar(Component.empty());
    }

    private void removeBar(Player player) {
        BossBar existing = bars.remove(player.getUniqueId());
        if (existing != null) {
            player.hideBossBar(existing);
        }
    }

    /** Releases a player's bar on quit, so the map cannot grow without bound. */
    public void forget(UUID playerId) {
        bars.remove(playerId);
        boards.remove(playerId);
    }

    /** Hides every bar, on plugin disable. */
    public void clear() {
        bars.clear();
        boards.clear();
    }
}

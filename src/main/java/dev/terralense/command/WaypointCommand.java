package dev.terralense.command;

import dev.terralense.api.marker.MarkerVisibility;
import dev.terralense.compatibility.PlatformScheduler;
import dev.terralense.core.Messages;
import dev.terralense.marker.WaypointMarkerProvider;
import dev.terralense.storage.Waypoint;
import dev.terralense.storage.WaypointService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /terralense waypoint ...} */
public final class WaypointCommand implements SubCommand {
    private final WaypointService waypoints;
    private final WaypointMarkerProvider markers;
    private final PlatformScheduler scheduler;

    public WaypointCommand(WaypointService waypoints,
                           WaypointMarkerProvider markers,
                           PlatformScheduler scheduler) {
        this.waypoints = waypoints;
        this.markers = markers;
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "waypoint";
    }

    @Override
    public List<String> aliases() {
        return List.of("wp");
    }

    @Override
    public String permission() {
        return "terralense.waypoint";
    }

    @Override
    public String usage() {
        return "waypoint <add|remove|list|tp> [name]";
    }

    @Override
    public String description() {
        return "Manage your waypoints.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        if (args.length == 0) {
            sender.sendMessage(Messages.error("Usage: /terralense " + usage()));
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add", "set" -> add(player, args);
            case "remove", "delete", "del" -> remove(player, args);
            case "list" -> list(player);
            case "tp", "teleport" -> teleport(player, args);
            default -> sender.sendMessage(Messages.error("Unknown action '" + args[0]
                    + "'. Use add, remove, list or tp."));
        }
    }

    private void add(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Messages.error("Usage: /terralense waypoint add <name>"));
            return;
        }
        String name = args[1];
        if (!Waypoint.isValidName(name)) {
            player.sendMessage(Messages.error("Waypoint names may use letters, numbers, "
                    + "underscores and hyphens, up to " + Waypoint.MAX_NAME_LENGTH
                    + " characters."));
            return;
        }

        Location location = player.getLocation();
        if (!Waypoint.isValidPosition(location.getBlockX(), location.getBlockY(),
                location.getBlockZ())) {
            player.sendMessage(Messages.error("A waypoint cannot be saved at this position."));
            return;
        }

        Waypoint waypoint = new Waypoint(0L, player.getUniqueId(), name,
                player.getWorld().getUID(), location.getBlockX(), location.getBlockY(),
                location.getBlockZ(), 0x4FD1A5, "diamond", MarkerVisibility.PRIVATE,
                System.currentTimeMillis());

        waypoints.add(waypoint).thenAccept(outcome -> scheduler.runOnServerThread(() -> {
            if (!player.isOnline()) {
                return;
            }
            switch (outcome) {
                case CREATED -> {
                    player.sendMessage(Messages.success("Waypoint '" + name + "' saved."));
                    if (!waypoints.isPersistent()) {
                        player.sendMessage(Messages.warn("Storage is unavailable, so this "
                                + "waypoint will be lost when the server restarts."));
                    }
                }
                case DUPLICATE -> player.sendMessage(
                        Messages.warn("You already have a waypoint called '" + name + "'."));
                case LIMIT_REACHED -> player.sendMessage(Messages.warn("You have reached the "
                        + "limit of " + WaypointService.MAX_PER_PLAYER + " waypoints."));
                case FAILED -> player.sendMessage(
                        Messages.error("Could not save that waypoint."));
            }
        }));
    }

    private void remove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Messages.error("Usage: /terralense waypoint remove <name>"));
            return;
        }
        String name = args[1];
        waypoints.remove(player.getUniqueId(), name)
                .whenComplete((removed, error) -> scheduler.runOnServerThread(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        player.sendMessage(Messages.error("Could not remove that waypoint."));
                    } else if (removed) {
                        player.sendMessage(Messages.success("Waypoint '" + name + "' removed."));
                    } else {
                        player.sendMessage(Messages.warn("You have no waypoint called '"
                                + name + "'."));
                    }
                }));
    }

    private void list(Player player) {
        waypoints.list(player.getUniqueId())
                .whenComplete((found, error) -> scheduler.runOnServerThread(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        player.sendMessage(Messages.error("Could not read your waypoints."));
                        return;
                    }
                    if (found.isEmpty()) {
                        player.sendMessage(Messages.info("You have no waypoints yet. "
                                + "Use /terralense waypoint add <name>."));
                        return;
                    }
                    player.sendMessage(Messages.info("Your waypoints (" + found.size()
                            + "/" + WaypointService.MAX_PER_PLAYER + "):"));
                    for (Waypoint waypoint : found) {
                        player.sendMessage(Messages.detailLine(waypoint.name(),
                                waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z()
                                        + " in " + worldName(player, waypoint.worldId())));
                    }
                }));
    }

    private void teleport(Player player, String[] args) {
        if (!player.hasPermission("terralense.waypoint.teleport")) {
            player.sendMessage(Messages.error("You do not have permission to teleport."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Messages.error("Usage: /terralense waypoint tp <name>"));
            return;
        }
        String name = args[1];

        waypoints.find(player.getUniqueId(), name)
                .whenComplete((found, error) -> scheduler.runOnServerThread(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        player.sendMessage(Messages.error("Could not read that waypoint."));
                        return;
                    }
                    if (found.isEmpty()) {
                        player.sendMessage(Messages.warn("You have no waypoint called '"
                                + name + "'."));
                        return;
                    }
                    performTeleport(player, found.get());
                }));
    }

    private void performTeleport(Player player, Waypoint waypoint) {
        World world = player.getServer().getWorld(waypoint.worldId());
        if (world == null) {
            player.sendMessage(Messages.error("That waypoint's world is not loaded."));
            return;
        }
        // Centre on the block and keep the player's facing, so arriving does not drop them in a
        // corner or spin the camera.
        Location target = new Location(world, waypoint.x() + 0.5D, waypoint.y(),
                waypoint.z() + 0.5D, player.getLocation().getYaw(),
                player.getLocation().getPitch());

        // teleportAsync lets the server load the destination chunk off-thread instead of stalling
        // the tick.
        player.teleportAsync(target).thenAccept(success -> scheduler.runOnServerThread(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (success) {
                player.sendMessage(Messages.success("Teleported to '" + waypoint.name() + "'."));
            } else {
                player.sendMessage(Messages.error("That teleport was blocked."));
            }
        }));
    }

    private String worldName(Player player, UUID worldId) {
        World world = player.getServer().getWorld(worldId);
        return world == null ? "an unloaded world" : world.getName();
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> actions = new ArrayList<>();
            for (String action : List.of("add", "remove", "list", "tp")) {
                if (action.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    actions.add(action);
                }
            }
            return actions;
        }
        if (args.length == 2 && sender instanceof Player player && isNameAction(args[0])) {
            // Completed from the in-memory marker cache: tab completion must never trigger a
            // database query.
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Waypoint waypoint : markers.cached(player.getUniqueId())) {
                if (waypoint.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(waypoint.name());
                }
            }
            return names;
        }
        return List.of();
    }

    private static boolean isNameAction(String action) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "remove", "delete", "del", "tp", "teleport" -> true;
            default -> false;
        };
    }
}

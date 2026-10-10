package dev.terralense.compatibility;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** {@link PlatformScheduler} backed by the standard Bukkit/Paper scheduler. */
public final class BukkitPlatformScheduler implements PlatformScheduler {
    private final Plugin plugin;
    private final Server server;

    public BukkitPlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
    }

    @Override
    public void runOnServerThread(Runnable task) {
        if (isServerThread()) {
            task.run();
            return;
        }
        if (!plugin.isEnabled()) {
            // Scheduling against a disabled plugin throws; during shutdown the work is no longer
            // relevant, so drop it quietly.
            return;
        }
        server.getScheduler().runTask(plugin, task);
    }

    @Override
    public TaskHandle repeatOnServerThread(Runnable task, long initialDelayTicks, long periodTicks) {
        BukkitTask handle = server.getScheduler()
                .runTaskTimer(plugin, task, Math.max(0L, initialDelayTicks), Math.max(1L, periodTicks));
        return handle::cancel;
    }

    @Override
    public boolean isServerThread() {
        return server.isPrimaryThread();
    }

    @Override
    public void shutdown() {
        server.getScheduler().cancelTasks(plugin);
    }
}

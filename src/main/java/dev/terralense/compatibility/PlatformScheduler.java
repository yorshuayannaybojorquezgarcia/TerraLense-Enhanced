package dev.terralense.compatibility;

/**
 * Scheduling abstraction so the rest of the plugin never talks to a concrete scheduler directly.
 */
public interface PlatformScheduler {
    /** Runs a task where it is safe to read global server state. */
    void runOnServerThread(Runnable task);

    /**
     * Runs a repeating task on a fixed tick interval.
     *
     * @return a handle that cancels the task
     */
    TaskHandle repeatOnServerThread(Runnable task, long initialDelayTicks, long periodTicks);

    /** True when the caller is currently on a thread that owns server state. */
    boolean isServerThread();

    /** Cancels everything this scheduler started; called on plugin disable. */
    void shutdown();

    /** Cancellable handle for a scheduled repeating task. */
    interface TaskHandle {
        void cancel();
    }
}

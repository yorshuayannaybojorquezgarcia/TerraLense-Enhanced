package dev.terralense.core;

import java.util.List;

/**
 * Outcome of a configuration reload.
 *
 * @param success whether the new configuration was applied
 * @param message failure detail, or a short summary on success
 * @param packRegenerated whether the resource pack had to be rebuilt
 * @param notes settings that could not be applied without a restart
 */
public record ReloadResult(boolean success,
                           String message,
                           boolean packRegenerated,
                           List<String> notes) {
    public ReloadResult {
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public static ReloadResult failure(String message) {
        return new ReloadResult(false, message, false, List.of());
    }

    public static ReloadResult success(boolean packRegenerated, List<String> notes) {
        return new ReloadResult(true, "configuration applied", packRegenerated, notes);
    }
}

package dev.terralense.config;

import java.util.Locale;
import java.util.Optional;

/** Controls how TerraLense obtains the resource pack that provides the HUD font. */
public enum ResourcePackMode {
    /**
     * Generate the pack and serve it, but stand down automatically if the server is already
     * configured to send its own resource pack.
     */
    AUTO,

    /** Always generate and serve the TerraLense pack. */
    GENERATE,

    /** Do not generate anything. */
    EXTERNAL,

    /** Do not generate or send any pack; the glyph HUD is disabled. */
    DISABLED;

    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ResourcePackMode> fromConfig(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(" ", "");
        return switch (key) {
            case "auto" -> Optional.of(AUTO);
            case "generate", "always", "self" -> Optional.of(GENERATE);
            case "external", "manual" -> Optional.of(EXTERNAL);
            case "disabled", "none", "off", "false" -> Optional.of(DISABLED);
            default -> Optional.empty();
        };
    }
}

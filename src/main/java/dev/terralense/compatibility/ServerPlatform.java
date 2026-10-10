package dev.terralense.compatibility;

/** Detects the server flavour TerraLense is running on. */
public enum ServerPlatform {
    PAPER("Paper"),
    FOLIA("Folia");

    private final String displayName;

    ServerPlatform(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isFolia() {
        return this == FOLIA;
    }

    /** Detects the platform. */
    public static ServerPlatform detect() {
        return classPresent("io.papermc.paper.threadedregions.RegionizedServer")
                ? FOLIA
                : PAPER;
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}

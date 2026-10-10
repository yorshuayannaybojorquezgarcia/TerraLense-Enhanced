package dev.terralense.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checks SpigotMC for a newer release and logs a one-line notice.
 *
 * <p>Runs off the main thread and never blocks startup. Any failure is a debug message: a plugin
 * should not spam the console because a website was briefly unreachable.
 */
public final class UpdateChecker {
    /** Spigot resource id for TerraLense. */
    public static final int RESOURCE_ID = 138_116;

    public static final String RESOURCE_URL =
            "https://www.spigotmc.org/resources/terralense." + RESOURCE_ID + "/";

    private static final String API_URL =
            "https://api.spiget.org/v2/resources/" + RESOURCE_ID + "/versions/latest";

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    /** A version string longer than this is not a version string. */
    private static final int MAX_RESPONSE_BYTES = 128;

    private final String currentVersion;
    private final Log log;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile String latestVersion;
    private volatile boolean updateAvailable;

    public UpdateChecker(String currentVersion, Log log) {
        this.currentVersion = currentVersion;
        this.log = log;
    }

    /**
     * Runs the check on a short-lived daemon thread.
     *
     * <p>A bare thread rather than an executor: an {@code ExecutorService} would have to be closed,
     * and closing it blocks until the request finishes, which is exactly what must not happen on
     * startup.
     */
    public void checkAsync() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(this::check, "TerraLense-update-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void check() {
        try {
            String latest = fetchLatestVersion();
            if (latest == null || latest.isBlank()) {
                log.debug("update check: no version returned");
                return;
            }
            this.latestVersion = latest;
            int comparison = compareVersions(latest, currentVersion);
            this.updateAvailable = comparison > 0;

            if (updateAvailable) {
                log.info("An update is available: " + latest + " (running " + currentVersion + ").");
                log.info("Download: " + RESOURCE_URL);
            } else if (comparison < 0) {
                log.debug("running " + currentVersion + ", newer than the published " + latest);
            } else {
                log.debug("up to date (" + currentVersion + ")");
            }
        } catch (IOException | RuntimeException ex) {
            // A failed check is not worth a warning; the plugin works either way.
            log.debug(() -> "update check failed: " + ex.getMessage());
        } finally {
            running.set(false);
        }
    }

    private String fetchLatestVersion() throws IOException {
        URL url = URI.create(API_URL).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "TerraLense/" + currentVersion);
        connection.setRequestProperty("Accept", "application/json");

        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                log.debug("update check: HTTP " + connection.getResponseCode());
                return null;
            }
            String body;
            try (InputStream in = connection.getInputStream()) {
                body = new String(in.readNBytes(4096), StandardCharsets.UTF_8);
            }
            return parseName(body);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Pulls the {@code name} field out of the version JSON.
     *
     * <p>Hand-parsed to avoid depending on a JSON library for one field. The response is a flat
     * object, so a substring scan is enough.
     */
    static String parseName(String json) {
        if (json == null) {
            return null;
        }
        int key = json.indexOf("\"name\"");
        if (key < 0) {
            return null;
        }
        int colon = json.indexOf(':', key + 6);
        if (colon < 0) {
            return null;
        }
        int open = json.indexOf('"', colon + 1);
        if (open < 0) {
            return null;
        }
        int close = json.indexOf('"', open + 1);
        if (close < 0 || close - open > MAX_RESPONSE_BYTES) {
            return null;
        }
        String value = json.substring(open + 1, close).trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Compares two dotted version strings numerically.
     *
     * <p>Non-numeric trailers such as {@code 1.2.0-beta} compare on the numeric part only, so a
     * pre-release of the running version is not reported as an update.
     *
     * @return positive when {@code left} is newer, negative when older, zero when equal
     */
    static int compareVersions(String left, String right) {
        String[] a = normalise(left).split("\\.");
        String[] b = normalise(right).split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = numberAt(a, i);
            int y = numberAt(b, i);
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static String normalise(String version) {
        if (version == null) {
            return "";
        }
        String trimmed = version.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        int cut = trimmed.length();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c != '.' && (c < '0' || c > '9')) {
                cut = i;
                break;
            }
        }
        return trimmed.substring(0, cut);
    }

    private static int numberAt(String[] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** True once a check has found a newer published version. */
    public boolean updateAvailable() {
        return updateAvailable;
    }

    /** The newest published version, or {@code null} if no check has succeeded. */
    public String latestVersion() {
        return latestVersion;
    }

    public String currentVersion() {
        return currentVersion;
    }
}

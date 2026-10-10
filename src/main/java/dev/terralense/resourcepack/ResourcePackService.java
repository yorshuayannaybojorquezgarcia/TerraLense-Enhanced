package dev.terralense.resourcepack;

import dev.terralense.config.ResourcePackMode;
import dev.terralense.config.TerraLenseConfig;
import dev.terralense.core.Log;
import dev.terralense.core.Messages;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.UUID;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Generates the HUD pack and offers it to players. */
public final class ResourcePackService {
    /** Stable pack id. */
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes(
            "terralense-hud".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private final PackGenerator generator;
    private final Log log;
    private final Path dataDirectory;

    private volatile TerraLenseConfig config;
    private volatile GeneratedPack pack;
    private volatile String packUrl;
    private volatile boolean active;
    private volatile String inactiveReason = "not initialised";
    private PackHttpServer httpServer;

    public ResourcePackService(PackGenerator generator,
                               Log log,
                               Path dataDirectory,
                               TerraLenseConfig config) {
        this.generator = generator;
        this.log = log;
        this.dataDirectory = dataDirectory;
        this.config = config;
    }

    /**
     * Prepares the pack according to the configured mode.
     *
     * @param settings delivery settings read from configuration
     * @param serverHasOwnPack whether the server already sends a pack
     * @param packFormat pack format for the running client version
     */
    public void initialise(DeliverySettings settings, boolean serverHasOwnPack, int packFormat) {
        ResourcePackMode mode = config.resourcePackMode();

        if (mode == ResourcePackMode.DISABLED) {
            deactivate("resource-pack.mode is 'disabled'");
            return;
        }
        if (mode == ResourcePackMode.EXTERNAL) {
            deactivate("resource-pack.mode is 'external'; assets are expected in your own pack");
            log.info("Resource pack mode: external. TerraLense will not generate or send a pack.");
            return;
        }
        if (mode == ResourcePackMode.AUTO && serverHasOwnPack) {
            // Standing down rather than stacking on top of an operator's pack.
            deactivate("the server already sends its own resource pack");
            log.warn("A server resource pack is already configured, so TerraLense will not send "
                    + "its own. Merge the generated pack into yours, then set "
                    + "resource-pack.mode to 'external'.");
            generateOnly(packFormat);
            return;
        }

        if (!generateOnly(packFormat)) {
            return;
        }

        String resolvedUrl = resolveUrl(settings);
        if (resolvedUrl == null) {
            return;
        }

        this.packUrl = resolvedUrl;
        this.active = true;
        this.inactiveReason = null;
        log.info("Resource pack ready: " + pack.sizeBytes() / 1024 + "KB, sha1 "
                + pack.sha1().substring(0, 8) + ", "
                + (pack.regenerated() ? "regenerated" : "cached") + ".");
        log.info("Pack URL: " + resolvedUrl);
    }

    /**
     * Determines the download URL, or {@code null} if the pack cannot be delivered (in which case
     * the reason has already been recorded).
     */
    private String resolveUrl(DeliverySettings settings) {
        String explicit = settings.explicitUrl() == null ? "" : settings.explicitUrl().trim();
        if (!explicit.isEmpty()) {
            if (!isValidPackUrl(explicit)) {
                deactivate("resource-pack.url is not a valid http(s) URL");
                log.error("resource-pack.url is not a valid http/https URL; the HUD is disabled.");
                return null;
            }
            // An explicit URL means the operator is hosting it themselves, so the built-in server
            // is not needed.
            stopHttpServer();
            return explicit;
        }

        if (!settings.selfHostEnabled()) {
            deactivate("no resource-pack.url is set and self-hosting is disabled");
            log.warn("The HUD pack was generated at " + pack.file().toAbsolutePath()
                    + " but there is no way to deliver it. Either enable "
                    + "resource-pack.self-host.enabled or host the file and set "
                    + "resource-pack.url.");
            return null;
        }

        if (!startHttpServer(settings)) {
            deactivate("the built-in pack HTTP server could not start");
            return null;
        }

        String host = resolvePublicHost(settings);
        if (host == null) {
            deactivate("could not determine a public host for the pack URL");
            log.error("Self-hosting is enabled but the public address could not be determined. "
                    + "Set resource-pack.self-host.public-address to your server's public "
                    + "hostname or IP.");
            return null;
        }
        return httpServer.urlFor(host);
    }

    private boolean startHttpServer(DeliverySettings settings) {
        if (httpServer == null) {
            httpServer = new PackHttpServer(log);
        }
        if (httpServer.isRunning()) {
            // Already up from a previous initialise; just point it at the current file in case the
            // pack was regenerated.
            httpServer.updateFile(pack.file());
            return true;
        }
        return httpServer.start(settings.bindAddress(), settings.port(), pack.file());
    }

    private void stopHttpServer() {
        if (httpServer != null) {
            httpServer.close();
            httpServer = null;
        }
    }

    /** Works out the hostname clients should connect to. */
    private String resolvePublicHost(DeliverySettings settings) {
        String configured = settings.publicAddress() == null ? "" : settings.publicAddress().trim();
        if (!configured.isEmpty()) {
            return configured;
        }
        String serverIp = settings.serverIp() == null ? "" : settings.serverIp().trim();
        if (!serverIp.isEmpty()) {
            return serverIp;
        }
        // No hint available: localhost is right for single-machine testing and is clearly wrong for
        // production, which the warning makes explicit.
        log.warn("resource-pack.self-host.public-address is not set, so 'localhost' will be "
                + "used. That only works if players connect from the same machine. Set it to "
                + "your server's public hostname or IP before letting players in.");
        return "localhost";
    }

    /**
     * Pack delivery settings.
     *
     * @param explicitUrl operator-hosted URL; wins over self-hosting
     * @param selfHostEnabled whether to run the built-in HTTP server
     * @param bindAddress address the built-in server binds to
     * @param port port the built-in server listens on
     * @param publicAddress hostname clients should use
     * @param serverIp the server's own configured IP, used as a fallback
     */
    public record DeliverySettings(String explicitUrl,
                                   boolean selfHostEnabled,
                                   String bindAddress,
                                   int port,
                                   String publicAddress,
                                   String serverIp) {
    }

    private boolean generateOnly(int packFormat) {
        try {
            PackLayout layout = PackLayout.of(config.hudGridSize(),
                    config.hudSize(), config.hudMargin(), config.hudPosition());
            this.pack = generator.generate(dataDirectory.resolve("pack"), layout, packFormat);
            return true;
        } catch (IOException | RuntimeException ex) {
            // A pack failure disables the HUD but must never stop the plugin.
            deactivate("pack generation failed: " + ex.getMessage());
            log.error("Could not generate the HUD resource pack; the minimap will be disabled.", ex);
            return false;
        }
    }

    private void deactivate(String reason) {
        this.active = false;
        this.inactiveReason = reason;
    }

    /** Validates a pack URL. */
    static boolean isValidPackUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    /** Offers the pack to a player. */
    public void send(Player player) {
        if (!active || pack == null || packUrl == null) {
            return;
        }
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(
                    PACK_ID, URI.create(packUrl), pack.sha1());
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    // Additive: never discards a pack the server already sent.
                    .replace(false)
                    // Optional: a declined pack must not kick the player.
                    .required(false)
                    .prompt(prompt())
                    .build());
        } catch (RuntimeException ex) {
            log.debug(() -> "could not send the resource pack to " + player.getName()
                    + ": " + ex.getMessage());
        }
    }

    private Component prompt() {
        return Component.text()
                .append(Component.text("TerraLense", Messages.PRIMARY))
                .append(Component.text(" needs this pack to draw the minimap.", Messages.BODY))
                .build();
    }

    public boolean isActive() {
        return active;
    }

    /** Why the HUD is inactive, or {@code null} when it is working. */
    public String inactiveReason() {
        return inactiveReason;
    }

    public GeneratedPack pack() {
        return pack;
    }

    /** Whether a config change requires regenerating the pack. */
    public boolean requiresRegeneration(TerraLenseConfig updated) {
        return pack == null
                || !config.packRelevantFingerprint().equals(updated.packRelevantFingerprint());
    }

    public void applyConfig(TerraLenseConfig updated) {
        this.config = updated;
    }

    /** The built-in HTTP server, or {@code null} when self-hosting is not in use. */
    public PackHttpServer httpServer() {
        return httpServer;
    }

    /** The URL clients are told to download from, or {@code null} when inactive. */
    public String packUrl() {
        return packUrl;
    }

    /** Stops the built-in HTTP server. */
    public void shutdown() {
        stopHttpServer();
    }
}

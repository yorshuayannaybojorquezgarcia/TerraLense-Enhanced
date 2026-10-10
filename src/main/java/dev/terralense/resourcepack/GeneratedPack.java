package dev.terralense.resourcepack;

import java.nio.file.Path;

/**
 * Result of a pack generation attempt.
 *
 * @param file location of the written zip
 * @param sha1 hex SHA-1 of the zip, as required when sending a pack
 * @param sizeBytes size of the zip on disk
 * @param fingerprint deterministic identity of the inputs that produced it
 * @param regenerated false when an identical cached pack was reused
 */
public record GeneratedPack(Path file,
                            String sha1,
                            long sizeBytes,
                            String fingerprint,
                            boolean regenerated) {
}

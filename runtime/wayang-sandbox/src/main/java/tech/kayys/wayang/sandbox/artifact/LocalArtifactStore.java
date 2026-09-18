package tech.kayys.wayang.sandbox.artifact;

import tech.kayys.wayang.spi.sandbox.artifact.ArtifactDescriptor;
import tech.kayys.wayang.spi.sandbox.artifact.ArtifactDownloadResult;
import tech.kayys.wayang.spi.sandbox.artifact.ArtifactStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LocalArtifactStore implements ArtifactStore {

    private final Path storeDirectory;
    private final ConcurrentMap<String, ArtifactDescriptor> metadataMap = new ConcurrentHashMap<>();

    public LocalArtifactStore(Path storeDirectory) {
        this.storeDirectory = Objects.requireNonNull(storeDirectory, "storeDirectory must not be null");
    }

    @Override
    public void put(ArtifactDescriptor descriptor, InputStream content) throws Exception {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(content, "content must not be null");

        Files.createDirectories(storeDirectory);
        Path artifactFile = storeDirectory.resolve(descriptor.artifactId());

        MessageDigest digest = ArtifactDigest.sha256();
        long bytesWritten = 0;

        try (content; OutputStream out = Files.newOutputStream(
                artifactFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = content.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                bytesWritten++;
            }
        }

        String calculatedSha = ArtifactDigest.toHex(digest.digest());
        if (!calculatedSha.equalsIgnoreCase(descriptor.sha256())) {
            Files.deleteIfExists(artifactFile);
            throw new IllegalStateException("Artifact digest mismatch. Expected: "
                    + descriptor.sha256() + ", calculated: " + calculatedSha);
        }

        metadataMap.put(descriptor.artifactId(), descriptor);
    }

    @Override
    public ArtifactDownloadResult get(String artifactId) throws Exception {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        ArtifactDescriptor descriptor = metadataMap.get(artifactId);
        if (descriptor == null) {
            throw new IllegalArgumentException("Artifact not found in store: " + artifactId);
        }

        Path artifactFile = storeDirectory.resolve(artifactId);
        if (!Files.exists(artifactFile)) {
            throw new IllegalStateException("Artifact file missing on disk: " + artifactId);
        }

        return new ArtifactDownloadResult(descriptor, Files.newInputStream(artifactFile));
    }

    @Override
    public Optional<ArtifactDescriptor> metadata(String artifactId) {
        if (artifactId == null || artifactId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(metadataMap.get(artifactId));
    }

    @Override
    public void delete(String artifactId) throws Exception {
        if (artifactId == null || artifactId.isBlank()) {
            return;
        }
        metadataMap.remove(artifactId);
        Path artifactFile = storeDirectory.resolve(artifactId);
        Files.deleteIfExists(artifactFile);
    }
}

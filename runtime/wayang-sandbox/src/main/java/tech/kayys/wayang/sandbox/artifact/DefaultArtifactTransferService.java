package tech.kayys.wayang.sandbox.artifact;

import tech.kayys.wayang.spi.sandbox.FilesystemAccess;
import tech.kayys.wayang.spi.sandbox.ResolvedSandboxPath;
import tech.kayys.wayang.spi.sandbox.Sandbox;
import tech.kayys.wayang.spi.sandbox.SandboxManager;
import tech.kayys.wayang.spi.sandbox.artifact.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultArtifactTransferService implements ArtifactTransferService {

    private final SandboxManager sandboxManager;
    private final ConcurrentMap<String, ArtifactDescriptor> artifacts = new ConcurrentHashMap<>();

    public DefaultArtifactTransferService(SandboxManager sandboxManager) {
        this.sandboxManager = Objects.requireNonNull(sandboxManager, "sandboxManager must not be null");
    }

    @Override
    public CompletionStage<ArtifactDescriptor> upload(ArtifactUploadRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        return CompletableFuture.supplyAsync(() -> {
            Sandbox sandbox = sandboxManager.find(request.sandboxId())
                    .orElseThrow(() -> new IllegalArgumentException("Sandbox not found: " + request.sandboxId()));

            String rootId = locationToRootId(request.location());
            ResolvedSandboxPath targetPath = sandbox.filesystem().resolve(rootId, request.relativePath());

            if (!targetPath.access().writable()) {
                throw new SecurityException("Root " + rootId + " does not allow write access");
            }

            Path physicalPath = targetPath.path();
            if (physicalPath.getParent() != null) {
                try {
                    Files.createDirectories(physicalPath.getParent());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create parent directories", e);
                }
            }

            MessageDigest digest = ArtifactDigest.sha256();
            long totalBytes = 0;

            try (InputStream in = request.content();
                 OutputStream out = Files.newOutputStream(
                         physicalPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    totalBytes += read;
                    if (request.maxBytes() > 0 && totalBytes > request.maxBytes()) {
                        throw new IllegalStateException("Artifact size exceeds limit: " + request.maxBytes() + " bytes");
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            } catch (Exception e) {
                try {
                    Files.deleteIfExists(physicalPath);
                } catch (Exception ignored) {
                }
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException("Failed to stream artifact upload", e);
            }

            String sha256 = ArtifactDigest.toHex(digest.digest());

            ArtifactDescriptor descriptor = new ArtifactDescriptor(
                    request.artifactId(),
                    request.executionId(),
                    request.tenantId(),
                    request.sandboxId(),
                    request.name(),
                    request.mediaType() != null ? request.mediaType() : "application/octet-stream",
                    totalBytes,
                    sha256,
                    Instant.now(),
                    request.attributes()
            );

            artifacts.put(artifactKey(request.sandboxId(), request.artifactId()), descriptor);
            return descriptor;
        });
    }

    @Override
    public CompletionStage<ArtifactDownloadResult> download(ArtifactDownloadRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        return CompletableFuture.supplyAsync(() -> {
            ArtifactDescriptor descriptor = find(request.sandboxId(), request.artifactId())
                    .orElseThrow(() -> new IllegalArgumentException("Artifact descriptor not found: " + request.artifactId()));

            Sandbox sandbox = sandboxManager.find(request.sandboxId())
                    .orElseThrow(() -> new IllegalArgumentException("Sandbox not found: " + request.sandboxId()));

            // Find file in workspace or output
            ResolvedSandboxPath targetPath;
            try {
                targetPath = sandbox.filesystem().resolve("output", Path.of(descriptor.name()));
            } catch (Exception e) {
                targetPath = sandbox.filesystem().resolve("workspace", Path.of(descriptor.name()));
            }

            if (!Files.exists(targetPath.path())) {
                targetPath = sandbox.filesystem().resolve("workspace", Path.of(descriptor.name()));
            }

            if (!Files.exists(targetPath.path())) {
                throw new IllegalStateException("Artifact file not found on disk: " + descriptor.name());
            }

            try {
                InputStream in = Files.newInputStream(targetPath.path());
                return new ArtifactDownloadResult(descriptor, in);
            } catch (Exception e) {
                throw new RuntimeException("Failed to open artifact for download", e);
            }
        });
    }

    @Override
    public CompletionStage<Void> delete(String sandboxId, String artifactId) {
        return CompletableFuture.runAsync(() -> {
            ArtifactDescriptor descriptor = artifacts.remove(artifactKey(sandboxId, artifactId));
            if (descriptor != null) {
                sandboxManager.find(sandboxId).ifPresent(sb -> {
                    try {
                        ResolvedSandboxPath resolved = sb.filesystem().resolve("workspace", Path.of(descriptor.name()));
                        Files.deleteIfExists(resolved.path());
                    } catch (Exception ignored) {
                    }
                });
            }
        });
    }

    @Override
    public Optional<ArtifactDescriptor> find(String sandboxId, String artifactId) {
        if (sandboxId == null || artifactId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(artifacts.get(artifactKey(sandboxId, artifactId)));
    }

    private static String artifactKey(String sandboxId, String artifactId) {
        return sandboxId + ":" + artifactId;
    }

    private static String locationToRootId(ArtifactLocation location) {
        return switch (location) {
            case INPUT -> "input";
            case OUTPUT -> "output";
            case WORKSPACE -> "workspace";
        };
    }
}

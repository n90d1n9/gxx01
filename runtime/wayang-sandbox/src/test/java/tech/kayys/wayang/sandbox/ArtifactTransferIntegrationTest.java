package tech.kayys.wayang.sandbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.wayang.sandbox.artifact.ArtifactDigest;
import tech.kayys.wayang.sandbox.artifact.DefaultArtifactTransferService;
import tech.kayys.wayang.sandbox.process.ProcessSandboxProvider;
import tech.kayys.wayang.sandbox.runtime.DefaultSandboxManager;
import tech.kayys.wayang.sandbox.runtime.DefaultSandboxProviderRegistry;
import tech.kayys.wayang.spi.sandbox.*;
import tech.kayys.wayang.spi.sandbox.artifact.ArtifactDescriptor;
import tech.kayys.wayang.spi.sandbox.artifact.ArtifactDownloadRequest;
import tech.kayys.wayang.spi.sandbox.artifact.ArtifactDownloadResult;
import tech.kayys.wayang.spi.sandbox.artifact.ArtifactLocation;
import tech.kayys.wayang.spi.sandbox.artifact.ArtifactUploadRequest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactTransferIntegrationTest {

    @TempDir
    Path tempDir;

    private DefaultSandboxManager sandboxManager;
    private DefaultArtifactTransferService transferService;
    private Sandbox sandbox;

    @BeforeEach
    void setUp() throws Exception {
        DefaultSandboxProviderRegistry registry = new DefaultSandboxProviderRegistry();
        registry.register(new ProcessSandboxProvider(tempDir));
        sandboxManager = new DefaultSandboxManager(registry);
        transferService = new DefaultArtifactTransferService(sandboxManager);

        SandboxRequest request = new SandboxRequest(
                "sb-art-1",
                SandboxType.PROCESS,
                Set.of(),
                SandboxLimits.unlimited(),
                SandboxFilesystem.empty(),
                SandboxNetwork.disabled(),
                Map.of(),
                Map.of("executionId", "exec-art-1", "tenantId", "tenant-1")
        );

        sandbox = sandboxManager.create(request);
        sandboxManager.start("sb-art-1");
    }

    @AfterEach
    void tearDown() throws Exception {
        sandboxManager.destroy("sb-art-1");
    }

    @Test
    void testUploadAndDownloadArtifact() throws Exception {
        byte[] content = "Hello Secure Sandbox Transfer!".getBytes(StandardCharsets.UTF_8);

        ArtifactUploadRequest uploadReq = new ArtifactUploadRequest(
                "sb-art-1",
                "exec-art-1",
                "tenant-1",
                "art-1",
                "greeting.txt",
                "text/plain",
                ArtifactLocation.WORKSPACE,
                Path.of("greeting.txt"),
                new ByteArrayInputStream(content),
                -1L,
                Map.of()
        );

        ArtifactDescriptor descriptor = transferService.upload(uploadReq).toCompletableFuture().get();
        assertNotNull(descriptor);
        assertEquals("art-1", descriptor.artifactId());
        assertEquals(content.length, descriptor.size());
        assertNotNull(descriptor.sha256());

        // Download artifact
        ArtifactDownloadRequest downloadReq = new ArtifactDownloadRequest(
                "sb-art-1",
                "exec-art-1",
                "tenant-1",
                "art-1",
                -1L
        );

        ArtifactDownloadResult downloadRes = transferService.download(downloadReq).toCompletableFuture().get();
        assertEquals("art-1", downloadRes.descriptor().artifactId());
        byte[] downloadedBytes = downloadRes.content().readAllBytes();
        assertArrayEquals(content, downloadedBytes);

        // Delete artifact
        transferService.delete("sb-art-1", "art-1").toCompletableFuture().get();
        assertTrue(transferService.find("sb-art-1", "art-1").isEmpty());
    }

    @Test
    void testUploadExceedingSizeLimitRejected() {
        byte[] content = new byte[10_000];

        ArtifactUploadRequest uploadReq = new ArtifactUploadRequest(
                "sb-art-1",
                "exec-art-1",
                "tenant-1",
                "art-too-large",
                "big.dat",
                "application/octet-stream",
                ArtifactLocation.WORKSPACE,
                Path.of("big.dat"),
                new ByteArrayInputStream(content),
                1000L, // Max 1000 bytes
                Map.of()
        );

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> transferService.upload(uploadReq).toCompletableFuture().get());
        assertTrue(ex.getCause().getMessage().contains("exceeds limit"));
    }

    @Test
    void testUploadPathTraversalRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new ArtifactUploadRequest(
                        "sb-art-1",
                        "exec-art-1",
                        "tenant-1",
                        "art-traversal",
                        "escape.txt",
                        "text/plain",
                        ArtifactLocation.WORKSPACE,
                        Path.of("/escape.txt"), // absolute path forbidden
                        new ByteArrayInputStream(new byte[10]),
                        -1L,
                        Map.of()
                ));
    }
}

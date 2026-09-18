package tech.kayys.wayang.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.wayang.sandbox.filesystem.DefaultSandboxFilesystem;
import tech.kayys.wayang.sandbox.filesystem.FilesystemAccessException;
import tech.kayys.wayang.sandbox.policy.SandboxPolicyEvaluator;
import tech.kayys.wayang.sandbox.policy.SandboxProfileMerger;
import tech.kayys.wayang.sandbox.policy.TenantSandboxPolicy;
import tech.kayys.wayang.spi.sandbox.*;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SandboxSecurityBoundaryTest {

    @TempDir
    Path tempDir;

    @Test
    void testFilesystemPathTraversalPrevention() {
        Path workspace = tempDir.resolve("ws");
        FilesystemRoot root = new FilesystemRoot("workspace", workspace, FilesystemAccess.READ_WRITE, false);
        FilesystemSandboxPolicy policy = FilesystemSandboxPolicy.strict(List.of(root));
        DefaultSandboxFilesystem fs = new DefaultSandboxFilesystem(policy);

        // Disallow absolute path
        assertThrows(FilesystemAccessException.class, () ->
                fs.resolve("workspace", Path.of("/etc/passwd")));

        // Disallow parent traversal
        assertThrows(FilesystemAccessException.class, () ->
                fs.resolve("workspace", Path.of("../secret.txt")));

        // Disallow deeply nested parent traversal
        assertThrows(FilesystemAccessException.class, () ->
                fs.resolve("workspace", Path.of("sub/../../secret.txt")));

        // Allow legitimate relative path
        ResolvedSandboxPath legitimate = fs.resolve("workspace", Path.of("safe/file.txt"));
        assertEquals(workspace.resolve("safe/file.txt").normalize(), legitimate.path().normalize());
    }

    @Test
    void testDenyOverridesAllowInPolicyEvaluation() {
        SandboxLimits platformLimits = new SandboxLimits(2000L, 1024L * 1024L * 1024L, -1L, 10L, Duration.ofMinutes(5), 10_000L, 100L);
        TenantSandboxPolicy tenantPolicy = new TenantSandboxPolicy("tenant-strict", platformLimits, Set.of(NetworkMode.DISABLED), 10);

        // Context requesting NetworkMode FULL (disallowed by tenant policy)
        ExecutionIsolationProfile requestedProfile = new ExecutionIsolationProfile(
                IsolationLevel.NONE,
                Set.of(),
                SandboxType.PROCESS,
                SandboxLimits.unlimited(),
                SandboxFilesystem.empty(),
                SandboxNetwork.full(),
                Map.of(),
                Map.of()
        );

        SandboxPolicyContext context = new SandboxPolicyContext(
                "exec-sec-1",
                "tenant-strict",
                "user-1",
                "agent-1",
                "corr-1",
                requestedProfile,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                Instant.now(),
                Map.of()
        );

        SandboxPolicyDecision decision = SandboxPolicyEvaluator.evaluate(List.of(tenantPolicy), context);
        assertTrue(decision instanceof SandboxPolicyDecision.Deny);
        SandboxPolicyDecision.Deny deny = (SandboxPolicyDecision.Deny) decision;
        assertTrue(deny.reason().contains("Network mode FULL is not permitted"));
    }

    @Test
    void testProfileMergerNeverWidensRestrictions() {
        ExecutionIsolationProfile base = new ExecutionIsolationProfile(
                IsolationLevel.PROCESS,
                Set.of(IsolationFeature.PROCESS_ISOLATION),
                SandboxType.PROCESS,
                new SandboxLimits(1000L, 2048L, -1L, 5L, Duration.ofSeconds(60), 1000L, 50L),
                SandboxFilesystem.empty(),
                SandboxNetwork.restricted(List.of(new NetworkRule(NetworkProtocol.TCP, "api.example.com", 443))),
                Map.of("ENV_A", "1"),
                Map.of()
        );

        ExecutionIsolationProfile overlay = new ExecutionIsolationProfile(
                IsolationLevel.STRONG,
                Set.of(IsolationFeature.TEMPORARY_WORKSPACE),
                SandboxType.CONTAINER,
                new SandboxLimits(500L, 4096L, -1L, 10L, Duration.ofSeconds(30), 2000L, 100L),
                SandboxFilesystem.empty(),
                SandboxNetwork.full(),
                Map.of("ENV_B", "2"),
                Map.of()
        );

        ExecutionIsolationProfile merged = SandboxProfileMerger.merge(base, overlay);

        // Highest isolation level
        assertEquals(IsolationLevel.STRONG, merged.level());
        // More isolated container type
        assertEquals(SandboxType.CONTAINER, merged.preferredSandboxType());
        // Minimum CPU limit (500 < 1000)
        assertEquals(500L, merged.limits().cpuMillis());
        // Minimum memory limit (2048 < 4096)
        assertEquals(2048L, merged.limits().memoryBytes());
        // Minimum timeout (30s < 60s)
        assertEquals(Duration.ofSeconds(30), merged.limits().executionTimeout());
        // Network mode: restricted & full -> restricted (never widened to full!)
        assertEquals(NetworkMode.RESTRICTED, merged.network().mode());
        // Union of features
        assertTrue(merged.requires(IsolationFeature.PROCESS_ISOLATION));
        assertTrue(merged.requires(IsolationFeature.TEMPORARY_WORKSPACE));
    }
}

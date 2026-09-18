package tech.kayys.wayang.sandbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.wayang.operator.runtime.DefaultOperatorServiceRegistry;
import tech.kayys.wayang.operator.runtime.sandbox.DefaultSandboxOperatorService;
import tech.kayys.wayang.sandbox.process.ProcessSandboxProvider;
import tech.kayys.wayang.sandbox.runtime.DefaultSandboxManager;
import tech.kayys.wayang.sandbox.runtime.DefaultSandboxProviderRegistry;
import tech.kayys.wayang.sandbox.runtime.observability.DefaultSandboxDiagnostics;
import tech.kayys.wayang.sandbox.runtime.observability.DefaultSandboxObservabilityManager;
import tech.kayys.wayang.spi.operator.OperatorContext;
import tech.kayys.wayang.spi.operator.OperatorResult;
import tech.kayys.wayang.spi.sandbox.*;
import tech.kayys.wayang.spi.sandbox.observability.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SandboxObservabilityAndOperatorTest {

    @TempDir
    Path tempDir;

    private DefaultSandboxManager sandboxManager;
    private DefaultSandboxObservabilityManager obsManager;
    private DefaultSandboxDiagnostics diagnostics;
    private DefaultSandboxOperatorService operatorService;

    @BeforeEach
    void setUp() throws Exception {
        DefaultSandboxProviderRegistry registry = new DefaultSandboxProviderRegistry();
        registry.register(new ProcessSandboxProvider(tempDir));
        sandboxManager = new DefaultSandboxManager(registry);
        obsManager = new DefaultSandboxObservabilityManager(null, sandboxManager);
        diagnostics = new DefaultSandboxDiagnostics(sandboxManager);
        operatorService = new DefaultSandboxOperatorService(sandboxManager);

        SandboxRequest request = new SandboxRequest(
                "sb-obs-1",
                SandboxType.PROCESS,
                Set.of(),
                SandboxLimits.unlimited(),
                SandboxFilesystem.empty(),
                SandboxNetwork.disabled(),
                Map.of(),
                Map.of("executionId", "exec-obs-1", "tenantId", "tenant-1")
        );
        sandboxManager.create(request);
        sandboxManager.start("sb-obs-1");
    }

    @AfterEach
    void tearDown() throws Exception {
        for (Sandbox s : sandboxManager.list()) {
            try {
                sandboxManager.destroy(s.context().sandboxId());
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void testObservabilityRecordingAndHealth() {
        SandboxObservation obs = new SandboxObservation(
                "obs-1",
                SandboxObservationType.STARTED,
                Instant.now(),
                "sb-obs-1",
                "exec-obs-1",
                "tenant-1",
                "agent-1",
                "corr-1",
                "process",
                Map.of()
        );
        obsManager.record(obs);
        assertEquals(1, obsManager.getObservations().size());

        SandboxHealth health = obsManager.health("sb-obs-1").orElseThrow();
        assertEquals(SandboxHealthStatus.HEALTHY, health.status());
    }

    @Test
    void testDiagnosticsInspection() throws Exception {
        SandboxDiagnosticsSnapshot snapshot = diagnostics.inspect("sb-obs-1");
        assertNotNull(snapshot);
        assertEquals("sb-obs-1", snapshot.sandboxId());
        assertEquals(SandboxState.RUNNING, snapshot.state());
        assertEquals(SandboxHealthStatus.HEALTHY, snapshot.health());
        assertTrue(snapshot.supportedFeatures().contains(SandboxObservabilityFeature.LIFECYCLE_EVENTS));
    }

    @Test
    void testOperatorServiceControlPlane() {
        DefaultOperatorServiceRegistry registry = new DefaultOperatorServiceRegistry();
        registry.register(operatorService);
        assertTrue(registry.find(DefaultSandboxOperatorService.ID).isPresent());

        OperatorContext ctx = new OperatorContext("tenant-1", "admin", "corr-1", "req-1", Map.of());

        // List
        OperatorResult<List<Sandbox>> listResult = operatorService.list(ctx);
        assertTrue(listResult instanceof OperatorResult.Success);
        assertEquals(1, ((OperatorResult.Success<List<Sandbox>>) listResult).value().size());

        // Inspect
        OperatorResult<Sandbox> inspectResult = operatorService.inspect(ctx, "sb-obs-1");
        assertTrue(inspectResult instanceof OperatorResult.Success);
        assertEquals("sb-obs-1", ((OperatorResult.Success<Sandbox>) inspectResult).value().context().sandboxId());

        // Destroy via operator
        OperatorResult<Void> destroyResult = operatorService.destroy(ctx, "sb-obs-1");
        assertTrue(destroyResult instanceof OperatorResult.Success);
        assertFalse(sandboxManager.find("sb-obs-1").isPresent());
    }
}

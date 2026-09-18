package tech.kayys.wayang.sandbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.wayang.sandbox.process.DefaultProcessSandbox;
import tech.kayys.wayang.sandbox.process.ProcessSandboxProvider;
import tech.kayys.wayang.sandbox.runtime.DefaultSandboxManager;
import tech.kayys.wayang.sandbox.runtime.DefaultSandboxProviderRegistry;
import tech.kayys.wayang.spi.sandbox.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class SandboxLifecycleIntegrationTest {

    @TempDir
    Path tempDir;

    private DefaultSandboxProviderRegistry providerRegistry;
    private DefaultSandboxManager sandboxManager;

    @BeforeEach
    void setUp() {
        providerRegistry = new DefaultSandboxProviderRegistry();
        providerRegistry.register(new ProcessSandboxProvider(tempDir));
        sandboxManager = new DefaultSandboxManager(providerRegistry);
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
    void testCompleteLifecycleAndExecution() throws Exception {
        SandboxRequest request = new SandboxRequest(
                "sb-lifecycle-1",
                SandboxType.PROCESS,
                Set.of("process-isolation"),
                SandboxLimits.unlimited(),
                SandboxFilesystem.empty(),
                SandboxNetwork.disabled(),
                Map.of("TEST_ENV", "value123"),
                Map.of("executionId", "exec-lifecycle-1", "tenantId", "tenant-test")
        );

        // 1. Create
        Sandbox sandbox = sandboxManager.create(request);
        assertNotNull(sandbox);
        assertEquals(SandboxState.CREATED, sandbox.state());
        assertTrue(sandboxManager.find("sb-lifecycle-1").isPresent());

        // 2. Start
        sandboxManager.start("sb-lifecycle-1");
        assertEquals(SandboxState.RUNNING, sandbox.state());

        // 3. Execute in RUNNING state
        ProcessSandbox processSandbox = (ProcessSandbox) sandbox;
        ProcessExecutionRequest execReq = ProcessExecutionRequest.of(
                List.of("echo", "wayang-lifecycle-test"),
                Duration.ofSeconds(5)
        );
        ProcessExecutionResult result = processSandbox.execute(execReq).get();
        assertTrue(result.successful());
        assertTrue(result.stdout().contains("wayang-lifecycle-test"));

        // 4. Stop
        sandboxManager.stop("sb-lifecycle-1");
        assertEquals(SandboxState.STOPPED, sandbox.state());

        // Cannot execute in STOPPED state
        ExecutionException ex = assertThrows(ExecutionException.class, () -> processSandbox.execute(execReq).get());
        assertInstanceOf(IllegalStateException.class, ex.getCause());

        // 5. Restart from STOPPED
        sandboxManager.start("sb-lifecycle-1");
        assertEquals(SandboxState.RUNNING, sandbox.state());

        // 6. Destroy
        Path ws = sandbox.context().workspace().orElseThrow();
        assertTrue(Files.exists(ws));

        sandboxManager.destroy("sb-lifecycle-1");
        assertEquals(SandboxState.DESTROYED, sandbox.state());
        assertFalse(sandboxManager.find("sb-lifecycle-1").isPresent());
        assertFalse(Files.exists(ws));

        // Cannot start or execute after DESTROYED
        assertThrows(IllegalStateException.class, sandbox::start);
    }
}

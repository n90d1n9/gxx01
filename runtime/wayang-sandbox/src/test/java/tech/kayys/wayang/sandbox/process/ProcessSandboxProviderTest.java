package tech.kayys.wayang.sandbox.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.wayang.spi.sandbox.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProcessSandboxProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void testProcessSandboxExecutionAndLifecycle() throws Exception {
        ProcessSandboxProvider provider = new ProcessSandboxProvider(tempDir);
        assertEquals(ProcessSandboxProvider.ID, provider.getProviderId());

        SandboxRequest req = new SandboxRequest(
                "sb-proc-1",
                SandboxType.PROCESS,
                Set.of("process-isolation"),
                new SandboxLimits(-1L, -1L, -1L, -1L, Duration.ofSeconds(10), 1024L, -1L),
                SandboxFilesystem.empty(),
                SandboxNetwork.disabled(),
                Map.of("MY_TEST_VAR", "WORLD"),
                Map.of("tenantId", "tenant-alpha")
        );

        Sandbox sandbox = provider.create(req);
        assertInstanceOf(ProcessSandbox.class, sandbox);
        ProcessSandbox processSandbox = (ProcessSandbox) sandbox;

        assertEquals(SandboxState.CREATED, processSandbox.state());
        processSandbox.start();
        assertEquals(SandboxState.RUNNING, processSandbox.state());

        assertEquals("tenant-alpha", processSandbox.context().tenantId().orElse(""));

        // Execute simple command
        ProcessExecutionRequest execReq = new ProcessExecutionRequest(
                List.of("echo", "HELLO"),
                Map.of(),
                Duration.ofSeconds(5),
                false
        );
        ProcessExecutionResult result = processSandbox.execute(execReq).join();
        assertTrue(result.successful());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("HELLO"));

        // Destroy
        processSandbox.destroy();
        assertEquals(SandboxState.DESTROYED, processSandbox.state());
    }
}

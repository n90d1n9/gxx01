package tech.kayys.wayang.sandbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.wayang.spi.sandbox.SandboxConfiguration;
import tech.kayys.wayang.spi.sandbox.SandboxExecutionResult;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class LocalProcessSandboxTest {

    private LocalProcessSandbox sandbox;

    @BeforeEach
    void setUp() throws Exception {
        SandboxConfiguration config = new SandboxConfiguration();
        config.addEnvironmentVariable("TEST_KEY", "TEST_VAL");
        sandbox = new LocalProcessSandbox(config);
        sandbox.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (sandbox != null) {
            sandbox.stop();
        }
    }

    @Test
    void testWriteAndReadFile() throws Exception {
        sandbox.writeFile("sub/test.txt", "Hello from Sandbox!");
        String content = sandbox.readFile("sub/test.txt");
        assertEquals("Hello from Sandbox!", content);
    }

    @Test
    void testPathTraversalPrevented() {
        assertThrows(SecurityException.class, () -> {
            sandbox.writeFile("../outside.txt", "Evil content");
        });
    }

    @Test
    void testExecuteCommand() throws Exception {
        SandboxExecutionResult result = sandbox.executeCommand("echo Hello Sandbox", 5000);
        assertTrue(result.isSuccess());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Hello Sandbox"));
    }

    @Test
    void testLocalSandboxProvider() throws Exception {
        LocalSandboxProvider provider = new LocalSandboxProvider();
        assertEquals("local", provider.getProviderId());
        assertEquals("local", provider.providerId());
        assertNotNull(provider.descriptor());
        assertTrue(provider.descriptor().supports(tech.kayys.wayang.spi.sandbox.SandboxType.PROCESS));

        SandboxConfiguration config = new SandboxConfiguration();
        try (var s = provider.createSandbox(config)) {
            assertNotNull(s);
        }

        // Test Phase 4.1 create(SandboxRequest)
        tech.kayys.wayang.spi.sandbox.SandboxRequest req = new tech.kayys.wayang.spi.sandbox.SandboxRequest(
                "req-sb-1",
                tech.kayys.wayang.spi.sandbox.SandboxType.PROCESS,
                java.util.Set.of("process-isolation"),
                tech.kayys.wayang.spi.sandbox.SandboxLimits.unlimited(),
                tech.kayys.wayang.spi.sandbox.SandboxFilesystem.empty(),
                tech.kayys.wayang.spi.sandbox.SandboxNetwork.disabled(),
                java.util.Map.of("MY_VAR", "my_val"),
                java.util.Map.of()
        );
        try (var s2 = provider.create(req)) {
            assertNotNull(s2);
            assertEquals(tech.kayys.wayang.spi.sandbox.SandboxState.CREATED, s2.state());
            s2.start();
            assertEquals(tech.kayys.wayang.spi.sandbox.SandboxState.RUNNING, s2.state());
            assertNotNull(s2.descriptor());
            assertEquals("req-sb-1", s2.descriptor().id());
            assertNotNull(s2.context());
            assertTrue(s2.context().workspace().isPresent());
        }
    }

    @Test
    void testDescriptorAndLifecycle() throws Exception {
        assertNotNull(sandbox.descriptor());
        assertEquals(tech.kayys.wayang.spi.sandbox.SandboxType.PROCESS, sandbox.descriptor().type());
        assertEquals(tech.kayys.wayang.spi.sandbox.SandboxState.RUNNING, sandbox.state());
        assertNotNull(sandbox.context());
        assertTrue(sandbox.context().workspace().isPresent());

        sandbox.destroy();
        assertEquals(tech.kayys.wayang.spi.sandbox.SandboxState.DESTROYED, sandbox.state());
    }
}

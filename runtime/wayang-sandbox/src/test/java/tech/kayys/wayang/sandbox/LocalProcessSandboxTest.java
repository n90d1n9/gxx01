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

        SandboxConfiguration config = new SandboxConfiguration();
        try (var s = provider.createSandbox(config)) {
            assertNotNull(s);
        }
    }
}

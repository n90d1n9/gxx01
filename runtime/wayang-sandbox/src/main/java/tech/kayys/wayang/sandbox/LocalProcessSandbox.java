package tech.kayys.wayang.sandbox;

import tech.kayys.wayang.extension.Version;
import tech.kayys.wayang.spi.sandbox.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Local process-based implementation of {@link Sandbox}.
 * Isolates execution within a designated working directory on the host filesystem.
 */
public class LocalProcessSandbox implements Sandbox {

    private final String sandboxId;
    private final Path workingDir;
    private final Map<String, String> envVars;
    private final boolean autoDeleteOnClose;
    private final SandboxDescriptor descriptor;
    private final SandboxContext context;
    private volatile SandboxState state = SandboxState.CREATED;

    public LocalProcessSandbox(SandboxConfiguration config) throws IOException {
        this(
                config != null && config.getWorkingDirectory() != null && !config.getWorkingDirectory().isBlank()
                        ? Path.of(config.getWorkingDirectory())
                        : Files.createTempDirectory("wayang-sandbox-"),
                config != null && config.getEnvironmentVariables() != null
                        ? config.getEnvironmentVariables()
                        : Map.of(),
                config == null || config.getWorkingDirectory() == null || config.getWorkingDirectory().isBlank(),
                UUID.randomUUID().toString()
        );
    }

    public LocalProcessSandbox(SandboxRequest request) throws IOException {
        this(
                Files.createTempDirectory("wayang-sandbox-"),
                request != null ? request.environment() : Map.of(),
                true,
                request != null && request.sandboxId() != null ? request.sandboxId() : UUID.randomUUID().toString()
        );
    }

    public LocalProcessSandbox(Path workingDir, Map<String, String> envVars, boolean autoDeleteOnClose) {
        this(workingDir, envVars, autoDeleteOnClose, UUID.randomUUID().toString());
    }

    public LocalProcessSandbox(Path workingDir, Map<String, String> envVars, boolean autoDeleteOnClose, String sandboxId) {
        this.sandboxId = sandboxId;
        this.workingDir = workingDir.toAbsolutePath().normalize();
        this.envVars = envVars != null ? Map.copyOf(envVars) : Map.of();
        this.autoDeleteOnClose = autoDeleteOnClose;

        this.descriptor = new SandboxDescriptor(
                sandboxId,
                "Local Process Sandbox",
                "Local OS process isolated environment",
                SandboxType.PROCESS,
                Version.parse("1.0.0"),
                Set.of("process-isolation", "filesystem-isolation"),
                Map.of("workingDirectory", this.workingDir.toString())
        );

        Instant created = Instant.now();
        this.context = new SandboxContext() {
            @Override
            public String sandboxId() {
                return sandboxId;
            }

            @Override
            public String executionId() {
                return sandboxId;
            }

            @Override
            public Optional<String> tenantId() {
                return Optional.empty();
            }

            @Override
            public Optional<String> agentId() {
                return Optional.empty();
            }

            @Override
            public SandboxDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public Optional<Path> workspace() {
                return Optional.of(workingDir);
            }

            @Override
            public Optional<Path> inputDirectory() {
                return Optional.of(workingDir.resolve("input"));
            }

            @Override
            public Optional<Path> outputDirectory() {
                return Optional.of(workingDir.resolve("output"));
            }

            @Override
            public Instant createdAt() {
                return created;
            }

            @Override
            public Map<String, Object> attributes() {
                return Map.of();
            }
        };
    }

    @Override
    public SandboxDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public SandboxState state() {
        return state;
    }

    @Override
    public SandboxContext context() {
        return context;
    }

    @Override
    public synchronized void start() throws Exception {
        state = SandboxState.STARTING;
        Files.createDirectories(workingDir);
        state = SandboxState.RUNNING;
    }

    @Override
    public synchronized void stop() throws Exception {
        state = SandboxState.STOPPING;
        if (autoDeleteOnClose && Files.exists(workingDir)) {
            try (var stream = Files.walk(workingDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
        state = SandboxState.STOPPED;
    }

    @Override
    public synchronized void destroy() throws Exception {
        stop();
        state = SandboxState.DESTROYED;
    }

    @Override
    public SandboxExecutionResult executeCommand(String command, long timeoutMillis) throws Exception {
        ensureStarted();
        if (command == null || command.isBlank()) {
            return new SandboxExecutionResult(0, "", "");
        }

        ProcessBuilder pb;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            pb = new ProcessBuilder("sh", "-c", command);
        }

        pb.directory(workingDir.toFile());
        pb.environment().putAll(envVars);

        Process process = pb.start();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread outReader = Thread.ofVirtual().start(() -> readStream(process.getInputStream(), stdout));
        Thread errReader = Thread.ofVirtual().start(() -> readStream(process.getErrorStream(), stderr));

        boolean completed = process.waitFor(timeoutMillis > 0 ? timeoutMillis : 60_000, TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            return new SandboxExecutionResult(137, stdout.toString(), "Command timed out after " + timeoutMillis + "ms");
        }

        outReader.join(1000);
        errReader.join(1000);

        return new SandboxExecutionResult(process.exitValue(), stdout.toString(), stderr.toString());
    }

    @Override
    public void writeFile(String path, String content) throws Exception {
        ensureStarted();
        Path resolved = resolveSafe(path);
        if (resolved.getParent() != null) {
            Files.createDirectories(resolved.getParent());
        }
        Files.writeString(resolved, content != null ? content : "", StandardCharsets.UTF_8);
    }

    @Override
    public String readFile(String path) throws Exception {
        ensureStarted();
        Path resolved = resolveSafe(path);
        if (!Files.exists(resolved)) {
            throw new IOException("File not found in sandbox: " + path);
        }
        return Files.readString(resolved, StandardCharsets.UTF_8);
    }

    public Path getWorkingDirectory() {
        return workingDir;
    }

    private void ensureStarted() throws IllegalStateException {
        if (state != SandboxState.RUNNING) {
            try {
                start();
            } catch (Exception e) {
                state = SandboxState.FAILED;
                throw new IllegalStateException("Failed to auto-start sandbox: " + e.getMessage(), e);
            }
        }
    }

    private Path resolveSafe(String relativeOrSubPath) throws SecurityException {
        Path resolved = workingDir.resolve(relativeOrSubPath).normalize();
        if (!resolved.startsWith(workingDir)) {
            throw new SecurityException("Sandbox path traversal denied: " + relativeOrSubPath);
        }
        return resolved;
    }

    private void readStream(java.io.InputStream in, StringBuilder out) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append("\n");
            }
        } catch (IOException ignored) {}
    }
}

package tech.kayys.wayang.sandbox;

import tech.kayys.wayang.spi.sandbox.Sandbox;
import tech.kayys.wayang.spi.sandbox.SandboxConfiguration;
import tech.kayys.wayang.spi.sandbox.SandboxExecutionResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Local process-based implementation of {@link Sandbox}.
 * Isolates execution within a designated working directory on the host filesystem.
 */
public class LocalProcessSandbox implements Sandbox {

    private final Path workingDir;
    private final Map<String, String> envVars;
    private final boolean autoDeleteOnClose;
    private volatile boolean started = false;

    public LocalProcessSandbox(SandboxConfiguration config) throws IOException {
        if (config != null && config.getWorkingDirectory() != null && !config.getWorkingDirectory().isBlank()) {
            this.workingDir = Path.of(config.getWorkingDirectory()).toAbsolutePath().normalize();
            this.autoDeleteOnClose = false;
        } else {
            this.workingDir = Files.createTempDirectory("wayang-sandbox-");
            this.autoDeleteOnClose = true;
        }
        this.envVars = config != null && config.getEnvironmentVariables() != null
                ? Map.copyOf(config.getEnvironmentVariables())
                : Map.of();
    }

    public LocalProcessSandbox(Path workingDir, Map<String, String> envVars, boolean autoDeleteOnClose) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
        this.envVars = envVars != null ? Map.copyOf(envVars) : Map.of();
        this.autoDeleteOnClose = autoDeleteOnClose;
    }

    @Override
    public void start() throws Exception {
        Files.createDirectories(workingDir);
        this.started = true;
    }

    @Override
    public void stop() throws Exception {
        this.started = false;
        if (autoDeleteOnClose && Files.exists(workingDir)) {
            try (var stream = Files.walk(workingDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
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
        if (!started) {
            try {
                start();
            } catch (Exception e) {
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

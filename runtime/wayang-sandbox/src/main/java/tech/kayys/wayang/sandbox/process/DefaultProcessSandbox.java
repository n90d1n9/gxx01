package tech.kayys.wayang.sandbox.process;

import tech.kayys.wayang.extension.Version;
import tech.kayys.wayang.sandbox.filesystem.DefaultSandboxFilesystem;
import tech.kayys.wayang.spi.sandbox.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultProcessSandbox implements ProcessSandbox {

    private final String sandboxId;
    private final SandboxRequest request;
    private final Path workspace;
    private final Path inputDirectory;
    private final Path outputDirectory;
    private final Instant createdAt;
    private final SandboxFilesystem filesystem;

    private final AtomicReference<SandboxState> state =
            new AtomicReference<>(SandboxState.CREATED);

    private final ExecutorService ioExecutor =
            Executors.newVirtualThreadPerTaskExecutor();

    private volatile Process currentProcess;

    public DefaultProcessSandbox(
            String sandboxId,
            SandboxRequest request,
            Path workspace,
            Path inputDirectory,
            Path outputDirectory) {

        this.sandboxId = Objects.requireNonNull(sandboxId, "sandboxId");
        this.request = Objects.requireNonNull(request, "request");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.inputDirectory = Objects.requireNonNull(inputDirectory, "inputDirectory");
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
        this.createdAt = Instant.now();

        List<FilesystemRoot> roots = List.of(
                new FilesystemRoot("workspace", workspace, FilesystemAccess.READ_WRITE, false),
                new FilesystemRoot("input", inputDirectory, FilesystemAccess.READ, false),
                new FilesystemRoot("output", outputDirectory, FilesystemAccess.READ_WRITE, false)
        );
        this.filesystem = new DefaultSandboxFilesystem(FilesystemSandboxPolicy.strict(roots));
    }

    @Override
    public SandboxDescriptor descriptor() {
        return new SandboxDescriptor(
                sandboxId,
                "Process Sandbox",
                "OS process execution sandbox",
                SandboxType.PROCESS,
                Version.parse("1.0.0"),
                Set.of(
                        "process-isolation",
                        "temporary-workspace"
                ),
                Map.of()
        );
    }

    @Override
    public SandboxState state() {
        return state.get();
    }

    @Override
    public SandboxContext context() {
        return new DefaultProcessSandboxContext(
                sandboxId,
                request,
                descriptor(),
                workspace,
                inputDirectory,
                outputDirectory,
                createdAt
        );
    }

    @Override
    public SandboxFilesystem filesystem() {
        return filesystem;
    }

    @Override
    public void start() {
        transition(SandboxState.CREATED, SandboxState.STARTING);
        state.set(SandboxState.RUNNING);
    }

    @Override
    public void stop() {
        SandboxState current = state.get();
        if (current == SandboxState.STOPPED || current == SandboxState.DESTROYED) {
            return;
        }

        if (current != SandboxState.RUNNING && current != SandboxState.STARTING) {
            return;
        }

        state.set(SandboxState.STOPPING);

        Process active = currentProcess;
        if (active != null && active.isAlive()) {
            terminateProcessTree(active);
        }

        state.set(SandboxState.STOPPED);
    }

    @Override
    public void destroy() throws Exception {
        SandboxState current = state.get();
        if (current != SandboxState.STOPPED && current != SandboxState.CREATED && current != SandboxState.FAILED) {
            stop();
        }

        if (current != SandboxState.DESTROYED) {
            deleteWorkspace(workspace);
            ioExecutor.close();
            state.set(SandboxState.DESTROYED);
        }
    }

    @Override
    public CompletableFuture<ProcessExecutionResult> execute(ProcessExecutionRequest executionRequest) {
        Objects.requireNonNull(executionRequest, "executionRequest");

        if (state.get() != SandboxState.RUNNING) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Sandbox is not running: " + state.get()));
        }

        return CompletableFuture.supplyAsync(() -> executeBlocking(executionRequest), ioExecutor);
    }

    private ProcessExecutionResult executeBlocking(ProcessExecutionRequest execReq) {
        Instant started = Instant.now();
        ProcessBuilder builder = new ProcessBuilder(execReq.command());
        builder.directory(workspace.toFile());

        Map<String, String> environment = builder.environment();
        if (!execReq.inheritEnvironment()) {
            environment.clear();
        }

        environment.putAll(execReq.environment());
        environment.put("WAYANG_SANDBOX_ID", sandboxId);
        environment.put("WAYANG_WORKSPACE", workspace.toString());
        environment.put("WAYANG_INPUT_DIR", inputDirectory.toString());
        environment.put("WAYANG_OUTPUT_DIR", outputDirectory.toString());

        long outputLimit = request.limits().hasOutputLimit() ? request.limits().outputBytes() : -1L;
        AtomicBoolean outputLimitExceeded = new AtomicBoolean(false);

        try {
            Process startedProcess = builder.start();
            this.currentProcess = startedProcess;

            CompletableFuture<String> stdoutFuture = readAsync(
                    startedProcess.getInputStream(), outputLimit, outputLimitExceeded, startedProcess);
            CompletableFuture<String> stderrFuture = readAsync(
                    startedProcess.getErrorStream(), outputLimit, outputLimitExceeded, startedProcess);

            boolean completed = startedProcess.waitFor(execReq.timeout().toMillis(), TimeUnit.MILLISECONDS);
            boolean timedOut = false;
            boolean forciblyTerminated = false;

            if (!completed) {
                timedOut = true;
                terminateProcessTree(startedProcess);
                if (startedProcess.isAlive()) {
                    forciblyTerminated = true;
                }
            }

            int exitCode = startedProcess.isAlive() ? -1 : startedProcess.exitValue();
            String stdout = stdoutFuture.join();
            String stderr = stderrFuture.join();
            Duration duration = Duration.between(started, Instant.now());

            return new ProcessExecutionResult(
                    exitCode,
                    stdout,
                    stderr,
                    duration,
                    timedOut,
                    forciblyTerminated,
                    outputLimitExceeded.get()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Process active = currentProcess;
            if (active != null) {
                terminateProcessTree(active);
            }
            throw new CompletionException(e);
        } catch (IOException e) {
            throw new CompletionException(e);
        } finally {
            this.currentProcess = null;
        }
    }

    private CompletableFuture<String> readAsync(
            InputStream inputStream,
            long outputLimit,
            AtomicBoolean outputExceeded,
            Process proc) {

        return CompletableFuture.supplyAsync(() -> {
            try (InputStream input = inputStream;
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192];
                int read;
                long totalBytes = 0;

                while ((read = input.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    totalBytes += read;

                    if (outputLimit >= 0 && totalBytes > outputLimit) {
                        outputExceeded.set(true);
                        terminateProcessTree(proc);
                        break;
                    }
                }
                return out.toString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, ioExecutor);
    }

    private static void terminateProcessTree(Process root) {
        ProcessHandle handle = root.toHandle();
        handle.descendants()
                .toList()
                .reversed()
                .forEach(DefaultProcessSandbox::destroyHandle);
        destroyHandle(handle);
    }

    private static void destroyHandle(ProcessHandle handle) {
        if (!handle.isAlive()) {
            return;
        }
        handle.destroy();
        try {
            if (!handle.onExit().orTimeout(2, TimeUnit.SECONDS).isDone()) {
                handle.destroyForcibly();
            }
        } catch (Exception ignored) {
            handle.destroyForcibly();
        }
    }

    private static void deleteWorkspace(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(item -> {
                        try {
                            Files.deleteIfExists(item);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    private void transition(SandboxState expected, SandboxState next) {
        if (!state.compareAndSet(expected, next)) {
            throw new IllegalStateException("Invalid sandbox transition: " + state.get() + " -> " + next);
        }
    }
}

package tech.kayys.wayang.sandbox.process;

import tech.kayys.wayang.extension.Version;
import tech.kayys.wayang.spi.sandbox.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ProcessSandboxProvider implements SandboxProvider {

    public static final String ID = "wayang.sandbox.process";

    private final Path baseDirectory;

    public ProcessSandboxProvider(Path baseDirectory) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory");
    }

    @Override
    public String getProviderId() {
        return ID;
    }

    @Override
    public SandboxProviderDescriptor descriptor() {
        return new SandboxProviderDescriptor(
                ID,
                "Process Sandbox",
                "OS process based execution isolation",
                Version.parse("1.0.0"),
                Set.of(SandboxType.PROCESS, SandboxType.NONE),
                Set.of(
                        IsolationFeature.PROCESS_ISOLATION,
                        IsolationFeature.TEMPORARY_WORKSPACE
                ),
                Set.of(),
                Set.of(
                        ResourceLimitFeature.EXECUTION_TIMEOUT,
                        ResourceLimitFeature.OUTPUT_LIMIT
                ),
                Map.of()
        );
    }

    @Override
    public Sandbox create(SandboxRequest request) throws Exception {
        if (request.preferredType() != SandboxType.PROCESS
                && request.preferredType() != SandboxType.NONE) {
            throw new IllegalArgumentException(
                    "ProcessSandboxProvider cannot create " + request.preferredType());
        }

        Files.createDirectories(baseDirectory);

        String sandboxId = request.sandboxId() != null
                ? request.sandboxId()
                : UUID.randomUUID().toString();

        Path workspace = Files.createDirectories(baseDirectory.resolve(sandboxId));
        Path input = Files.createDirectories(workspace.resolve("input"));
        Path output = Files.createDirectories(workspace.resolve("output"));

        return new DefaultProcessSandbox(
                sandboxId,
                request,
                workspace,
                input,
                output
        );
    }
}

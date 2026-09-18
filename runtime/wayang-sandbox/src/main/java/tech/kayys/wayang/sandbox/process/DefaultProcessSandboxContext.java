package tech.kayys.wayang.sandbox.process;

import tech.kayys.wayang.spi.sandbox.SandboxContext;
import tech.kayys.wayang.spi.sandbox.SandboxDescriptor;
import tech.kayys.wayang.spi.sandbox.SandboxRequest;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

final class DefaultProcessSandboxContext implements SandboxContext {

    private final String sandboxId;
    private final SandboxRequest request;
    private final SandboxDescriptor descriptor;
    private final Path workspace;
    private final Path inputDirectory;
    private final Path outputDirectory;
    private final Instant createdAt;

    DefaultProcessSandboxContext(
            String sandboxId,
            SandboxRequest request,
            SandboxDescriptor descriptor,
            Path workspace,
            Path inputDirectory,
            Path outputDirectory,
            Instant createdAt) {

        this.sandboxId = sandboxId;
        this.request = request;
        this.descriptor = descriptor;
        this.workspace = workspace;
        this.inputDirectory = inputDirectory;
        this.outputDirectory = outputDirectory;
        this.createdAt = createdAt;
    }

    @Override
    public String sandboxId() {
        return sandboxId;
    }

    @Override
    public String executionId() {
        return request.attributes() != null && request.attributes().get("executionId") != null
                ? String.valueOf(request.attributes().get("executionId"))
                : sandboxId;
    }

    @Override
    public Optional<String> tenantId() {
        return request.attributes() != null && request.attributes().get("tenantId") != null
                ? Optional.of(String.valueOf(request.attributes().get("tenantId")))
                : Optional.empty();
    }

    @Override
    public Optional<String> agentId() {
        return request.attributes() != null && request.attributes().get("agentId") != null
                ? Optional.of(String.valueOf(request.attributes().get("agentId")))
                : Optional.empty();
    }

    @Override
    public SandboxDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public Optional<Path> workspace() {
        return Optional.of(workspace);
    }

    @Override
    public Optional<Path> inputDirectory() {
        return Optional.of(inputDirectory);
    }

    @Override
    public Optional<Path> outputDirectory() {
        return Optional.of(outputDirectory);
    }

    @Override
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public Map<String, Object> attributes() {
        return request.attributes();
    }
}

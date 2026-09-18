package tech.kayys.wayang.sandbox.context;

import tech.kayys.wayang.spi.sandbox.SandboxContext;
import tech.kayys.wayang.spi.sandbox.SandboxDescriptor;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DefaultSandboxContext implements SandboxContext {

    private final String sandboxId;
    private final String executionId;
    private final String tenantId;
    private final String userId;
    private final String agentId;
    private final String sessionId;
    private final String correlationId;
    private final SandboxDescriptor descriptor;
    private final Path workspace;
    private final Path inputDirectory;
    private final Path outputDirectory;
    private final Instant createdAt;
    private final Instant deadline;
    private final Map<String, Object> attributes;

    public DefaultSandboxContext(
            String sandboxId,
            String executionId,
            String tenantId,
            String userId,
            String agentId,
            String sessionId,
            String correlationId,
            SandboxDescriptor descriptor,
            Path workspace,
            Path inputDirectory,
            Path outputDirectory,
            Instant createdAt,
            Instant deadline,
            Map<String, Object> attributes) {

        this.sandboxId = Objects.requireNonNull(sandboxId, "sandboxId must not be null");
        this.executionId = Objects.requireNonNull(executionId, "executionId must not be null");
        this.tenantId = tenantId;
        this.userId = userId;
        this.agentId = agentId;
        this.sessionId = sessionId;
        this.correlationId = correlationId;
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        this.workspace = workspace;
        this.inputDirectory = inputDirectory;
        this.outputDirectory = outputDirectory;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.deadline = deadline;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    @Override
    public String sandboxId() {
        return sandboxId;
    }

    @Override
    public String executionId() {
        return executionId;
    }

    @Override
    public Optional<String> tenantId() {
        return Optional.ofNullable(tenantId);
    }

    @Override
    public Optional<String> userId() {
        return Optional.ofNullable(userId);
    }

    @Override
    public Optional<String> agentId() {
        return Optional.ofNullable(agentId);
    }

    @Override
    public Optional<String> sessionId() {
        return Optional.ofNullable(sessionId);
    }

    @Override
    public Optional<String> correlationId() {
        return Optional.ofNullable(correlationId);
    }

    @Override
    public SandboxDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public Optional<Path> workspace() {
        return Optional.ofNullable(workspace);
    }

    @Override
    public Optional<Path> inputDirectory() {
        return Optional.ofNullable(inputDirectory);
    }

    @Override
    public Optional<Path> outputDirectory() {
        return Optional.ofNullable(outputDirectory);
    }

    @Override
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public Optional<Instant> deadline() {
        return Optional.ofNullable(deadline);
    }

    @Override
    public Map<String, Object> attributes() {
        return attributes;
    }

    public static Builder builder(String sandboxId, String executionId, SandboxDescriptor descriptor) {
        return new Builder(sandboxId, executionId, descriptor);
    }

    public static final class Builder {
        private final String sandboxId;
        private final String executionId;
        private final SandboxDescriptor descriptor;
        private String tenantId;
        private String userId;
        private String agentId;
        private String sessionId;
        private String correlationId;
        private Path workspace;
        private Path inputDirectory;
        private Path outputDirectory;
        private Instant createdAt = Instant.now();
        private Instant deadline;
        private Map<String, Object> attributes = Map.of();

        private Builder(String sandboxId, String executionId, SandboxDescriptor descriptor) {
            this.sandboxId = sandboxId;
            this.executionId = executionId;
            this.descriptor = descriptor;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder workspace(Path workspace) {
            this.workspace = workspace;
            return this;
        }

        public Builder inputDirectory(Path inputDirectory) {
            this.inputDirectory = inputDirectory;
            return this;
        }

        public Builder outputDirectory(Path outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder deadline(Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public DefaultSandboxContext build() {
            return new DefaultSandboxContext(
                    sandboxId, executionId, tenantId, userId, agentId, sessionId,
                    correlationId, descriptor, workspace, inputDirectory, outputDirectory,
                    createdAt, deadline, attributes
            );
        }
    }
}

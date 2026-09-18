package tech.kayys.wayang.sandbox.runtime.observability;

import tech.kayys.wayang.spi.sandbox.Sandbox;
import tech.kayys.wayang.spi.sandbox.SandboxManager;
import tech.kayys.wayang.spi.sandbox.SandboxState;
import tech.kayys.wayang.spi.sandbox.observability.*;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DefaultSandboxDiagnostics implements SandboxDiagnostics {

    private final SandboxManager sandboxManager;

    public DefaultSandboxDiagnostics(SandboxManager sandboxManager) {
        this.sandboxManager = Objects.requireNonNull(sandboxManager, "sandboxManager must not be null");
    }

    @Override
    public SandboxDiagnosticsSnapshot inspect(String sandboxId) throws Exception {
        Objects.requireNonNull(sandboxId, "sandboxId must not be null");

        Sandbox sandbox = sandboxManager.find(sandboxId)
                .orElseThrow(() -> new IllegalArgumentException("Sandbox not found: " + sandboxId));

        SandboxHealthStatus healthStatus = sandbox.state() == SandboxState.FAILED
                ? SandboxHealthStatus.UNHEALTHY
                : (sandbox.state() == SandboxState.RUNNING ? SandboxHealthStatus.HEALTHY : SandboxHealthStatus.UNKNOWN);

        Set<SandboxObservabilityFeature> features = Set.of(
                SandboxObservabilityFeature.LIFECYCLE_EVENTS,
                SandboxObservabilityFeature.FILESYSTEM_METRICS,
                SandboxObservabilityFeature.ARTIFACT_METRICS,
                SandboxObservabilityFeature.HEALTH_STATUS
        );

        return new SandboxDiagnosticsSnapshot(
                sandbox.context().sandboxId(),
                sandbox.context().executionId(),
                sandbox.descriptor().type(),
                sandbox.state(),
                healthStatus,
                features,
                ResourceMetrics.empty(),
                Instant.now(),
                Map.of(
                        "type", sandbox.descriptor().type().name(),
                        "state", sandbox.state().name()
                )
        );
    }
}

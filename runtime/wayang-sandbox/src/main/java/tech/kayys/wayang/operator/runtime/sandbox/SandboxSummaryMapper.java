package tech.kayys.wayang.operator.runtime.sandbox;

import tech.kayys.wayang.spi.operator.sandbox.SandboxDiagnosticsSummary;
import tech.kayys.wayang.spi.operator.sandbox.SandboxHealthSummary;
import tech.kayys.wayang.spi.operator.sandbox.SandboxMetricsSummary;
import tech.kayys.wayang.spi.operator.sandbox.SandboxSummary;
import tech.kayys.wayang.spi.sandbox.Sandbox;
import tech.kayys.wayang.spi.sandbox.SandboxState;
import tech.kayys.wayang.spi.sandbox.observability.SandboxHealthStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SandboxSummaryMapper {

    private SandboxSummaryMapper() {
    }

    public static SandboxSummary map(Sandbox sandbox) {
        if (sandbox == null) {
            return null;
        }

        String executionId = sandbox.context() != null ? sandbox.context().executionId() : null;
        String tenantId = (sandbox.context() != null && sandbox.context().tenantId().isPresent())
                ? sandbox.context().tenantId().get()
                : null;
        String agentId = (sandbox.context() != null && sandbox.context().agentId().isPresent())
                ? sandbox.context().agentId().get()
                : null;

        var descriptor = sandbox.descriptor();
        var type = descriptor != null ? descriptor.type() : null;
        String providerId = (descriptor != null && descriptor.attributes().containsKey("providerId"))
                ? String.valueOf(descriptor.attributes().get("providerId"))
                : null;
        Instant createdAt = sandbox.context() != null ? sandbox.context().createdAt() : Instant.now();
        Instant startedAt = sandbox.state() == SandboxState.RUNNING ? Instant.now() : null;
        Map<String, Object> attrs = descriptor != null ? descriptor.attributes() : Map.of();

        return new SandboxSummary(
                sandbox.id(),
                executionId,
                tenantId,
                agentId,
                type,
                sandbox.state(),
                providerId,
                createdAt,
                startedAt,
                attrs
        );
    }

    public static SandboxHealthSummary mapHealth(Sandbox sandbox) {
        if (sandbox == null) {
            return null;
        }
        SandboxHealthStatus status = SandboxHealthStatus.HEALTHY;
        String reason = "Healthy";
        if (sandbox.state() == SandboxState.FAILED) {
            status = SandboxHealthStatus.UNHEALTHY;
            reason = "Sandbox in failed state";
        } else if (sandbox.state() == SandboxState.STOPPED || sandbox.state() == SandboxState.DESTROYED) {
            status = SandboxHealthStatus.UNKNOWN;
            reason = "Sandbox terminated";
        }

        return new SandboxHealthSummary(
                sandbox.id(),
                status,
                Instant.now(),
                reason,
                Map.of("state", sandbox.state().name())
        );
    }

    public static SandboxMetricsSummary mapMetrics(Sandbox sandbox) {
        if (sandbox == null) {
            return null;
        }
        var descriptor = sandbox.descriptor();
        String providerId = (descriptor != null && descriptor.attributes().containsKey("providerId"))
                ? String.valueOf(descriptor.attributes().get("providerId"))
                : "unknown";
        return new SandboxMetricsSummary(
                sandbox.id(),
                sandbox.context() != null ? sandbox.context().executionId() : null,
                providerId,
                Instant.now(),
                null,
                null,
                null,
                null
        );
    }

    public static SandboxDiagnosticsSummary mapDiagnostics(Sandbox sandbox) {
        if (sandbox == null) {
            return null;
        }
        List<String> warnings = sandbox.state() == SandboxState.FAILED
                ? List.of("Sandbox encountered a runtime failure")
                : List.of();

        String providerId = (sandbox.descriptor() != null && sandbox.descriptor().attributes().containsKey("providerId"))
                ? String.valueOf(sandbox.descriptor().attributes().get("providerId"))
                : "none";

        return new SandboxDiagnosticsSummary(
                sandbox.id(),
                Instant.now(),
                sandbox.state().name(),
                sandbox.state() == SandboxState.FAILED ? "UNHEALTHY" : "HEALTHY",
                warnings,
                Map.of("providerId", providerId)
        );
    }
}

package tech.kayys.wayang.sandbox.runtime.observability;

import tech.kayys.wayang.spi.sandbox.Sandbox;
import tech.kayys.wayang.spi.sandbox.SandboxManager;
import tech.kayys.wayang.spi.sandbox.SandboxState;
import tech.kayys.wayang.spi.sandbox.observability.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class DefaultSandboxObservabilityManager implements SandboxObservabilityManager {

    private final SandboxObservability observability;
    private final SandboxManager sandboxManager;
    private final ConcurrentLinkedQueue<SandboxObservation> observations = new ConcurrentLinkedQueue<>();
    private final ConcurrentMap<String, SandboxMetricsSnapshot> latestMetrics = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SandboxFailure> latestFailures = new ConcurrentHashMap<>();

    public DefaultSandboxObservabilityManager(
            SandboxObservability observability,
            SandboxManager sandboxManager) {

        this.observability = observability != null ? observability : NoopSandboxObservability.INSTANCE;
        this.sandboxManager = sandboxManager;
    }

    public DefaultSandboxObservabilityManager() {
        this(NoopSandboxObservability.INSTANCE, null);
    }

    @Override
    public void record(SandboxObservation observation) {
        if (observation == null) return;
        observations.add(observation);
        observability.recordEvent(observation);
    }

    @Override
    public void recordMetrics(SandboxMetricsSnapshot metrics) {
        if (metrics == null) return;
        latestMetrics.put(metrics.sandboxId(), metrics);
        observability.recordMetrics(metrics);
    }

    @Override
    public void recordFailure(SandboxFailure failure) {
        if (failure == null) return;
        latestFailures.put(failure.sandboxId(), failure);
        observability.recordFailure(failure);
    }

    @Override
    public Optional<SandboxHealth> health(String sandboxId) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return Optional.empty();
        }

        if (latestFailures.containsKey(sandboxId)) {
            SandboxFailure failure = latestFailures.get(sandboxId);
            return Optional.of(new SandboxHealth(
                    sandboxId,
                    SandboxHealthStatus.UNHEALTHY,
                    failure.timestamp(),
                    failure.reason() != null ? failure.reason() : failure.category(),
                    Map.of("category", failure.category())
            ));
        }

        if (sandboxManager != null) {
            Optional<Sandbox> sb = sandboxManager.find(sandboxId);
            if (sb.isPresent()) {
                SandboxState state = sb.get().state();
                SandboxHealthStatus status = (state == SandboxState.RUNNING)
                        ? SandboxHealthStatus.HEALTHY
                        : (state == SandboxState.FAILED ? SandboxHealthStatus.UNHEALTHY : SandboxHealthStatus.UNKNOWN);

                return Optional.of(new SandboxHealth(
                        sandboxId,
                        status,
                        Instant.now(),
                        "State: " + state,
                        Map.of("state", state.name())
                ));
            }
        }

        return Optional.of(new SandboxHealth(
                sandboxId,
                SandboxHealthStatus.UNKNOWN,
                Instant.now(),
                "Sandbox not found",
                Map.of()
        ));
    }

    public List<SandboxObservation> getObservations() {
        return List.copyOf(observations);
    }
}

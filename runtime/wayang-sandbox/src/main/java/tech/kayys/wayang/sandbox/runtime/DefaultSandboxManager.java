package tech.kayys.wayang.sandbox.runtime;

import tech.kayys.wayang.spi.sandbox.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultSandboxManager implements SandboxManager {

    private final SandboxProviderRegistry providerRegistry;
    private final SandboxStateStore stateStore;
    private final ConcurrentMap<String, Sandbox> sandboxes = new ConcurrentHashMap<>();

    public DefaultSandboxManager(SandboxProviderRegistry providerRegistry) {
        this(providerRegistry, new DefaultSandboxStateStore());
    }

    public DefaultSandboxManager(
            SandboxProviderRegistry providerRegistry,
            SandboxStateStore stateStore) {

        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry must not be null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
    }

    @Override
    public Sandbox create(SandboxRequest request) throws Exception {
        Objects.requireNonNull(request, "request must not be null");

        String sandboxId = request.sandboxId();
        if (sandboxId == null || sandboxId.isBlank()) {
            throw new IllegalArgumentException("Sandbox ID must not be blank");
        }

        SandboxProvider provider;
        if (providerRegistry instanceof DefaultSandboxProviderRegistry defaultRegistry) {
            provider = defaultRegistry.select(request);
        } else {
            provider = providerRegistry.find(request.attributes().getOrDefault("providerId", "wayang.sandbox.process").toString())
                    .orElseThrow(() -> new IllegalStateException("No matching sandbox provider found"));
        }

        Sandbox sandbox = provider.create(request);

        if (!sandboxId.equals(sandbox.context().sandboxId())) {
            throw new IllegalStateException(
                    "Sandbox provider returned unexpected sandbox ID: " + sandbox.context().sandboxId());
        }

        Sandbox existing = sandboxes.putIfAbsent(sandboxId, sandbox);
        if (existing != null) {
            try {
                sandbox.destroy();
            } catch (Exception ignored) {
            }
            throw new IllegalStateException("Sandbox already exists: " + sandboxId);
        }

        saveSnapshot(sandbox, provider.providerId());
        return sandbox;
    }

    @Override
    public Optional<Sandbox> find(String sandboxId) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sandboxes.get(sandboxId));
    }

    @Override
    public List<Sandbox> list() {
        return List.copyOf(new ArrayList<>(sandboxes.values()));
    }

    @Override
    public void start(String sandboxId) throws Exception {
        Sandbox sandbox = requireSandbox(sandboxId);
        synchronized (sandbox) {
            SandboxState state = sandbox.state();
            if (state == SandboxState.RUNNING) {
                return;
            }
            SandboxStateMachine.requireTransition(state, SandboxState.STARTING);
            sandbox.start();
            saveSnapshot(sandbox, null);
        }
    }

    @Override
    public void stop(String sandboxId) throws Exception {
        Sandbox sandbox = requireSandbox(sandboxId);
        synchronized (sandbox) {
            SandboxState state = sandbox.state();
            if (state == SandboxState.STOPPED || state == SandboxState.DESTROYED) {
                return;
            }
            SandboxStateMachine.requireTransition(state, SandboxState.STOPPING);
            sandbox.stop();
            saveSnapshot(sandbox, null);
        }
    }

    @Override
    public void destroy(String sandboxId) throws Exception {
        Sandbox sandbox = requireSandbox(sandboxId);
        synchronized (sandbox) {
            SandboxState state = sandbox.state();
            if (state == SandboxState.DESTROYED) {
                sandboxes.remove(sandboxId, sandbox);
                stateStore.delete(sandboxId);
                return;
            }

            if (state == SandboxState.RUNNING
                    || state == SandboxState.STARTING
                    || state == SandboxState.STOPPING) {
                try {
                    sandbox.stop();
                } catch (Exception ignored) {
                }
            }

            sandbox.destroy();
            sandboxes.remove(sandboxId, sandbox);
            stateStore.delete(sandboxId);
        }
    }

    private Sandbox requireSandbox(String sandboxId) {
        return find(sandboxId).orElseThrow(() ->
                new IllegalArgumentException("Sandbox not found: " + sandboxId));
    }

    private void saveSnapshot(Sandbox sandbox, String providerId) {
        try {
            SandboxSnapshot snapshot = new SandboxSnapshot(
                    sandbox.context().sandboxId(),
                    sandbox.context().executionId(),
                    sandbox.context().tenantId().orElse("default"),
                    sandbox.context().agentId().orElse(""),
                    providerId != null ? providerId : "unknown",
                    sandbox.descriptor().type(),
                    sandbox.state(),
                    sandbox.context().createdAt(),
                    Instant.now(),
                    sandbox.context().attributes()
            );
            stateStore.save(snapshot);
        } catch (Exception ignored) {
            // Observability/snapshot failure does not block execution path
        }
    }
}

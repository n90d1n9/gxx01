package tech.kayys.wayang.sandbox.runtime;

import tech.kayys.wayang.spi.sandbox.SandboxSnapshot;
import tech.kayys.wayang.spi.sandbox.SandboxState;
import tech.kayys.wayang.spi.sandbox.SandboxStateStore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultSandboxStateStore implements SandboxStateStore {

    private final ConcurrentMap<String, SandboxSnapshot> store = new ConcurrentHashMap<>();

    @Override
    public void save(SandboxSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        store.put(snapshot.sandboxId(), snapshot);
    }

    @Override
    public Optional<SandboxSnapshot> find(String sandboxId) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(sandboxId));
    }

    @Override
    public List<SandboxSnapshot> findActive() {
        return store.values().stream()
                .filter(s -> s.state() != SandboxState.DESTROYED)
                .toList();
    }

    @Override
    public void delete(String sandboxId) {
        if (sandboxId != null) {
            store.remove(sandboxId);
        }
    }
}

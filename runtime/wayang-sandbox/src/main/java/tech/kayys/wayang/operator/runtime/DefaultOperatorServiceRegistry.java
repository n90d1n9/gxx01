package tech.kayys.wayang.operator.runtime;

import tech.kayys.wayang.spi.operator.OperatorService;
import tech.kayys.wayang.spi.operator.OperatorServiceRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DefaultOperatorServiceRegistry implements OperatorServiceRegistry {

    private final ConcurrentMap<String, OperatorService> services = new ConcurrentHashMap<>();

    @Override
    public Optional<OperatorService> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(services.get(id));
    }

    @Override
    public List<OperatorService> findAll() {
        return List.copyOf(services.values());
    }

    @Override
    public void register(OperatorService service) {
        Objects.requireNonNull(service, "service must not be null");
        if (service.id() == null || service.id().isBlank()) {
            throw new IllegalArgumentException("service id must not be blank");
        }

        OperatorService previous = services.putIfAbsent(service.id(), service);
        if (previous != null) {
            throw new IllegalStateException("Operator service already registered: " + service.id());
        }
    }

    @Override
    public Optional<OperatorService> unregister(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(services.remove(id));
    }
}

package tech.kayys.wayang.operator.runtime.diagnostics;

import tech.kayys.wayang.spi.diagnostics.DiagnosticComponentType;
import tech.kayys.wayang.spi.diagnostics.DiagnosticProvider;
import tech.kayys.wayang.spi.diagnostics.DiagnosticProviderRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultDiagnosticProviderRegistry implements DiagnosticProviderRegistry {

    private final ConcurrentMap<String, DiagnosticProvider> providers = new ConcurrentHashMap<>();

    public void register(DiagnosticProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        if (provider.id() == null || provider.id().isBlank()) {
            throw new IllegalArgumentException("provider id must not be blank");
        }
        providers.put(provider.id(), provider);
    }

    public Optional<DiagnosticProvider> unregister(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.remove(id));
    }

    @Override
    public Optional<DiagnosticProvider> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(id));
    }

    @Override
    public List<DiagnosticProvider> findAll() {
        return List.copyOf(providers.values());
    }

    @Override
    public List<DiagnosticProvider> findByType(DiagnosticComponentType type) {
        if (type == null) {
            return List.of();
        }
        return providers.values().stream()
                .filter(p -> p.componentType() == type)
                .toList();
    }
}

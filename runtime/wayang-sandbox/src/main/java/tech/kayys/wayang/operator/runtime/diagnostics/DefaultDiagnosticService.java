package tech.kayys.wayang.operator.runtime.diagnostics;

import tech.kayys.wayang.spi.diagnostics.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultDiagnosticService implements DiagnosticService {

    private final DiagnosticProviderRegistry registry;

    public DefaultDiagnosticService(DiagnosticProviderRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public DiagnosticResult diagnose(DiagnosticContext context, DiagnosticComponent component) throws Exception {
        if (component == null) {
            throw new IllegalArgumentException("component must not be null");
        }

        List<DiagnosticProvider> providers = registry.findByType(component.type());
        for (DiagnosticProvider provider : providers) {
            DiagnosticResult result = diagnoseProvider(provider, context);
            if (result.component().id().equals(component.id())) {
                return result;
            }
        }

        // Check if there is a provider whose ID matches component ID
        var byId = registry.find(component.id());
        if (byId.isPresent()) {
            return diagnoseProvider(byId.get(), context);
        }

        throw new IllegalArgumentException("No diagnostic provider for " + component.type() + "/" + component.id());
    }

    @Override
    public List<DiagnosticResult> diagnoseAll(DiagnosticContext context) throws Exception {
        return registry.findAll().stream()
                .map(provider -> diagnoseProvider(provider, context))
                .toList();
    }

    @Override
    public List<DiagnosticResult> diagnoseType(DiagnosticContext context, DiagnosticComponentType type) throws Exception {
        if (type == null) {
            return List.of();
        }
        return registry.findByType(type).stream()
                .map(provider -> diagnoseProvider(provider, context))
                .toList();
    }

    public DiagnosticReport generateReport(DiagnosticContext context) throws Exception {
        List<DiagnosticResult> results = diagnoseAll(context);
        DiagnosticStatus overall = aggregate(results);
        return new DiagnosticReport(Instant.now(), overall, results, Map.of());
    }

    public DiagnosticStatus aggregate(List<DiagnosticResult> results) {
        if (results == null || results.isEmpty()) {
            return DiagnosticStatus.UNKNOWN;
        }

        boolean hasDegraded = false;
        boolean hasKnown = false;

        for (DiagnosticResult result : results) {
            switch (result.status()) {
                case UNHEALTHY -> {
                    return DiagnosticStatus.UNHEALTHY;
                }
                case DEGRADED -> {
                    hasDegraded = true;
                    hasKnown = true;
                }
                case HEALTHY -> hasKnown = true;
                case UNKNOWN -> {
                }
            }
        }

        if (hasDegraded) {
            return DiagnosticStatus.DEGRADED;
        }
        if (hasKnown) {
            return DiagnosticStatus.HEALTHY;
        }
        return DiagnosticStatus.UNKNOWN;
    }

    private DiagnosticResult diagnoseProvider(DiagnosticProvider provider, DiagnosticContext context) {
        try {
            return provider.diagnose(context);
        } catch (Exception e) {
            return new DiagnosticResult(
                    new DiagnosticComponent(provider.componentType(), provider.id()),
                    DiagnosticStatus.UNKNOWN,
                    Instant.now(),
                    "Diagnostic provider failed: " + e.getMessage(),
                    List.of(new DiagnosticIssue("PROVIDER_FAILURE", DiagnosticSeverity.ERROR, e.getMessage() != null ? e.getMessage() : "Unknown error", Map.of())),
                    Map.of("providerFailure", true)
            );
        }
    }
}

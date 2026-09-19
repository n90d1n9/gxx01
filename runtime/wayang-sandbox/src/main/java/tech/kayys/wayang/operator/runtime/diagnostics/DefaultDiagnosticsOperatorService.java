package tech.kayys.wayang.operator.runtime.diagnostics;

import tech.kayys.wayang.operator.runtime.PermissiveOperatorAuthorization;
import tech.kayys.wayang.spi.diagnostics.*;
import tech.kayys.wayang.spi.operator.OperatorAuthorization;
import tech.kayys.wayang.spi.operator.OperatorContext;
import tech.kayys.wayang.spi.operator.OperatorResult;
import tech.kayys.wayang.spi.operator.OperatorService;
import tech.kayys.wayang.spi.operator.diagnostics.DiagnosticsOperatorPermissions;
import tech.kayys.wayang.spi.operator.diagnostics.DiagnosticsOperatorService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultDiagnosticsOperatorService implements DiagnosticsOperatorService, OperatorService {

    public static final String ID = "operator.service.diagnostics";

    private final DefaultDiagnosticService diagnosticService;
    private final OperatorAuthorization authorization;

    public DefaultDiagnosticsOperatorService(DefaultDiagnosticService diagnosticService) {
        this(diagnosticService, new PermissiveOperatorAuthorization());
    }

    public DefaultDiagnosticsOperatorService(
            DefaultDiagnosticService diagnosticService,
            OperatorAuthorization authorization) {
        this.diagnosticService = Objects.requireNonNull(diagnosticService, "diagnosticService must not be null");
        this.authorization = Objects.requireNonNull(authorization, "authorization must not be null");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Diagnostics Operator Service";
    }

    @Override
    public String description() {
        return "Operator control plane for inspecting runtime system diagnostics";
    }

    @Override
    public OperatorResult<DiagnosticReport> diagnoseAll(OperatorContext context) {
        try {
            authorization.require(context, DiagnosticsOperatorPermissions.READ);
        } catch (SecurityException se) {
            return OperatorResult.failure("PERMISSION_DENIED", se.getMessage());
        }

        try {
            DiagnosticContext diagCtx = toDiagnosticContext(context);
            List<DiagnosticResult> results = diagnosticService.diagnoseAll(diagCtx);
            DiagnosticStatus overall = diagnosticService.aggregate(results);
            return OperatorResult.success(new DiagnosticReport(Instant.now(), overall, results, Map.of()));
        } catch (Exception e) {
            return OperatorResult.failure("DIAGNOSTICS_FAILED", e.getMessage());
        }
    }

    @Override
    public OperatorResult<DiagnosticResult> diagnose(OperatorContext context, DiagnosticComponent component) {
        try {
            authorization.require(context, DiagnosticsOperatorPermissions.INSPECT);
        } catch (SecurityException se) {
            return OperatorResult.failure("PERMISSION_DENIED", se.getMessage());
        }

        if (component == null) {
            return OperatorResult.failure("INVALID_ARGUMENT", "Diagnostic component must not be null");
        }

        try {
            return OperatorResult.success(diagnosticService.diagnose(toDiagnosticContext(context), component));
        } catch (Exception e) {
            return OperatorResult.failure("DIAGNOSTIC_FAILED", e.getMessage());
        }
    }

    @Override
    public OperatorResult<List<DiagnosticResult>> diagnoseType(OperatorContext context, DiagnosticComponentType type) {
        try {
            authorization.require(context, DiagnosticsOperatorPermissions.READ);
        } catch (SecurityException se) {
            return OperatorResult.failure("PERMISSION_DENIED", se.getMessage());
        }

        if (type == null) {
            return OperatorResult.failure("INVALID_ARGUMENT", "Component type must not be null");
        }

        try {
            return OperatorResult.success(diagnosticService.diagnoseType(toDiagnosticContext(context), type));
        } catch (Exception e) {
            return OperatorResult.failure("DIAGNOSTIC_QUERY_FAILED", e.getMessage());
        }
    }

    private DiagnosticContext toDiagnosticContext(OperatorContext context) {
        if (context == null) {
            return DiagnosticContext.defaultContext();
        }
        return new DiagnosticContext(
                context.tenantId(),
                context.userId(),
                context.correlationId(),
                Duration.ofSeconds(5),
                context.attributes()
        );
    }
}

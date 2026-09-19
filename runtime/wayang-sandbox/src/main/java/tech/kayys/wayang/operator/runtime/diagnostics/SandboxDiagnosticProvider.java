package tech.kayys.wayang.operator.runtime.diagnostics;

import tech.kayys.wayang.spi.diagnostics.*;
import tech.kayys.wayang.spi.sandbox.Sandbox;
import tech.kayys.wayang.spi.sandbox.SandboxManager;
import tech.kayys.wayang.spi.sandbox.SandboxState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SandboxDiagnosticProvider implements DiagnosticProvider {

    public static final String ID = "sandbox-diagnostic-provider";

    private final SandboxManager sandboxManager;

    public SandboxDiagnosticProvider(SandboxManager sandboxManager) {
        this.sandboxManager = Objects.requireNonNull(sandboxManager, "sandboxManager must not be null");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public DiagnosticComponentType componentType() {
        return DiagnosticComponentType.SANDBOX;
    }

    @Override
    public DiagnosticResult diagnose(DiagnosticContext context) throws Exception {
        List<Sandbox> sandboxes = sandboxManager.list();
        List<DiagnosticIssue> issues = new ArrayList<>();
        DiagnosticStatus status = DiagnosticStatus.HEALTHY;

        for (Sandbox sandbox : sandboxes) {
            if (sandbox.state() == SandboxState.FAILED) {
                issues.add(new DiagnosticIssue(
                        "SANDBOX_FAILED",
                        DiagnosticSeverity.ERROR,
                        "Sandbox " + sandbox.id() + " is in FAILED state",
                        Map.of("sandboxId", sandbox.id())
                ));
                status = DiagnosticStatus.UNHEALTHY;
            }
        }

        return new DiagnosticResult(
                DiagnosticComponent.of(DiagnosticComponentType.SANDBOX, ID),
                status,
                Instant.now(),
                "Sandbox subsystem status: " + status + " (" + sandboxes.size() + " sandboxes active)",
                issues,
                Map.of("totalSandboxes", sandboxes.size())
        );
    }
}

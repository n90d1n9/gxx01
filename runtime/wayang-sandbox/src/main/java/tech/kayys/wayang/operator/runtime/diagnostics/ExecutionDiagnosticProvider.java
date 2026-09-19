package tech.kayys.wayang.operator.runtime.diagnostics;

import tech.kayys.wayang.spi.diagnostics.*;
import tech.kayys.wayang.spi.execution.ExecutionControl;
import tech.kayys.wayang.spi.execution.ExecutionInfo;
import tech.kayys.wayang.spi.execution.ExecutionQuery;
import tech.kayys.wayang.spi.execution.ExecutionState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ExecutionDiagnosticProvider implements DiagnosticProvider {

    public static final String ID = "execution-diagnostic-provider";

    private final ExecutionControl executionControl;

    public ExecutionDiagnosticProvider(ExecutionControl executionControl) {
        this.executionControl = Objects.requireNonNull(executionControl, "executionControl must not be null");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public DiagnosticComponentType componentType() {
        return DiagnosticComponentType.EXECUTION;
    }

    @Override
    public DiagnosticResult diagnose(DiagnosticContext context) throws Exception {
        List<ExecutionInfo> executions = executionControl.list(ExecutionQuery.all());
        List<DiagnosticIssue> issues = new ArrayList<>();
        DiagnosticStatus status = DiagnosticStatus.HEALTHY;

        for (ExecutionInfo exec : executions) {
            if (exec.state() == ExecutionState.FAILED) {
                issues.add(new DiagnosticIssue(
                        "EXECUTION_FAILED",
                        DiagnosticSeverity.WARNING,
                        "Execution " + exec.executionId() + " failed: " + exec.failureMessageOptional().orElse("No message"),
                        Map.of("executionId", exec.executionId())
                ));
                if (status != DiagnosticStatus.UNHEALTHY) {
                    status = DiagnosticStatus.DEGRADED;
                }
            }
        }

        return new DiagnosticResult(
                DiagnosticComponent.of(DiagnosticComponentType.EXECUTION, ID),
                status,
                Instant.now(),
                "Execution subsystem status: " + status + " (" + executions.size() + " executions tracked)",
                issues,
                Map.of("totalExecutions", executions.size())
        );
    }
}

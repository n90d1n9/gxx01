package tech.kayys.wayang.operator.runtime.sandbox;

import tech.kayys.wayang.spi.operator.OperatorContext;
import tech.kayys.wayang.spi.operator.OperatorResult;
import tech.kayys.wayang.spi.operator.OperatorService;
import tech.kayys.wayang.spi.operator.sandbox.SandboxOperatorService;
import tech.kayys.wayang.spi.sandbox.Sandbox;
import tech.kayys.wayang.spi.sandbox.SandboxManager;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DefaultSandboxOperatorService implements SandboxOperatorService, OperatorService {

    public static final String ID = "operator.service.sandbox";

    private final SandboxManager sandboxManager;

    public DefaultSandboxOperatorService(SandboxManager sandboxManager) {
        this.sandboxManager = Objects.requireNonNull(sandboxManager, "sandboxManager must not be null");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Sandbox Operator Service";
    }

    @Override
    public String description() {
        return "Operator control plane for managing and inspecting Wayang execution sandboxes";
    }

    @Override
    public OperatorResult<List<Sandbox>> list(OperatorContext context) {
        try {
            List<Sandbox> sandboxes = sandboxManager.list();
            if (context != null && context.tenantId() != null && !context.tenantId().isBlank()) {
                sandboxes = sandboxes.stream()
                        .filter(s -> s.context().tenantId().map(t -> t.equals(context.tenantId())).orElse(false))
                        .toList();
            }
            return OperatorResult.success(sandboxes);
        } catch (Exception e) {
            return OperatorResult.failure("SANDBOX_LIST_FAILED", e.getMessage());
        }
    }

    @Override
    public OperatorResult<Sandbox> inspect(OperatorContext context, String sandboxId) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return OperatorResult.failure("INVALID_ARGUMENT", "sandboxId must not be blank");
        }
        Optional<Sandbox> sb = sandboxManager.find(sandboxId);
        if (sb.isEmpty()) {
            return OperatorResult.failure("SANDBOX_NOT_FOUND", "No sandbox found with ID: " + sandboxId);
        }
        Sandbox s = sb.get();
        if (context != null && context.tenantId() != null && !context.tenantId().isBlank()) {
            if (!s.context().tenantId().map(t -> t.equals(context.tenantId())).orElse(false)) {
                return OperatorResult.failure("PERMISSION_DENIED", "Sandbox does not belong to tenant: " + context.tenantId());
            }
        }
        return OperatorResult.success(s);
    }

    @Override
    public OperatorResult<Void> destroy(OperatorContext context, String sandboxId) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return OperatorResult.failure("INVALID_ARGUMENT", "sandboxId must not be blank");
        }
        try {
            sandboxManager.destroy(sandboxId);
            return OperatorResult.success(null);
        } catch (Exception e) {
            return OperatorResult.failure("SANDBOX_DESTROY_FAILED", e.getMessage());
        }
    }
}

package tech.kayys.wayang.operator.runtime.execution;

import tech.kayys.wayang.spi.execution.ExecutionControl;
import tech.kayys.wayang.spi.execution.ExecutionInfo;
import tech.kayys.wayang.spi.execution.ExecutionQuery;
import tech.kayys.wayang.spi.execution.ExecutionState;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryExecutionControl implements ExecutionControl {

    private final ConcurrentMap<String, ExecutionInfo> executions = new ConcurrentHashMap<>();

    public void register(ExecutionInfo info) {
        Objects.requireNonNull(info, "info must not be null");
        executions.put(info.executionId(), info);
    }

    @Override
    public Optional<ExecutionInfo> find(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(executions.get(executionId));
    }

    @Override
    public List<ExecutionInfo> list(ExecutionQuery query) {
        if (query == null) {
            return List.copyOf(executions.values());
        }

        return executions.values().stream()
                .filter(e -> query.tenantId() == null || Objects.equals(query.tenantId(), e.tenantId()))
                .filter(e -> query.userId() == null || Objects.equals(query.userId(), e.userId()))
                .filter(e -> query.agentId() == null || Objects.equals(query.agentId(), e.agentId()))
                .filter(e -> query.workflowId() == null || Objects.equals(query.workflowId(), e.workflowId()))
                .filter(e -> query.states() == null || query.states().isEmpty() || query.states().contains(e.state()))
                .filter(e -> query.createdAfter() == null || (e.createdAt() != null && e.createdAt().isAfter(query.createdAfter())))
                .filter(e -> query.createdBefore() == null || (e.createdAt() != null && e.createdAt().isBefore(query.createdBefore())))
                .limit(query.limit() > 0 ? query.limit() : 100)
                .toList();
    }

    @Override
    public void pause(String executionId) throws Exception {
        ExecutionInfo current = requireExecution(executionId);
        if (current.state() != ExecutionState.RUNNING && current.state() != ExecutionState.QUEUED) {
            throw new IllegalStateException("Cannot pause execution in state: " + current.state());
        }
        ExecutionInfo updated = updateState(current, ExecutionState.PAUSED);
        executions.put(executionId, updated);
    }

    @Override
    public void resume(String executionId) throws Exception {
        ExecutionInfo current = requireExecution(executionId);
        if (current.state() != ExecutionState.PAUSED) {
            throw new IllegalStateException("Cannot resume execution in state: " + current.state());
        }
        ExecutionInfo updated = updateState(current, ExecutionState.RUNNING);
        executions.put(executionId, updated);
    }

    @Override
    public void cancel(String executionId) throws Exception {
        ExecutionInfo current = requireExecution(executionId);
        if (current.state() == ExecutionState.COMPLETED || current.state() == ExecutionState.CANCELLED) {
            throw new IllegalStateException("Cannot cancel execution in terminal state: " + current.state());
        }
        ExecutionInfo updated = updateState(current, ExecutionState.CANCELLED);
        executions.put(executionId, updated);
    }

    @Override
    public void retry(String executionId) throws Exception {
        ExecutionInfo current = requireExecution(executionId);
        if (current.state() != ExecutionState.FAILED && current.state() != ExecutionState.CANCELLED) {
            throw new IllegalStateException("Cannot retry execution in state: " + current.state());
        }
        ExecutionInfo updated = updateState(current, ExecutionState.QUEUED);
        executions.put(executionId, updated);
    }

    private ExecutionInfo requireExecution(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        ExecutionInfo info = executions.get(executionId);
        if (info == null) {
            throw new NoSuchElementException("Execution not found: " + executionId);
        }
        return info;
    }

    private ExecutionInfo updateState(ExecutionInfo current, ExecutionState newState) {
        Instant completedAt = (newState == ExecutionState.COMPLETED || newState == ExecutionState.CANCELLED || newState == ExecutionState.FAILED)
                ? Instant.now()
                : current.completedAt();
        return new ExecutionInfo(
                current.executionId(),
                current.tenantId(),
                current.userId(),
                current.agentId(),
                current.workflowId(),
                newState,
                current.createdAt(),
                current.startedAt(),
                completedAt,
                Instant.now(),
                current.correlationId(),
                current.failureCode(),
                current.failureMessage(),
                current.attributes()
        );
    }
}

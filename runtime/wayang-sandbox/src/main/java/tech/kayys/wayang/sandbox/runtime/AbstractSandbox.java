package tech.kayys.wayang.sandbox.runtime;

import tech.kayys.wayang.spi.sandbox.*;

import java.util.Objects;

/**
 * Base sandbox implementation encapsulating lifecycle state transitions,
 * synchronization, and error handling according to the sandbox state machine.
 */
public abstract class AbstractSandbox implements Sandbox, SandboxLifecycle {

    private final SandboxDescriptor descriptor;
    private final SandboxContext context;

    private volatile SandboxState state = SandboxState.CREATED;

    protected AbstractSandbox(
            SandboxDescriptor descriptor,
            SandboxContext context) {

        this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
    }

    @Override
    public final SandboxDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public final SandboxContext context() {
        return context;
    }

    @Override
    public final SandboxState state() {
        return state;
    }

    @Override
    public final synchronized void start() throws Exception {
        SandboxState current = state;

        if (current == SandboxState.RUNNING) {
            return;
        }

        SandboxStateMachine.requireTransition(current, SandboxState.STARTING);
        transitionTo(SandboxState.STARTING);

        try {
            doStart();
            transitionTo(SandboxState.RUNNING);
        } catch (Exception e) {
            transitionTo(SandboxState.FAILED);
            try {
                doStartFailureCleanup();
            } catch (Exception cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    @Override
    public final synchronized void stop() throws Exception {
        SandboxState current = state;

        if (current == SandboxState.STOPPED || current == SandboxState.DESTROYED) {
            return;
        }

        SandboxStateMachine.requireTransition(current, SandboxState.STOPPING);
        transitionTo(SandboxState.STOPPING);

        try {
            doStop();
            transitionTo(SandboxState.STOPPED);
        } catch (Exception e) {
            transitionTo(SandboxState.FAILED);
            throw e;
        }
    }

    @Override
    public final synchronized void destroy() throws Exception {
        SandboxState current = state;

        if (current == SandboxState.DESTROYED) {
            return;
        }

        if (current == SandboxState.RUNNING || current == SandboxState.STARTING || current == SandboxState.STOPPING) {
            try {
                stop();
            } catch (Exception ignored) {
                // Continue destruction even if graceful stop fails
            }
        }

        current = state;
        SandboxStateMachine.requireTransition(current, SandboxState.DESTROYED);

        try {
            doDestroy();
        } finally {
            state = SandboxState.DESTROYED;
        }
    }

    protected abstract void doStart() throws Exception;

    protected abstract void doStop() throws Exception;

    protected abstract void doDestroy() throws Exception;

    protected void doStartFailureCleanup() throws Exception {
        // Subclasses may override to perform cleanup on startup failure
    }

    protected final void transitionTo(SandboxState next) {
        SandboxStateMachine.requireTransition(state, next);
        state = next;
    }
}

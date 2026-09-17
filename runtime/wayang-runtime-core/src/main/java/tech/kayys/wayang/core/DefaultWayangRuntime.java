package tech.kayys.wayang.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import tech.kayys.wayang.agent.AgentRequest;
import tech.kayys.wayang.agent.AgentResponse;
import tech.kayys.wayang.core.runtime.WayangRuntime;
import tech.kayys.wayang.execution.ExecutionContext;
import tech.kayys.wayang.execution.ExecutionEngine;
import tech.kayys.wayang.execution.ExecutionResult;
import tech.kayys.wayang.execution.SimpleExecutionContext;
import tech.kayys.wayang.extension.Id;
import tech.kayys.wayang.extension.Metadata;
import tech.kayys.wayang.identity.ResourceId;
import tech.kayys.wayang.resource.BaseResource;
import tech.kayys.wayang.resource.ResourceType;

/**
 * Default CDI implementation of {@link WayangRuntime}.
 *
 * <p>This is the top-level entry point for the Wayang execution stack.
 * The full call chain is:
 * <pre>
 *   DefaultWayangRuntime
 *     → DefaultExecutionEngine
 *       → AgentExecutionService
 *         → DefaultAgentExecution
 *           → ReActAgent (real loop: model → tool pipeline → checkpoint → model …)
 *             → DefaultAgentToolExecutor (CB + retry + timeout)
 *               → Tool.execute()
 * </pre>
 *
 * <p>The response returned is the <em>actual</em> agent-generated content,
 * not a hard-coded "Execution completed successfully" string.
 */
@ApplicationScoped
public class DefaultWayangRuntime extends BaseResource implements WayangRuntime {

    private static final Logger LOG = Logger.getLogger(DefaultWayangRuntime.class.getName());

    private static final Executor RUNTIME_POOL = Executors.newVirtualThreadPerTaskExecutor();

    public DefaultWayangRuntime() {
        super(
            new ResourceId.CustomId(Id.random(), new ResourceType.Execution()),
            Metadata.builder()
                .name("DefaultWayangRuntime")
                .description("Top-level Wayang agent execution runtime")
                .build()
        );
    }

    @Inject
    ExecutionEngine executionEngine;

    public void setExecutionEngine(ExecutionEngine executionEngine) {
        this.executionEngine = executionEngine;
    }

    // -------------------------------------------------------------------------
    // WayangRuntime
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<AgentResponse> executeAsync(AgentDefinition agent, AgentRequest request) {
        return CompletableFuture.supplyAsync(() -> executeSync(agent, request), RUNTIME_POOL);
    }

    @Override
    public boolean supports(AgentDefinition agent) {
        // This runtime supports all agent definitions by default.
        // Future: check agent.type() and route to specialised runtimes.
        return agent != null;
    }

    // -------------------------------------------------------------------------
    // Synchronous path (used internally; also convenient for testing)
    // -------------------------------------------------------------------------

    public AgentResponse executeSync(AgentDefinition agent, AgentRequest request) {
        try {
            ExecutionContext context = SimpleExecutionContext.fromRequest(request);
            ExecutionResult result   = executionEngine.executeAgent(agent, context);

            // Map ExecutionResult → AgentResponse, carrying the real content.
            if (result.getResult() instanceof AgentResponse r) {
                return r;
            }

            // Fallback mapping for non-AgentResponse result objects.
            String content = result.getResult() != null
                ? result.getResult().toString()
                : (result.getErrorMessage().orElse("No response from agent."));

            return AgentResponse.builder()
                .id(result.getExecutionId() != null ? result.getExecutionId().toString() : "")
                .success(result.isSuccess())
                .content(content)
                .error(result.getErrorMessage().orElse(null))
                .build();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Runtime execution failed", e);
            return AgentResponse.builder()
                .success(false)
                .error("Runtime error: " + e.getMessage())
                .build();
        }
    }
}

package tech.kayys.wayang.execution;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import tech.kayys.wayang.agent.AgentRequest;
import tech.kayys.wayang.agent.AgentResponse;
import tech.kayys.wayang.core.AgentDefinition;
import tech.kayys.wayang.extension.Id;
import tech.kayys.wayang.extension.Metadata;
import tech.kayys.wayang.identity.ResourceId;
import tech.kayys.wayang.resource.BaseResource;
import tech.kayys.wayang.resource.ResourceType;
import tech.kayys.wayang.skill.spi.Skill;
import tech.kayys.wayang.skill.spi.SkillDefinition;
import tech.kayys.wayang.skill.spi.SkillRegistry;
import tech.kayys.wayang.skill.spi.SkillResult;
import tech.kayys.wayang.workflow.WorkflowDefinition;
import tech.kayys.wayang.workflow.WorkflowEngine;
import tech.kayys.wayang.workflow.WorkflowResult;

/**
 * Default CDI implementation of {@link ExecutionEngine}.
 *
 * <p>Delegates agent execution to {@link AgentExecutionService}, which wires the
 * full {@code ReActAgent} loop (provider → tool pipeline → checkpoint).
 * The result is mapped to {@link ExecutionResult} carrying the <em>actual</em>
 * agent-generated content — not a hard-coded success string.
 *
 * <p>Also provides pluggable delegation to {@link WorkflowEngine} and {@link SkillRegistry}
 * when discoverable in the CDI container.</p>
 */
@ApplicationScoped
public class DefaultExecutionEngine extends BaseResource implements ExecutionEngine {

    private static final Logger LOG = Logger.getLogger(DefaultExecutionEngine.class.getName());

    public DefaultExecutionEngine() {
        super(
            new ResourceId.CustomId(Id.random(), new ResourceType.Execution()),
            Metadata.builder()
                .name("DefaultExecutionEngine")
                .description("Default CDI implementation of ExecutionEngine")
                .build()
        );
    }

    @Inject
    AgentExecutionService executionService;

    @Inject
    Instance<WorkflowEngine> workflowEngineInstances;

    @Inject
    Instance<SkillRegistry> skillRegistryInstances;

    // -------------------------------------------------------------------------
    // ExecutionEngine
    // -------------------------------------------------------------------------

    @Override
    public ExecutionResult executeAgent(AgentDefinition agent, ExecutionContext context) {
        Instant start = Instant.now();
        String executionId = context != null ? context.executionId().toString() : UUID.randomUUID().toString();

        try {
            AgentRequest request = extractRequest(context);
            ExecutionBudget budget = ExecutionBudget.defaults();

            AgentExecution execution = executionService.create(agent, request, budget);
            AgentResponse response   = execution.executeSync();

            Instant end = Instant.now();
            return ExecutionResult.builder()
                .executionId(UUID.fromString(execution.id()))
                .status(response.success() ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED)
                .result(response)            // carries actual agent content
                .startTime(start)
                .endTime(end)
                .errorMessage(response.success() ? null : response.error())
                .build();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Agent execution failed [" + executionId + "]", e);
            return ExecutionResult.builder()
                .executionId(uuidOrRandom(executionId))
                .status(ExecutionStatus.FAILED)
                .startTime(start)
                .endTime(Instant.now())
                .errorMessage(e.getMessage())
                .build();
        }
    }

    @Override
    public ExecutionResult executeWorkflow(WorkflowDefinition workflow, ExecutionContext context) {
        if (workflowEngineInstances != null && workflowEngineInstances.isResolvable()) {
            Instant start = Instant.now();
            try {
                Map<String, Object> ctxMap = extractVariables(context);
                WorkflowResult result = workflowEngineInstances.get().execute(workflow, ctxMap);
                UUID execId = result.id() != null ? uuidOrRandom(result.id()) : UUID.randomUUID();
                return ExecutionResult.builder()
                    .executionId(execId)
                    .status("COMPLETED".equalsIgnoreCase(result.status()) ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED)
                    .result(result)
                    .startTime(start)
                    .endTime(Instant.ofEpochMilli(result.endTime() > 0 ? result.endTime() : System.currentTimeMillis()))
                    .errorMessage(result.error())
                    .build();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Workflow execution failed", e);
                return ExecutionResult.failure(UUID.randomUUID(), e.getMessage());
            }
        }
        return ExecutionResult.failure(UUID.randomUUID(), "No WorkflowEngine provider is registered on the runtime classpath.");
    }

    @Override
    public ExecutionResult executeSkill(SkillDefinition skill, ExecutionContext context) {
        if (skillRegistryInstances != null && skillRegistryInstances.isResolvable() && skill != null) {
            Instant start = Instant.now();
            try {
                String skillId = skill.id() != null ? skill.id().toString() : "";
                Optional<Skill> skillOpt = skillRegistryInstances.get().findById(skillId);
                if (skillOpt.isPresent()) {
                    Map<String, Object> inputs = extractVariables(context);
                    SkillResult res = skillOpt.get().execute(() -> inputs);
                    return ExecutionResult.builder()
                        .executionId(UUID.randomUUID())
                        .status(res.isSuccess() ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED)
                        .result(res.getOutputs())
                        .startTime(start)
                        .endTime(Instant.now())
                        .build();
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Skill execution failed", e);
                return ExecutionResult.failure(UUID.randomUUID(), e.getMessage());
            }
        }
        return ExecutionResult.failure(UUID.randomUUID(), "Skill execution not yet implemented or SkillRegistry not resolvable.");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AgentRequest extractRequest(ExecutionContext context) {
        if (context == null) {
            return AgentRequest.builder().content("").build();
        }
        if (context instanceof SimpleExecutionContext sec) {
            return AgentRequest.builder()
                .content(sec.prompt().orElse(""))
                .build();
        }
        try {
            Optional<String> promptOpt = context.variables()
                .get(SimpleExecutionContext.PROMPT_KEY);
            if (promptOpt.isPresent()) {
                return AgentRequest.builder().content(promptOpt.get()).build();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return AgentRequest.builder().content("").build();
    }

    private Map<String, Object> extractVariables(ExecutionContext context) {
        if (context == null || context.variables() == null) {
            return Map.of();
        }
        try {
            VariableSnapshot snapshot = context.variables().snapshot();
            if (snapshot != null && snapshot.getValues() != null) {
                return snapshot.getValues();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Map.of();
    }

    private UUID uuidOrRandom(String id) {
        try {
            return UUID.fromString(id);
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }
}

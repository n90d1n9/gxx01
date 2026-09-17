package tech.kayys.wayang.agent.core.memory;

import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory implementation of {@link AgentMemoryService}.
 * Provides thread-safe storage of agent interactions and builds context prompts.
 */
public class DefaultAgentMemoryService implements AgentMemoryService {

    public record Interaction(
            String agentId,
            String sessionId,
            String userId,
            String userInput,
            String response,
            Instant timestamp
    ) {}

    private final Map<String, Deque<Interaction>> memoryStore = new ConcurrentHashMap<>();
    private final int maxInteractionsPerAgent;

    public DefaultAgentMemoryService() {
        this(100);
    }

    public DefaultAgentMemoryService(int maxInteractionsPerAgent) {
        this.maxInteractionsPerAgent = maxInteractionsPerAgent;
    }

    @Override
    public Uni<Void> storeInteraction(String agentId, String sessionId, String userId,
                                      String userInput, String response) {
        return Uni.createFrom().item(() -> {
            if (agentId == null || agentId.isBlank()) {
                return null;
            }
            Interaction interaction = new Interaction(
                    agentId,
                    sessionId,
                    userId,
                    userInput != null ? userInput : "",
                    response != null ? response : "",
                    Instant.now()
            );

            memoryStore.compute(agentId, (k, deque) -> {
                if (deque == null) {
                    deque = new ArrayDeque<>();
                }
                synchronized (deque) {
                    if (deque.size() >= maxInteractionsPerAgent) {
                        deque.pollFirst();
                    }
                    deque.addLast(interaction);
                }
                return deque;
            });
            return null;
        });
    }

    @Override
    public Uni<String> getContextPrompt(String agentId, int limit) {
        return Uni.createFrom().item(() -> {
            if (agentId == null) {
                return "";
            }
            Deque<Interaction> deque = memoryStore.get(agentId);
            if (deque == null || deque.isEmpty()) {
                return "";
            }

            List<Interaction> snapshot;
            synchronized (deque) {
                snapshot = new ArrayList<>(deque);
            }

            int n = Math.min(limit > 0 ? limit : 10, snapshot.size());
            List<Interaction> subList = snapshot.subList(snapshot.size() - n, snapshot.size());

            StringBuilder sb = new StringBuilder("### Previous Interactions:\n");
            for (Interaction item : subList) {
                sb.append("User: ").append(item.userInput()).append("\n");
                sb.append("Agent: ").append(item.response()).append("\n\n");
            }
            return sb.toString().stripTrailing();
        });
    }
}

package tech.kayys.wayang.cli;

import tech.kayys.wayang.tools.spi.Tool;
import tech.kayys.wayang.tools.spi.ToolContext;
import tech.kayys.wayang.tools.spi.ToolResult;
import tech.kayys.wayang.memory.model.Memory;
import tech.kayys.wayang.memory.service.VectorMemoryStore;
import tech.kayys.wayang.memory.service.InMemoryVectorStore;
import tech.kayys.wayang.vector.faiss.FaissVectorStore;

import java.io.File;
import java.util.List;
import java.util.Map;

public class WayangMemoryAgentTools {

    private static VectorMemoryStore getStore() {
        return new InMemoryVectorStore();
    }

    public static List<Tool> getTools() {
        return List.of(new QueryMemoryTool(), new StoreMemoryTool());
    }

    public static class QueryMemoryTool implements Tool {
        @Override public String id() { return "memory_query"; }
        @Override public String name() { return "Query Memory"; }
        @Override public String description() { return "Query the long-term categorical memory using a category filter. Categories: Instructions, Identity, Career, Projects, Preferences. Use this tool to remember past details about the user or project context."; }
        @Override public Map<String, Object> inputSchema() {
            return Map.of(
                "type", "object",
                "properties", Map.of(
                    "category", Map.of("type", "string", "description", "Category to query, e.g., 'Instructions', 'Projects'")
                ),
                "required", List.of()
            );
        }
        @Override public ToolResult execute(Map<String, Object> params, ToolContext context) {
            try {
                String category = params != null && params.containsKey("category") ? (String) params.get("category") : null;
                VectorMemoryStore store = getStore();
                Map<String, Object> filter = category != null && !category.isBlank() ? Map.of("category", category) : Map.of();
                List<Memory> results = store.searchByFilter(filter).await().indefinitely();
                
                if (results.isEmpty()) return ToolResult.success("No memories found.");
                
                StringBuilder sb = new StringBuilder("Memories found:\n");
                for (Memory m : results) {
                    sb.append("- [").append(m.getMetadata().getOrDefault("date", "unknown")).append("] ")
                      .append(m.getContent()).append("\n");
                }
                return ToolResult.success(sb.toString());
            } catch (Exception e) {
                return ToolResult.error("Failed to query memory: " + e.getMessage());
            }
        }
    }

    public static class StoreMemoryTool implements Tool {
        @Override public String id() { return "memory_store"; }
        @Override public String name() { return "Store Memory"; }
        @Override public String description() { return "Store a new memory about the user or project context. Categories should be one of: Instructions, Identity, Career, Projects, Preferences."; }
        @Override public Map<String, Object> inputSchema() {
            return Map.of(
                "type", "object",
                "properties", Map.of(
                    "category", Map.of("type", "string", "description", "Category, e.g., 'Instructions', 'Projects'"),
                    "content", Map.of("type", "string", "description", "The memory content to store")
                ),
                "required", List.of("category", "content")
            );
        }
        @Override public ToolResult execute(Map<String, Object> params, ToolContext context) {
            try {
                String category = (String) params.get("category");
                String content = (String) params.get("content");
                if (category == null || content == null) return ToolResult.error("Missing category or content");
                
                VectorMemoryStore store = getStore();
                Memory mem = Memory.builder()
                        .content(content)
                        .type(tech.kayys.wayang.memory.model.MemoryType.SEMANTIC)
                        .metadata(Map.of("category", category, "date", java.time.LocalDate.now().toString()))
                        .build();
                store.store(mem).await().indefinitely();
                return ToolResult.success("Stored memory successfully in category " + category);
            } catch (Exception e) {
                return ToolResult.error("Failed to store memory: " + e.getMessage());
            }
        }
    }
}

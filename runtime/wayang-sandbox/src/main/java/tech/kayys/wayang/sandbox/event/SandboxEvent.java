package tech.kayys.wayang.sandbox.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record SandboxEvent(
        String eventId,
        SandboxEventType type,
        Instant timestamp,
        String sandboxId,
        String executionId,
        Map<String, Object> attributes
) {
    public SandboxEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(sandboxId, "sandboxId must not be null");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static SandboxEvent of(SandboxEventType type, String sandboxId, String executionId) {
        return new SandboxEvent(
                java.util.UUID.randomUUID().toString(),
                type,
                Instant.now(),
                sandboxId,
                executionId,
                Map.of()
        );
    }
}

package tech.kayys.wayang.memory.spi;

import io.smallrye.mutiny.Uni;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Optional;

/**
 * Interface for Direct (Native/Zero-Copy) Agent Context management.
 * Interacts directly with off-heap native memory
 * to ensure that agent contexts (e.g. KV caches or large context windows)
 * are natively available for the FFM pipeline without Java String serialization.
 */
public interface DirectAgentContext {

    /**
     * Retrieves the entire agent context window natively.
     * The segment is constrained to the lifecycle of the provided Arena.
     *
     * @param agentId The unique agent identifier
     * @param arena   The FFM Arena governing the memory lifecycle
     * @return Uni resolving to an Optional MemorySegment
     */
    Uni<Optional<MemorySegment>> getNativeContext(String agentId, Arena arena);

    /**
     * Appends a new interaction to the agent's native context block.
     *
     * @param agentId        The unique agent identifier
     * @param newInteraction The raw String interaction to append
     * @return Uni resolving when append is complete
     */
    Uni<Void> appendToContext(String agentId, String newInteraction);

    /**
     * Replaces the entire native context with a new Segment.
     * Useful when the training engine (Tafkir) updates the KV cache natively.
     *
     * @param agentId        The unique agent identifier
     * @param contextSegment The native segment containing the new context
     * @return Uni resolving when write is complete
     */
    Uni<Void> replaceContext(String agentId, MemorySegment contextSegment);
}

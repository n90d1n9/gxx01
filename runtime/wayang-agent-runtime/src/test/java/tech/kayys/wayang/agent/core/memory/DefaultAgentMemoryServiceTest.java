package tech.kayys.wayang.agent.core.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAgentMemoryServiceTest {

    private DefaultAgentMemoryService memoryService;

    @BeforeEach
    void setUp() {
        memoryService = new DefaultAgentMemoryService(3);
    }

    @Test
    void testStoreAndRetrieveContextPrompt() {
        memoryService.storeInteraction("agent-1", "s1", "u1", "Hello", "Hi there!")
                .await().atMost(Duration.ofSeconds(1));
        memoryService.storeInteraction("agent-1", "s1", "u1", "What is 2+2?", "4")
                .await().atMost(Duration.ofSeconds(1));

        String prompt = memoryService.getContextPrompt("agent-1", 5)
                .await().atMost(Duration.ofSeconds(1));

        assertNotNull(prompt);
        assertTrue(prompt.contains("User: Hello"));
        assertTrue(prompt.contains("Agent: Hi there!"));
        assertTrue(prompt.contains("User: What is 2+2?"));
        assertTrue(prompt.contains("Agent: 4"));
    }

    @Test
    void testSlidingWindowEviction() {
        memoryService.storeInteraction("agent-1", "s1", "u1", "msg 1", "reply 1")
                .await().atMost(Duration.ofSeconds(1));
        memoryService.storeInteraction("agent-1", "s1", "u1", "msg 2", "reply 2")
                .await().atMost(Duration.ofSeconds(1));
        memoryService.storeInteraction("agent-1", "s1", "u1", "msg 3", "reply 3")
                .await().atMost(Duration.ofSeconds(1));
        memoryService.storeInteraction("agent-1", "s1", "u1", "msg 4", "reply 4")
                .await().atMost(Duration.ofSeconds(1));

        String prompt = memoryService.getContextPrompt("agent-1", 10)
                .await().atMost(Duration.ofSeconds(1));

        assertFalse(prompt.contains("msg 1"), "Oldest interaction should have been evicted");
        assertTrue(prompt.contains("msg 2"));
        assertTrue(prompt.contains("msg 3"));
        assertTrue(prompt.contains("msg 4"));
    }

    @Test
    void testEmptyMemory() {
        String prompt = memoryService.getContextPrompt("unknown-agent", 5)
                .await().atMost(Duration.ofSeconds(1));
        assertEquals("", prompt);
    }
}

package tech.kayys.wayang.sandbox.resource;

import org.junit.jupiter.api.Test;
import tech.kayys.wayang.extension.Version;
import tech.kayys.wayang.spi.sandbox.*;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourceLimitTest {

    @Test
    void testRequirementsExtraction() {
        SandboxLimits limits = new SandboxLimits(1000L, 2048L, -1L, -1L, Duration.ofSeconds(10), 10000L, -1L);
        Set<ResourceLimitFeature> reqs = ResourceLimitRequirements.from(limits);

        assertTrue(reqs.contains(ResourceLimitFeature.CPU_LIMIT));
        assertTrue(reqs.contains(ResourceLimitFeature.MEMORY_LIMIT));
        assertFalse(reqs.contains(ResourceLimitFeature.DISK_LIMIT));
        assertTrue(reqs.contains(ResourceLimitFeature.EXECUTION_TIMEOUT));
        assertTrue(reqs.contains(ResourceLimitFeature.OUTPUT_LIMIT));
    }

    @Test
    void testValidatorRejection() {
        SandboxProviderDescriptor desc = new SandboxProviderDescriptor(
                "p1", "Process Provider", "Desc", Version.parse("1.0.0"),
                Set.of(SandboxType.PROCESS),
                Set.of(IsolationFeature.PROCESS_ISOLATION),
                Set.of(),
                Set.of(ResourceLimitFeature.EXECUTION_TIMEOUT, ResourceLimitFeature.OUTPUT_LIMIT),
                Map.of()
        );

        SandboxLimits validLimits = new SandboxLimits(-1L, -1L, -1L, -1L, Duration.ofSeconds(10), 1024L, -1L);
        assertDoesNotThrow(() -> ResourceLimitValidator.validate(validLimits, desc));

        SandboxLimits invalidLimits = new SandboxLimits(1000L, -1L, -1L, -1L, Duration.ofSeconds(10), -1L, -1L);
        assertThrows(UnsupportedResourceLimitException.class, () ->
                ResourceLimitValidator.validate(invalidLimits, desc)
        );
    }
}

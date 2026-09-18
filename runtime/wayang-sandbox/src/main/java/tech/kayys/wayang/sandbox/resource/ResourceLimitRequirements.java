package tech.kayys.wayang.sandbox.resource;

import tech.kayys.wayang.spi.sandbox.ResourceLimitFeature;
import tech.kayys.wayang.spi.sandbox.SandboxLimits;

import java.util.EnumSet;
import java.util.Set;

public final class ResourceLimitRequirements {

    private ResourceLimitRequirements() {
    }

    public static Set<ResourceLimitFeature> from(SandboxLimits limits) {
        if (limits == null) {
            return Set.of();
        }

        EnumSet<ResourceLimitFeature> result = EnumSet.noneOf(ResourceLimitFeature.class);

        if (limits.hasCpuLimit()) {
            result.add(ResourceLimitFeature.CPU_LIMIT);
        }

        if (limits.hasMemoryLimit()) {
            result.add(ResourceLimitFeature.MEMORY_LIMIT);
        }

        if (limits.hasDiskLimit()) {
            result.add(ResourceLimitFeature.DISK_LIMIT);
        }

        if (limits.hasProcessLimit()) {
            result.add(ResourceLimitFeature.PROCESS_LIMIT);
        }

        if (limits.hasExecutionTimeout()) {
            result.add(ResourceLimitFeature.EXECUTION_TIMEOUT);
        }

        if (limits.hasOutputLimit()) {
            result.add(ResourceLimitFeature.OUTPUT_LIMIT);
        }

        if (limits.hasFileCountLimit()) {
            result.add(ResourceLimitFeature.FILE_COUNT_LIMIT);
        }

        return Set.copyOf(result);
    }
}

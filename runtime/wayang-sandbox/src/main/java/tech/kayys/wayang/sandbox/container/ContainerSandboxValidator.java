package tech.kayys.wayang.sandbox.container;

import tech.kayys.wayang.sandbox.resource.ResourceLimitRequirements;
import tech.kayys.wayang.spi.sandbox.*;
import tech.kayys.wayang.spi.sandbox.container.ContainerSandboxRequest;

public final class ContainerSandboxValidator {

    private ContainerSandboxValidator() {
    }

    public static void validate(
            ContainerSandboxRequest request,
            SandboxProviderDescriptor descriptor) {

        if (!descriptor.supportedTypes().contains(SandboxType.CONTAINER)) {
            throw new IllegalStateException("Provider does not support containers");
        }

        var requirements = ResourceLimitRequirements.from(request.limits());
        if (!descriptor.supportsAllResourceLimits(requirements)) {
            throw new IllegalStateException(
                    "Provider cannot enforce required resource limits: " + requirements);
        }
    }
}

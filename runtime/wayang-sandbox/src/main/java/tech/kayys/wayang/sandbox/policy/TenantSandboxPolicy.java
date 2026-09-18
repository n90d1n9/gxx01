package tech.kayys.wayang.sandbox.policy;

import tech.kayys.wayang.spi.sandbox.*;

import java.util.Objects;
import java.util.Set;

public class TenantSandboxPolicy implements SandboxPolicy {

    private final String tenantId;
    private final SandboxLimits maxLimits;
    private final Set<NetworkMode> allowedNetworkModes;
    private final int priority;

    public TenantSandboxPolicy(
            String tenantId,
            SandboxLimits maxLimits,
            Set<NetworkMode> allowedNetworkModes,
            int priority) {

        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.maxLimits = Objects.requireNonNull(maxLimits, "maxLimits must not be null");
        this.allowedNetworkModes = allowedNetworkModes == null
                ? Set.of(NetworkMode.DISABLED, NetworkMode.RESTRICTED)
                : Set.copyOf(allowedNetworkModes);
        this.priority = priority;
    }

    public TenantSandboxPolicy(String tenantId, SandboxLimits maxLimits) {
        this(tenantId, maxLimits, Set.of(NetworkMode.DISABLED, NetworkMode.RESTRICTED), 100);
    }

    @Override
    public String id() {
        return "tenant.policy." + tenantId;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public SandboxPolicyDecision evaluate(SandboxPolicyContext context) {
        if (!tenantId.equals(context.tenantId())) {
            return SandboxPolicyDecision.allow(ExecutionIsolationProfile.none());
        }

        ExecutionIsolationProfile requested = context.requestedProfile();

        // Check network mode
        if (!allowedNetworkModes.contains(requested.network().mode())) {
            return SandboxPolicyDecision.deny(
                    "Network mode " + requested.network().mode() + " is not permitted for tenant: " + tenantId);
        }

        // Check if requested limits exceed tenant max limits
        if (maxLimits.hasMemoryLimit() && requested.limits().hasMemoryLimit()
                && requested.limits().memoryBytes() > maxLimits.memoryBytes()) {
            return SandboxPolicyDecision.deny(
                    "Requested memory " + requested.limits().memoryBytes() + " bytes exceeds tenant limit "
                            + maxLimits.memoryBytes() + " bytes");
        }

        if (maxLimits.hasCpuLimit() && requested.limits().hasCpuLimit()
                && requested.limits().cpuMillis() > maxLimits.cpuMillis()) {
            return SandboxPolicyDecision.deny(
                    "Requested CPU " + requested.limits().cpuMillis() + " ms exceeds tenant limit "
                            + maxLimits.cpuMillis() + " ms");
        }

        // Return constrained profile with tenant max limits applied
        ExecutionIsolationProfile constrained = new ExecutionIsolationProfile(
                requested.level(),
                requested.requiredFeatures(),
                requested.preferredSandboxType(),
                maxLimits,
                requested.filesystem(),
                requested.network(),
                requested.environment(),
                requested.attributes()
        );

        return SandboxPolicyDecision.allow(constrained);
    }
}

package tech.kayys.wayang.sandbox.policy;

import tech.kayys.wayang.spi.sandbox.*;

import java.time.Duration;
import java.util.*;

public final class SandboxProfileMerger {

    private SandboxProfileMerger() {
    }

    public static ExecutionIsolationProfile merge(
            ExecutionIsolationProfile base,
            ExecutionIsolationProfile overlay) {

        if (base == null && overlay == null) {
            return ExecutionIsolationProfile.none();
        }
        if (base == null) {
            return overlay;
        }
        if (overlay == null) {
            return base;
        }

        // Higher isolation level wins
        IsolationLevel level = base.level().ordinal() >= overlay.level().ordinal()
                ? base.level()
                : overlay.level();

        // Union of required features
        Set<IsolationFeature> features = new HashSet<>(base.requiredFeatures());
        features.addAll(overlay.requiredFeatures());

        // Preferred type: more isolated type wins (VM > CONTAINER > PROCESS > NONE)
        SandboxType type = mergeType(base.preferredSandboxType(), overlay.preferredSandboxType());

        // Merge limits: most restrictive (minimum) limit wins
        SandboxLimits limits = mergeLimits(base.limits(), overlay.limits());

        // Merge network: most restrictive mode wins via intersection
        SandboxNetwork network = mergeNetwork(base.network(), overlay.network());

        // Filesystem: if either is not empty, use the more specific one
        SandboxFilesystem filesystem = overlay.filesystem() != null && !overlay.filesystem().roots().isEmpty()
                ? overlay.filesystem()
                : base.filesystem();

        // Merge environment and attributes
        Map<String, String> env = new HashMap<>(base.environment());
        env.putAll(overlay.environment());

        Map<String, Object> attrs = new HashMap<>(base.attributes());
        attrs.putAll(overlay.attributes());

        return new ExecutionIsolationProfile(
                level,
                features,
                type,
                limits,
                filesystem,
                network,
                env,
                attrs
        );
    }

    private static SandboxType mergeType(SandboxType a, SandboxType b) {
        if (a == SandboxType.VM || b == SandboxType.VM) {
            return SandboxType.VM;
        }
        if (a == SandboxType.CONTAINER || b == SandboxType.CONTAINER) {
            return SandboxType.CONTAINER;
        }
        if (a == SandboxType.PROCESS || b == SandboxType.PROCESS) {
            return SandboxType.PROCESS;
        }
        return SandboxType.NONE;
    }

    private static SandboxLimits mergeLimits(SandboxLimits a, SandboxLimits b) {
        long cpu = mergeLongLimit(a.cpuMillis(), b.cpuMillis());
        long memory = mergeLongLimit(a.memoryBytes(), b.memoryBytes());
        long disk = mergeLongLimit(a.diskBytes(), b.diskBytes());
        long proc = mergeLongLimit(a.processCount(), b.processCount());
        long output = mergeLongLimit(a.outputBytes(), b.outputBytes());
        long fileCount = mergeLongLimit(a.fileCount(), b.fileCount());

        Duration timeout = mergeTimeout(a.executionTimeout(), b.executionTimeout());

        return new SandboxLimits(cpu, memory, disk, proc, timeout, output, fileCount);
    }

    private static long mergeLongLimit(long a, long b) {
        if (a <= 0 && b <= 0) {
            return -1;
        }
        if (a <= 0) {
            return b;
        }
        if (b <= 0) {
            return a;
        }
        return Math.min(a, b);
    }

    private static Duration mergeTimeout(Duration a, Duration b) {
        if (a == null && b == null) {
            return null;
        }
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static SandboxNetwork mergeNetwork(SandboxNetwork a, SandboxNetwork b) {
        NetworkMode mode = NetworkPolicyIntersection.mode(a.mode(), b.mode());
        if (mode == NetworkMode.DISABLED) {
            return SandboxNetwork.disabled();
        }
        if (mode == NetworkMode.FULL) {
            return SandboxNetwork.full();
        }

        // RESTRICTED: intersection of rules
        List<NetworkRule> rules = new ArrayList<>();
        for (NetworkRule ra : a.rules()) {
            for (NetworkRule rb : b.rules()) {
                if (ra.protocol() == rb.protocol()
                        && ra.host().equalsIgnoreCase(rb.host())
                        && ra.port() == rb.port()) {
                    rules.add(ra);
                }
            }
        }
        return SandboxNetwork.restricted(rules);
    }
}

package tech.kayys.wayang.sandbox.policy;

import tech.kayys.wayang.spi.sandbox.ExecutionIsolationProfile;
import tech.kayys.wayang.spi.sandbox.SandboxPolicy;
import tech.kayys.wayang.spi.sandbox.SandboxPolicyContext;
import tech.kayys.wayang.spi.sandbox.SandboxPolicyDecision;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SandboxPolicyEvaluator {

    private SandboxPolicyEvaluator() {
    }

    public static SandboxPolicyDecision evaluate(
            List<SandboxPolicy> policies,
            SandboxPolicyContext context) {

        Objects.requireNonNull(context, "context must not be null");

        if (policies == null || policies.isEmpty()) {
            return SandboxPolicyDecision.allow(context.requestedProfile());
        }

        List<SandboxPolicy> sorted = policies.stream()
                .filter(SandboxPolicy::enabled)
                .sorted(Comparator.comparingInt(SandboxPolicy::priority))
                .toList();

        ExecutionIsolationProfile mergedProfile = context.requestedProfile();

        for (SandboxPolicy policy : sorted) {
            SandboxPolicyDecision decision = policy.evaluate(context);

            if (decision instanceof SandboxPolicyDecision.Deny deny) {
                return deny;
            }

            if (decision instanceof SandboxPolicyDecision.Allow allow) {
                mergedProfile = SandboxProfileMerger.merge(mergedProfile, allow.profile());
            }
        }

        return SandboxPolicyDecision.allow(mergedProfile);
    }
}

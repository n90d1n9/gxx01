package tech.kayys.wayang.sandbox.network;

import tech.kayys.wayang.spi.sandbox.*;

import java.util.List;
import java.util.Objects;

public final class DefaultSandboxNetwork implements SandboxNetwork {

    private final NetworkSandboxPolicy policy;

    public DefaultSandboxNetwork(NetworkSandboxPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public NetworkMode mode() {
        return policy.mode();
    }

    @Override
    public List<NetworkRule> rules() {
        return policy.allow();
    }

    @Override
    public boolean allows(NetworkRule rule) {
        Objects.requireNonNull(rule, "rule");

        if (policy.mode() == NetworkMode.DISABLED) {
            return false;
        }

        if (policy.mode() == NetworkMode.FULL) {
            return !matchesAny(policy.deny(), rule);
        }

        if (matchesAny(policy.deny(), rule)) {
            return false;
        }

        return matchesAny(policy.allow(), rule);
    }

    @Override
    public void validate(NetworkRule rule) {
        if (!allows(rule)) {
            throw new NetworkAccessException("Network access denied: " + rule);
        }
    }

    private boolean matchesAny(List<NetworkRule> rules, NetworkRule requested) {
        return rules.stream().anyMatch(candidate -> matches(candidate, requested));
    }

    private boolean matches(NetworkRule candidate, NetworkRule requested) {
        return candidate.protocol() == requested.protocol()
                && Objects.equals(candidate.port(), requested.port())
                && candidate.host().equalsIgnoreCase(requested.host());
    }
}

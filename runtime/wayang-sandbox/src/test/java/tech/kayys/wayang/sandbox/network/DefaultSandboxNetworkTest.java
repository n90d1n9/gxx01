package tech.kayys.wayang.sandbox.network;

import org.junit.jupiter.api.Test;
import tech.kayys.wayang.spi.sandbox.NetworkMode;
import tech.kayys.wayang.spi.sandbox.NetworkProtocol;
import tech.kayys.wayang.spi.sandbox.NetworkRule;
import tech.kayys.wayang.spi.sandbox.NetworkSandboxPolicy;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSandboxNetworkTest {

    @Test
    void testDisabledNetwork() {
        DefaultSandboxNetwork network = new DefaultSandboxNetwork(NetworkSandboxPolicy.disabled());
        assertEquals(NetworkMode.DISABLED, network.mode());

        NetworkRule rule = new NetworkRule(NetworkProtocol.TCP, "example.com", 443);
        assertFalse(network.allows(rule));
        assertThrows(NetworkAccessException.class, () -> network.validate(rule));
    }

    @Test
    void testFullNetwork() {
        DefaultSandboxNetwork network = new DefaultSandboxNetwork(NetworkSandboxPolicy.full());
        assertEquals(NetworkMode.FULL, network.mode());

        NetworkRule rule = new NetworkRule(NetworkProtocol.TCP, "example.com", 443);
        assertTrue(network.allows(rule));
        assertDoesNotThrow(() -> network.validate(rule));
    }

    @Test
    void testRestrictedNetworkWithDenyPrecedence() {
        NetworkRule allowed = new NetworkRule(NetworkProtocol.TCP, "allowed.com", 443);
        NetworkRule denied = new NetworkRule(NetworkProtocol.TCP, "denied.com", 443);
        NetworkRule blockedConflict = new NetworkRule(NetworkProtocol.TCP, "conflict.com", 80);

        NetworkSandboxPolicy policy = new NetworkSandboxPolicy(
                NetworkMode.RESTRICTED,
                List.of(allowed, blockedConflict),
                List.of(denied, blockedConflict),
                false,
                false,
                Map.of()
        );
        DefaultSandboxNetwork network = new DefaultSandboxNetwork(policy);

        assertTrue(network.allows(allowed));
        assertFalse(network.allows(denied));
        assertFalse(network.allows(blockedConflict)); // Deny takes precedence over allow
        assertFalse(network.allows(new NetworkRule(NetworkProtocol.TCP, "unknown.com", 443)));
    }
}

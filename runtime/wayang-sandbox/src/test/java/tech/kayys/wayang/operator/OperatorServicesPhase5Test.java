package tech.kayys.wayang.operator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.wayang.extension.Version;
import tech.kayys.wayang.operator.runtime.PermissiveOperatorAuthorization;
import tech.kayys.wayang.operator.runtime.diagnostics.*;
import tech.kayys.wayang.operator.runtime.execution.DefaultExecutionOperatorService;
import tech.kayys.wayang.operator.runtime.execution.InMemoryExecutionControl;
import tech.kayys.wayang.operator.runtime.plugin.DefaultPluginOperatorService;
import tech.kayys.wayang.operator.runtime.sandbox.DefaultSandboxOperatorService;
import tech.kayys.wayang.operator.runtime.session.DefaultSessionOperatorService;
import tech.kayys.wayang.operator.runtime.session.InMemorySessionManager;
import tech.kayys.wayang.operator.runtime.tool.DefaultToolCapabilityOperatorService;
import tech.kayys.wayang.spi.capability.Capability;
import tech.kayys.wayang.spi.capability.CapabilityDescriptor;
import tech.kayys.wayang.spi.capability.CapabilityRegistry;
import tech.kayys.wayang.spi.capability.CapabilityType;
import tech.kayys.wayang.spi.diagnostics.*;
import tech.kayys.wayang.spi.execution.*;
import tech.kayys.wayang.spi.operator.OperatorContext;
import tech.kayys.wayang.spi.operator.OperatorResult;
import tech.kayys.wayang.spi.operator.diagnostics.DiagnosticsOperatorPermissions;
import tech.kayys.wayang.spi.operator.execution.ExecutionOperatorPermissions;
import tech.kayys.wayang.spi.operator.plugin.PluginOperatorPermissions;
import tech.kayys.wayang.spi.operator.plugin.PluginSummary;
import tech.kayys.wayang.spi.operator.sandbox.SandboxOperatorPermissions;
import tech.kayys.wayang.spi.operator.sandbox.SandboxSummary;
import tech.kayys.wayang.spi.operator.session.SessionOperatorPermissions;
import tech.kayys.wayang.spi.operator.tool.CapabilitySummary;
import tech.kayys.wayang.spi.operator.tool.ToolCapabilityOperatorPermissions;
import tech.kayys.wayang.spi.operator.tool.ToolSummary;
import tech.kayys.wayang.spi.plugin.Manifest;
import tech.kayys.wayang.spi.plugin.Plugin;
import tech.kayys.wayang.spi.plugin.PluginManager;
import tech.kayys.wayang.spi.plugin.PluginState;
import tech.kayys.wayang.spi.sandbox.Sandbox;
import tech.kayys.wayang.spi.sandbox.SandboxContext;
import tech.kayys.wayang.spi.sandbox.SandboxDescriptor;
import tech.kayys.wayang.spi.sandbox.SandboxManager;
import tech.kayys.wayang.spi.sandbox.SandboxState;
import tech.kayys.wayang.spi.sandbox.SandboxType;
import tech.kayys.wayang.spi.session.*;
import tech.kayys.wayang.tool.DefaultToolDescriptor;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.tool.ToolId;
import tech.kayys.wayang.tool.ToolRegistry;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class OperatorServicesPhase5Test {

    private PermissiveOperatorAuthorization authorization;
    private OperatorContext context;

    @BeforeEach
    void setUp() {
        authorization = new PermissiveOperatorAuthorization();
        context = new OperatorContext("tenant-1", "user-1", "corr-1", "req-1", Map.of());
    }

    // --- Step 5.2: Plugin Operator ---
    @Test
    void testPluginOperatorLifecycleAndAuthorization() {
        Map<String, Plugin> plugins = new HashMap<>();
        AtomicReference<PluginState> pluginState = new AtomicReference<>(PluginState.ACTIVE);

        Manifest manifest = (Manifest) Proxy.newProxyInstance(
                Manifest.class.getClassLoader(),
                new Class<?>[]{Manifest.class},
                (proxy, method, args) -> {
                    if ("name".equals(method.getName())) return "Test Plugin";
                    if ("version".equals(method.getName())) return Version.VERSION_1_0_0;
                    if ("description".equals(method.getName())) return "Test Description";
                    return null;
                }
        );

        Plugin plugin = (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "id" -> { return "plugin-test"; }
                        case "manifest" -> { return manifest; }
                        case "state" -> { return pluginState.get(); }
                        case "extensions" -> { return List.of(); }
                        default -> { return null; }
                    }
                }
        );
        plugins.put("plugin-test", plugin);

        PluginManager pm = (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getPlugin" -> {
                            String id = (String) args[0];
                            return Optional.ofNullable(plugins.get(id));
                        }
                        case "getPlugins" -> {
                            return List.copyOf(plugins.values());
                        }
                        case "enablePlugin" -> {
                            pluginState.set(PluginState.ACTIVE);
                            return null;
                        }
                        case "disablePlugin" -> {
                            pluginState.set(PluginState.STOPPED);
                            return null;
                        }
                        case "unloadPlugin" -> {
                            String id = (String) args[0];
                            plugins.remove(id);
                            return null;
                        }
                        default -> { return null; }
                    }
                }
        );

        DefaultPluginOperatorService service = new DefaultPluginOperatorService(pm, authorization);

        // List
        var listRes = service.list(context);
        assertTrue(listRes instanceof OperatorResult.Success);
        assertEquals(1, ((OperatorResult.Success<List<PluginSummary>>) listRes).value().size());

        // Inspect
        var inspectRes = service.inspect(context, "plugin-test");
        assertTrue(inspectRes instanceof OperatorResult.Success);
        assertEquals("plugin-test", ((OperatorResult.Success<PluginSummary>) inspectRes).value().id());

        // Disable
        var disRes = service.disable(context, "plugin-test");
        assertTrue(disRes instanceof OperatorResult.Success);
        assertEquals(PluginState.STOPPED, pluginState.get());

        // Enable
        var enRes = service.enable(context, "plugin-test");
        assertTrue(enRes instanceof OperatorResult.Success);
        assertEquals(PluginState.ACTIVE, pluginState.get());

        // Authorization check: deny UNLOAD
        authorization.deny(PluginOperatorPermissions.UNLOAD);
        var unloadRes = service.unload(context, "plugin-test");
        assertTrue(unloadRes instanceof OperatorResult.Failure);
        assertEquals("PERMISSION_DENIED", ((OperatorResult.Failure<?>) unloadRes).code());
    }

    // --- Step 5.3: Sandbox Operator ---
    @Test
    void testSandboxOperatorLifecycleAndAuthorization() {
        SandboxDescriptor desc = new SandboxDescriptor(
                "sb-test-1", "Sandbox Test", "desc", SandboxType.PROCESS,
                Version.VERSION_1_0_0, Set.of(), Map.of("providerId", "proc-prov")
        );
        SandboxContext ctx = (SandboxContext) Proxy.newProxyInstance(
                SandboxContext.class.getClassLoader(),
                new Class<?>[]{SandboxContext.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "sandboxId" -> { return "sb-test-1"; }
                        case "executionId" -> { return "exec-test-1"; }
                        case "tenantId" -> { return Optional.of("tenant-1"); }
                        case "agentId" -> { return Optional.of("agent-1"); }
                        case "createdAt" -> { return Instant.now(); }
                        default -> { return null; }
                    }
                }
        );
        AtomicReference<SandboxState> sbState = new AtomicReference<>(SandboxState.RUNNING);

        Sandbox sandbox = (Sandbox) Proxy.newProxyInstance(
                Sandbox.class.getClassLoader(),
                new Class<?>[]{Sandbox.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "id" -> { return "sb-test-1"; }
                        case "descriptor" -> { return desc; }
                        case "state" -> { return sbState.get(); }
                        case "context" -> { return ctx; }
                        case "stop" -> {
                            sbState.set(SandboxState.STOPPED);
                            return null;
                        }
                        case "destroy" -> {
                            sbState.set(SandboxState.DESTROYED);
                            return null;
                        }
                        default -> { return null; }
                    }
                }
        );

        SandboxManager sm = (SandboxManager) Proxy.newProxyInstance(
                SandboxManager.class.getClassLoader(),
                new Class<?>[]{SandboxManager.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "find" -> {
                            String id = (String) args[0];
                            return "sb-test-1".equals(id) ? Optional.of(sandbox) : Optional.empty();
                        }
                        case "list" -> {
                            return sbState.get() == SandboxState.DESTROYED ? List.of() : List.of(sandbox);
                        }
                        case "stop" -> {
                            sandbox.stop();
                            return null;
                        }
                        case "destroy" -> {
                            sandbox.destroy();
                            return null;
                        }
                        default -> { return null; }
                    }
                }
        );

        DefaultSandboxOperatorService service = new DefaultSandboxOperatorService(sm, authorization);

        // List
        var listRes = service.list(context);
        assertTrue(listRes instanceof OperatorResult.Success);
        assertEquals(1, ((OperatorResult.Success<List<SandboxSummary>>) listRes).value().size());

        // Inspect
        var inspectRes = service.inspect(context, "sb-test-1");
        assertTrue(inspectRes instanceof OperatorResult.Success);
        assertEquals("sb-test-1", ((OperatorResult.Success<SandboxSummary>) inspectRes).value().sandboxId());

        // Health
        var healthRes = service.health(context, "sb-test-1");
        assertTrue(healthRes instanceof OperatorResult.Success);

        // Metrics
        var metricsRes = service.metrics(context, "sb-test-1");
        assertTrue(metricsRes instanceof OperatorResult.Success);

        // Diagnostics
        var diagRes = service.diagnostics(context, "sb-test-1");
        assertTrue(diagRes instanceof OperatorResult.Success);

        // Stop
        var stopRes = service.stop(context, "sb-test-1");
        assertTrue(stopRes instanceof OperatorResult.Success);
        assertEquals(SandboxState.STOPPED, sbState.get());

        // Authorization deny DESTROY
        authorization.deny(SandboxOperatorPermissions.DESTROY);
        var destroyRes = service.destroy(context, "sb-test-1");
        assertTrue(destroyRes instanceof OperatorResult.Failure);
        assertEquals("PERMISSION_DENIED", ((OperatorResult.Failure<?>) destroyRes).code());
    }

    // --- Step 5.4: Tool & Capability Operator ---
    @Test
    void testToolCapabilityOperator() {
        Capability cap = (Capability) Proxy.newProxyInstance(
                Capability.class.getClassLoader(),
                new Class<?>[]{Capability.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "id" -> { return "cap-browser"; }
                        case "type" -> { return CapabilityType.of("tool", "browser"); }
                        case "descriptor" -> {
                            return new CapabilityDescriptor(
                                    "cap-browser",
                                    "Web Browser",
                                    CapabilityType.of("tool", "browser"),
                                    Map.of("name", "Browser Tool", "tags", List.of("web"))
                            );
                        }
                        default -> { return null; }
                    }
                }
        );

        CapabilityRegistry capReg = (CapabilityRegistry) Proxy.newProxyInstance(
                CapabilityRegistry.class.getClassLoader(),
                new Class<?>[]{CapabilityRegistry.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "findAll" -> { return List.of(cap); }
                        case "find" -> {
                            String id = (String) args[0];
                            return "cap-browser".equals(id) ? Optional.of(cap) : Optional.empty();
                        }
                        default -> { return null; }
                    }
                }
        );

        DefaultToolDescriptor toolDesc = new DefaultToolDescriptor(
                ToolId.of("web_search"),
                "web_search",
                "Search web",
                "1.0.0",
                Set.of("cap-browser"),
                null, null, null
        );

        Tool tool = (Tool) Proxy.newProxyInstance(
                Tool.class.getClassLoader(),
                new Class<?>[]{Tool.class},
                (proxy, method, args) -> {
                    if ("descriptor".equals(method.getName())) return toolDesc;
                    return null;
                }
        );

        ToolRegistry toolReg = (ToolRegistry) Proxy.newProxyInstance(
                ToolRegistry.class.getClassLoader(),
                new Class<?>[]{ToolRegistry.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "listTools" -> { return List.of(tool); }
                        case "findByName" -> {
                            String name = (String) args[0];
                            return "web_search".equals(name) ? Optional.of(tool) : Optional.empty();
                        }
                        default -> { return null; }
                    }
                }
        );

        DefaultToolCapabilityOperatorService service = new DefaultToolCapabilityOperatorService(capReg, toolReg, authorization);

        // List capabilities
        var capsRes = service.listCapabilities(context);
        assertTrue(capsRes instanceof OperatorResult.Success);
        assertEquals(1, ((OperatorResult.Success<List<CapabilitySummary>>) capsRes).value().size());

        // Inspect capability
        var inspectCap = service.inspectCapability(context, "cap-browser");
        assertTrue(inspectCap instanceof OperatorResult.Success);

        // Find by type
        var byType = service.findCapabilitiesByType(context, "tool");
        assertTrue(byType instanceof OperatorResult.Success);
        assertEquals(1, ((OperatorResult.Success<List<CapabilitySummary>>) byType).value().size());

        // Find by tag
        var byTag = service.findCapabilitiesByTag(context, "web");
        assertTrue(byTag instanceof OperatorResult.Success);
        assertEquals(1, ((OperatorResult.Success<List<CapabilitySummary>>) byTag).value().size());

        // List tools
        var toolsRes = service.listTools(context);
        assertTrue(toolsRes instanceof OperatorResult.Success);
        assertEquals(1, ((OperatorResult.Success<List<ToolSummary>>) toolsRes).value().size());

        // Inspect tool
        var inspectTool = service.inspectTool(context, "web_search");
        assertTrue(inspectTool instanceof OperatorResult.Success);
        assertEquals("web_search", ((OperatorResult.Success<ToolSummary>) inspectTool).value().name());

        // Find tools by capability
        var byCap = service.findToolsByCapability(context, "cap-browser");
        assertTrue(byCap instanceof OperatorResult.Success);
        assertEquals(1, ((OperatorResult.Success<List<ToolSummary>>) byCap).value().size());

        // Authorization deny
        authorization.deny(ToolCapabilityOperatorPermissions.TOOL_INSPECT);
        var denied = service.inspectTool(context, "web_search");
        assertTrue(denied instanceof OperatorResult.Failure);
        assertEquals("PERMISSION_DENIED", ((OperatorResult.Failure<?>) denied).code());
    }

    // --- Step 5.5: Execution Operator ---
    @Test
    void testExecutionOperatorControlAndStateTransitions() {
        InMemoryExecutionControl control = new InMemoryExecutionControl();
        ExecutionInfo info = new ExecutionInfo(
                "exec-100", "tenant-1", "user-1", "agent-1", "wf-1",
                ExecutionState.RUNNING, Instant.now(), Instant.now(), null, Instant.now(),
                "corr-100", null, null, Map.of()
        );
        control.register(info);

        DefaultExecutionOperatorService service = new DefaultExecutionOperatorService(control, authorization);

        // List
        var listRes = service.list(context, ExecutionQuery.all());
        assertTrue(listRes instanceof OperatorResult.Success);
        assertEquals(1, ((OperatorResult.Success<List<ExecutionInfo>>) listRes).value().size());

        // Inspect
        var inspectRes = service.inspect(context, "exec-100");
        assertTrue(inspectRes instanceof OperatorResult.Success);
        assertEquals(ExecutionState.RUNNING, ((OperatorResult.Success<ExecutionInfo>) inspectRes).value().state());

        // Pause
        var pauseRes = service.pause(context, "exec-100");
        assertTrue(pauseRes instanceof OperatorResult.Success);
        assertEquals(ExecutionState.PAUSED, control.find("exec-100").orElseThrow().state());

        // Resume
        var resumeRes = service.resume(context, "exec-100");
        assertTrue(resumeRes instanceof OperatorResult.Success);
        assertEquals(ExecutionState.RUNNING, control.find("exec-100").orElseThrow().state());

        // Cancel
        var cancelRes = service.cancel(context, "exec-100");
        assertTrue(cancelRes instanceof OperatorResult.Success);
        assertEquals(ExecutionState.CANCELLED, control.find("exec-100").orElseThrow().state());

        // Retry
        var retryRes = service.retry(context, "exec-100");
        assertTrue(retryRes instanceof OperatorResult.Success);
        assertEquals(ExecutionState.QUEUED, control.find("exec-100").orElseThrow().state());

        // Authorization deny
        authorization.deny(ExecutionOperatorPermissions.CANCEL);
        var denied = service.cancel(context, "exec-100");
        assertTrue(denied instanceof OperatorResult.Failure);
        assertEquals("PERMISSION_DENIED", ((OperatorResult.Failure<?>) denied).code());
    }

    // --- Step 5.6: Session Operator ---
    @Test
    void testSessionOperatorControlAndState() {
        InMemorySessionManager sm = new InMemorySessionManager();
        SessionId id = SessionId.of("sess-200");
        SessionInfo info = new SessionInfo(
                id, "tenant-1", "user-1", "agent-1", SessionState.ACTIVE,
                Instant.now(), Instant.now(), Instant.now().plusSeconds(3600),
                "corr-200", List.of(), Map.of()
        );
        sm.register(info);

        DefaultSessionOperatorService service = new DefaultSessionOperatorService(sm, authorization);

        // List
        var listRes = service.list(context, SessionQuery.all());
        assertTrue(listRes instanceof OperatorResult.Success);
        assertEquals(1, ((OperatorResult.Success<List<SessionInfo>>) listRes).value().size());

        // Inspect
        var inspectRes = service.inspect(context, id);
        assertTrue(inspectRes instanceof OperatorResult.Success);
        assertEquals(SessionState.ACTIVE, ((OperatorResult.Success<SessionInfo>) inspectRes).value().state());

        // Suspend
        var suspRes = service.suspend(context, id);
        assertTrue(suspRes instanceof OperatorResult.Success);
        assertEquals(SessionState.SUSPENDED, sm.find(id).orElseThrow().state());

        // Resume
        var resRes = service.resume(context, id);
        assertTrue(resRes instanceof OperatorResult.Success);
        assertEquals(SessionState.ACTIVE, sm.find(id).orElseThrow().state());

        // Close
        var closeRes = service.close(context, id);
        assertTrue(closeRes instanceof OperatorResult.Success);
        assertEquals(SessionState.CLOSED, sm.find(id).orElseThrow().state());

        // Authorization deny
        authorization.deny(SessionOperatorPermissions.CLOSE);
        var denied = service.close(context, id);
        assertTrue(denied instanceof OperatorResult.Failure);
        assertEquals("PERMISSION_DENIED", ((OperatorResult.Failure<?>) denied).code());
    }

    // --- Step 5.7: Diagnostics Operator & Dominance Aggregation ---
    @Test
    void testDiagnosticsAggregationAndProviderFailureIsolation() {
        DefaultDiagnosticProviderRegistry reg = new DefaultDiagnosticProviderRegistry();

        // Register a healthy provider
        reg.register(new DiagnosticProvider() {
            @Override
            public String id() { return "healthy-prov"; }
            @Override
            public DiagnosticComponentType componentType() { return DiagnosticComponentType.PLATFORM; }
            @Override
            public DiagnosticResult diagnose(DiagnosticContext context) {
                return DiagnosticResult.healthy(DiagnosticComponent.of(DiagnosticComponentType.PLATFORM, "p1"), "Platform is OK");
            }
        });

        // Register a failing provider (throws exception)
        reg.register(new DiagnosticProvider() {
            @Override
            public String id() { return "broken-prov"; }
            @Override
            public DiagnosticComponentType componentType() { return DiagnosticComponentType.INFRASTRUCTURE; }
            @Override
            public DiagnosticResult diagnose(DiagnosticContext context) throws Exception {
                throw new RuntimeException("Underlying infrastructure connectivity timeout");
            }
        });

        DefaultDiagnosticService diagService = new DefaultDiagnosticService(reg);
        DefaultDiagnosticsOperatorService operatorService = new DefaultDiagnosticsOperatorService(diagService, authorization);

        var reportRes = operatorService.diagnoseAll(context);
        assertTrue(reportRes instanceof OperatorResult.Success);
        DiagnosticReport report = ((OperatorResult.Success<DiagnosticReport>) reportRes).value();

        // 2 results collected despite broken provider throwing exception!
        assertEquals(2, report.results().size());

        // Verify failure isolation on the broken provider
        DiagnosticResult brokenResult = report.results().stream()
                .filter(r -> r.component().id().equals("broken-prov"))
                .findFirst().orElseThrow();
        assertEquals(DiagnosticStatus.UNKNOWN, brokenResult.status());
        assertTrue(Boolean.TRUE.equals(brokenResult.attributes().get("providerFailure")));

        // Aggregation test: HEALTHY + UNKNOWN -> overall HEALTHY
        assertEquals(DiagnosticStatus.HEALTHY, report.overallStatus());

        // Now add a DEGRADED provider: DEGRADED dominates HEALTHY
        reg.register(new DiagnosticProvider() {
            @Override
            public String id() { return "degraded-prov"; }
            @Override
            public DiagnosticComponentType componentType() { return DiagnosticComponentType.MEMORY; }
            @Override
            public DiagnosticResult diagnose(DiagnosticContext context) {
                return DiagnosticResult.degraded(
                        DiagnosticComponent.of(DiagnosticComponentType.MEMORY, "mem-1"),
                        "Memory pressure at 88%",
                        List.of(DiagnosticIssue.warning("HIGH_MEMORY", "88% used"))
                );
            }
        });

        var degradedReport = ((OperatorResult.Success<DiagnosticReport>) operatorService.diagnoseAll(context)).value();
        assertEquals(DiagnosticStatus.DEGRADED, degradedReport.overallStatus());

        // Now add an UNHEALTHY provider: UNHEALTHY dominates DEGRADED & HEALTHY
        reg.register(new DiagnosticProvider() {
            @Override
            public String id() { return "unhealthy-prov"; }
            @Override
            public DiagnosticComponentType componentType() { return DiagnosticComponentType.SANDBOX; }
            @Override
            public DiagnosticResult diagnose(DiagnosticContext context) {
                return DiagnosticResult.unhealthy(
                        DiagnosticComponent.of(DiagnosticComponentType.SANDBOX, "sb-fail"),
                        "Sandbox failed to launch",
                        List.of(DiagnosticIssue.critical("SANDBOX_CRASH", "Process exited with 137"))
                );
            }
        });

        var unhealthyReport = ((OperatorResult.Success<DiagnosticReport>) operatorService.diagnoseAll(context)).value();
        assertEquals(DiagnosticStatus.UNHEALTHY, unhealthyReport.overallStatus());
    }
}

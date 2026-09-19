package tech.kayys.wayang.operator.runtime.diagnostics;

import tech.kayys.wayang.spi.diagnostics.*;
import tech.kayys.wayang.spi.plugin.Plugin;
import tech.kayys.wayang.spi.plugin.PluginManager;
import tech.kayys.wayang.spi.plugin.PluginState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PluginDiagnosticProvider implements DiagnosticProvider {

    public static final String ID = "plugin-diagnostic-provider";

    private final PluginManager pluginManager;

    public PluginDiagnosticProvider(PluginManager pluginManager) {
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager must not be null");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public DiagnosticComponentType componentType() {
        return DiagnosticComponentType.PLUGIN;
    }

    @Override
    public DiagnosticResult diagnose(DiagnosticContext context) throws Exception {
        List<Plugin> plugins = pluginManager.getPlugins();
        List<DiagnosticIssue> issues = new ArrayList<>();
        DiagnosticStatus status = DiagnosticStatus.HEALTHY;

        for (Plugin plugin : plugins) {
            if (plugin.state() == PluginState.ERROR) {
                issues.add(new DiagnosticIssue(
                        "PLUGIN_ERROR",
                        DiagnosticSeverity.ERROR,
                        "Plugin " + plugin.id() + " is in ERROR state",
                        Map.of("pluginId", plugin.id())
                ));
                status = DiagnosticStatus.UNHEALTHY;
            }
        }

        return new DiagnosticResult(
                DiagnosticComponent.of(DiagnosticComponentType.PLUGIN, ID),
                status,
                Instant.now(),
                "Plugin subsystem status: " + status + " (" + plugins.size() + " plugins loaded)",
                issues,
                Map.of("totalPlugins", plugins.size())
        );
    }
}

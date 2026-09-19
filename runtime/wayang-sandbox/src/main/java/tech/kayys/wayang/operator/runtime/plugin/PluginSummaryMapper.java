package tech.kayys.wayang.operator.runtime.plugin;

import tech.kayys.wayang.spi.operator.plugin.PluginSummary;
import tech.kayys.wayang.spi.plugin.Plugin;

import java.util.List;

public final class PluginSummaryMapper {

    private PluginSummaryMapper() {
    }

    public static PluginSummary map(Plugin plugin) {
        if (plugin == null) {
            return null;
        }

        var manifest = plugin.manifest();
        String name = manifest != null ? manifest.name() : plugin.id();
        String version = manifest != null && manifest.version() != null ? manifest.version().toString() : "0.0.0";
        String description = manifest != null ? manifest.description() : null;

        List<String> extensionTypes = plugin.extensions() == null
                ? List.of()
                : plugin.extensions().stream()
                        .filter(e -> e != null)
                        .map(e -> e.getClass().getName())
                        .toList();

        return new PluginSummary(
                plugin.id(),
                name,
                version,
                description,
                plugin.state(),
                extensionTypes
        );
    }
}

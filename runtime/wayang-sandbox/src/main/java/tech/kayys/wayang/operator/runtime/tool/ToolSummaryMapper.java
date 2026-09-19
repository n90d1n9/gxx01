package tech.kayys.wayang.operator.runtime.tool;

import tech.kayys.wayang.spi.operator.tool.ToolPermissionRequirement;
import tech.kayys.wayang.spi.operator.tool.ToolSummary;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.tool.ToolDescriptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class ToolSummaryMapper {

    private ToolSummaryMapper() {
    }

    public static ToolSummary map(Tool tool) {
        if (tool == null) {
            return null;
        }
        return map(tool.descriptor());
    }

    public static ToolSummary map(ToolDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }

        List<ToolPermissionRequirement> permissions = new ArrayList<>();
        try {
            var method = descriptor.getClass().getMethod("permissions");
            Object res = method.invoke(descriptor);
            if (res instanceof Collection<?> col) {
                for (Object item : col) {
                    if (item != null) {
                        String permId = String.valueOf(item);
                        boolean optional = false;
                        try {
                            var permIdMethod = item.getClass().getMethod("permissionId");
                            permId = (String) permIdMethod.invoke(item);
                            var optMethod = item.getClass().getMethod("optional");
                            optional = (Boolean) optMethod.invoke(item);
                        } catch (Exception ignored) {
                        }
                        permissions.add(new ToolPermissionRequirement(permId, optional));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        String providerId = null;
        if (descriptor.metadata() != null && descriptor.metadata().labels() != null) {
            providerId = descriptor.metadata().labels().get("providerId");
        }

        List<String> capabilities = descriptor.capabilityKeys() != null
                ? List.copyOf(descriptor.capabilityKeys())
                : List.of();

        return new ToolSummary(
                descriptor.name(),
                descriptor.description(),
                descriptor.version(),
                providerId,
                permissions,
                capabilities,
                Map.of()
        );
    }
}

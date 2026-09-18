package tech.kayys.wayang.sandbox;

import tech.kayys.wayang.extension.Id;
import tech.kayys.wayang.extension.Metadata;
import tech.kayys.wayang.extension.Version;
import tech.kayys.wayang.identity.ResourceId;
import tech.kayys.wayang.resource.BaseResource;
import tech.kayys.wayang.resource.ResourceType;
import tech.kayys.wayang.spi.sandbox.*;

import java.util.Map;
import java.util.Set;

public class LocalSandboxProvider extends BaseResource implements SandboxProvider {

    public static final String PROVIDER_ID = "local";

    private final SandboxProviderDescriptor descriptor;

    public LocalSandboxProvider() {
        super(
                new ResourceId.CustomId(Id.random(), new ResourceType.Execution()),
                Metadata.builder()
                        .name("LocalSandboxProvider")
                        .description("Host process-based isolated sandbox provider")
                        .build()
        );
        this.descriptor = new SandboxProviderDescriptor(
                PROVIDER_ID,
                "Local Process Sandbox Provider",
                "Host process-based isolated execution sandbox",
                Version.parse("1.0.0"),
                Set.of(SandboxType.PROCESS, SandboxType.NONE),
                Set.of(
                        IsolationFeature.PROCESS_ISOLATION,
                        IsolationFeature.FILESYSTEM_ISOLATION,
                        IsolationFeature.TEMPORARY_WORKSPACE
                ),
                Map.of()
        );
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public SandboxProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public Sandbox create(SandboxRequest request) throws Exception {
        return new LocalProcessSandbox(request);
    }

    @Override
    public Sandbox createSandbox(SandboxConfiguration config) throws Exception {
        return new LocalProcessSandbox(config);
    }
}

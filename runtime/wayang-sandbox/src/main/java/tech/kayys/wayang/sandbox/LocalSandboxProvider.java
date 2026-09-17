package tech.kayys.wayang.sandbox;

import tech.kayys.wayang.extension.Id;
import tech.kayys.wayang.extension.Metadata;
import tech.kayys.wayang.identity.ResourceId;
import tech.kayys.wayang.resource.BaseResource;
import tech.kayys.wayang.resource.ResourceType;
import tech.kayys.wayang.spi.sandbox.Sandbox;
import tech.kayys.wayang.spi.sandbox.SandboxConfiguration;
import tech.kayys.wayang.spi.sandbox.SandboxProvider;

public class LocalSandboxProvider extends BaseResource implements SandboxProvider {

    public static final String PROVIDER_ID = "local";

    public LocalSandboxProvider() {
        super(
                new ResourceId.CustomId(Id.random(), new ResourceType.Execution()),
                Metadata.builder()
                        .name("LocalSandboxProvider")
                        .description("Host process-based isolated sandbox provider")
                        .build()
        );
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public Sandbox createSandbox(SandboxConfiguration config) throws Exception {
        return new LocalProcessSandbox(config);
    }
}

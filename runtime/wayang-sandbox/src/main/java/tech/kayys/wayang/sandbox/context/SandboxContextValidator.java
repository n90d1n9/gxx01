package tech.kayys.wayang.sandbox.context;

import tech.kayys.wayang.spi.sandbox.SandboxContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class SandboxContextValidator {

    private SandboxContextValidator() {
    }

    public static void validate(SandboxContext context) {
        Objects.requireNonNull(context, "SandboxContext must not be null");

        if (context.sandboxId() == null || context.sandboxId().isBlank()) {
            throw new IllegalArgumentException("sandboxId must not be blank");
        }

        if (context.executionId() == null || context.executionId().isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }

        if (context.descriptor() == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }

        context.workspace().ifPresent(SandboxContextValidator::validateDirectory);
        context.inputDirectory().ifPresent(SandboxContextValidator::validateDirectory);
        context.outputDirectory().ifPresent(SandboxContextValidator::validateDirectory);
    }

    private static void validateDirectory(Path path) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Sandbox path must be absolute: " + path);
        }
        if (Files.exists(path) && !Files.isDirectory(path)) {
            throw new IllegalArgumentException("Sandbox path must be a directory: " + path);
        }
    }
}

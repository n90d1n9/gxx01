package tech.kayys.wayang.sandbox.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public final class SecureDirectoryCreator {

    private SecureDirectoryCreator() {
    }

    public static Path create(Path path) throws IOException {
        try {
            Files.createDirectories(path);
            Files.setPosixFilePermissions(
                    path,
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE
                    ));
        } catch (UnsupportedOperationException | SecurityException ignored) {
            Files.createDirectories(path);
        }

        return path;
    }
}

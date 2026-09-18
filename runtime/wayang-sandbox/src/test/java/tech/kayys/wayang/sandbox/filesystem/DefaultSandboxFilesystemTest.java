package tech.kayys.wayang.sandbox.filesystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.wayang.spi.sandbox.FilesystemAccess;
import tech.kayys.wayang.spi.sandbox.FilesystemRoot;
import tech.kayys.wayang.spi.sandbox.FilesystemSandboxPolicy;
import tech.kayys.wayang.spi.sandbox.ResolvedSandboxPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSandboxFilesystemTest {

    @TempDir
    Path tempDir;

    private Path workspace;
    private Path inputDir;
    private Path outputDir;
    private DefaultSandboxFilesystem filesystem;

    @BeforeEach
    void setUp() throws IOException {
        workspace = Files.createDirectories(tempDir.resolve("workspace"));
        inputDir = Files.createDirectories(tempDir.resolve("input"));
        outputDir = Files.createDirectories(tempDir.resolve("output"));

        Files.writeString(inputDir.resolve("read-only.txt"), "hello");

        List<FilesystemRoot> roots = List.of(
                new FilesystemRoot("workspace", workspace, FilesystemAccess.READ_WRITE, false),
                new FilesystemRoot("input", inputDir, FilesystemAccess.READ, false),
                new FilesystemRoot("output", outputDir, FilesystemAccess.READ_WRITE, false)
        );
        filesystem = new DefaultSandboxFilesystem(FilesystemSandboxPolicy.strict(roots));
    }

    @Test
    void testResolveAndExists() {
        assertTrue(filesystem.root("workspace").isPresent());
        assertTrue(filesystem.exists("input", Path.of("read-only.txt")));
        assertFalse(filesystem.exists("input", Path.of("non-existent.txt")));

        ResolvedSandboxPath resolved = filesystem.resolve("workspace", Path.of("test.txt"));
        assertEquals("workspace", resolved.rootId());
        assertEquals(FilesystemAccess.READ_WRITE, resolved.access());
        assertEquals(workspace.resolve("test.txt"), resolved.path());
    }

    @Test
    void testDenyParentTraversal() {
        assertThrows(FilesystemAccessException.class, () ->
                filesystem.resolve("workspace", Path.of("../outside.txt"))
        );
    }

    @Test
    void testDenyAbsolutePaths() {
        assertThrows(FilesystemAccessException.class, () ->
                filesystem.resolve("workspace", Path.of("/etc/passwd"))
        );
    }

    @Test
    void testValidateReadAndWrite() {
        assertDoesNotThrow(() -> filesystem.validateRead("input", Path.of("read-only.txt")));
        assertThrows(FilesystemAccessException.class, () ->
                filesystem.validateWrite("input", Path.of("read-only.txt"))
        );

        assertDoesNotThrow(() -> filesystem.validateWrite("output", Path.of("new.txt")));
    }
}

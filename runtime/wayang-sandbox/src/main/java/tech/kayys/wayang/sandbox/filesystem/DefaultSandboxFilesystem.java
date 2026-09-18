package tech.kayys.wayang.sandbox.filesystem;

import tech.kayys.wayang.spi.sandbox.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;

public final class DefaultSandboxFilesystem implements SandboxFilesystem {

    private final Map<String, FilesystemRoot> roots;
    private final FilesystemSandboxPolicy policy;

    public DefaultSandboxFilesystem(FilesystemSandboxPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");

        Map<String, FilesystemRoot> indexed = new LinkedHashMap<>();
        for (FilesystemRoot root : policy.roots()) {
            if (indexed.put(root.id(), root) != null) {
                throw new IllegalArgumentException("Duplicate filesystem root: " + root.id());
            }
        }

        this.roots = Map.copyOf(indexed);
    }

    @Override
    public Optional<FilesystemRoot> root(String rootId) {
        return Optional.ofNullable(roots.get(rootId));
    }

    @Override
    public ResolvedSandboxPath resolve(String rootId, Path requested) {
        FilesystemRoot root = requireRoot(rootId);
        Objects.requireNonNull(requested, "requested");

        if (policy.denyAbsolutePaths() && requested.isAbsolute()) {
            throw new FilesystemAccessException("Absolute paths are not allowed: " + requested);
        }

        Path rootPath = root.path().toAbsolutePath().normalize();
        Path candidate = rootPath.resolve(requested).normalize();

        if (policy.denyParentTraversal() && !candidate.startsWith(rootPath)) {
            throw new FilesystemAccessException("Path escapes sandbox root: " + requested);
        }

        if (policy.denySymlinkEscape()) {
            validateSymlinkBoundary(rootPath, candidate, root.followSymlinks());
        }

        return new ResolvedSandboxPath(root.id(), candidate, root.access());
    }

    @Override
    public boolean exists(String rootId, Path requested) {
        ResolvedSandboxPath resolved = resolve(rootId, requested);
        return Files.exists(resolved.path());
    }

    @Override
    public void validateRead(String rootId, Path requested) {
        ResolvedSandboxPath resolved = resolve(rootId, requested);

        if (!resolved.access().readable()) {
            throw new FilesystemAccessException("Read access denied for root: " + rootId);
        }

        if (Files.exists(resolved.path()) && !Files.isReadable(resolved.path())) {
            throw new FilesystemAccessException("Path is not readable: " + resolved.path());
        }
    }

    @Override
    public void validateWrite(String rootId, Path requested) {
        ResolvedSandboxPath resolved = resolve(rootId, requested);

        if (!resolved.access().writable()) {
            throw new FilesystemAccessException("Write access denied for root: " + rootId);
        }

        validateWritableLocation(resolved.path());
    }

    private FilesystemRoot requireRoot(String rootId) {
        FilesystemRoot root = roots.get(rootId);
        if (root == null) {
            throw new FilesystemAccessException("Unknown filesystem root: " + rootId);
        }
        return root;
    }

    private void validateWritableLocation(Path path) {
        Path existing = path;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }

        if (existing != null && Files.isSymbolicLink(existing)) {
            throw new FilesystemAccessException("Write through symbolic link is denied: " + path);
        }
    }

    private void validateSymlinkBoundary(Path root, Path candidate, boolean followSymlinks) {
        if (followSymlinks) {
            return;
        }

        Path current = root;
        Path relative = root.relativize(candidate);

        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new FilesystemAccessException("Symbolic link access denied: " + current);
            }
        }
    }
}

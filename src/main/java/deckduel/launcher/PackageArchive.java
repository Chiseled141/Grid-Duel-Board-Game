package deckduel.launcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unpacks an update package into a new folder, refusing anything unsafe: entries with absolute
 * paths, {@code ..}, folders or backslashes, names the manifest does not list, duplicates, and
 * content larger than {@code maxBytes}. Every extracted file's SHA-256 must match the manifest.
 */
public final class PackageArchive {
    /** Guards against a package that expands without limit. */
    public static final long DEFAULT_MAX_BYTES = 1L << 30;
    private static final int MAX_MANIFEST_BYTES = 16 * 1024;

    private PackageArchive() { }

    /** Reads only the manifest of a package. */
    public static PackageManifest manifest(Path zip) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; )
                if (entry.getName().equals(PackageManifest.NAME)) return PackageManifest.read(new ByteArrayInputStream(readSmall(in)));
        }
        throw new IOException("The package has no " + PackageManifest.NAME);
    }

    /**
     * Extracts {@code zip} into {@code target}, which must not exist yet. On any failure the
     * partly written folder is removed and nothing else is touched.
     */
    public static PackageManifest extract(Path zip, Path target, long maxBytes) throws IOException {
        if (Files.exists(target)) throw new IOException("Refusing to extract over " + target);
        PackageManifest manifest = manifest(zip);
        Files.createDirectories(target);
        try {
            Set<String> seen = new HashSet<>();
            long total = 0;
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
                for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                    String name = entry.getName();
                    checkName(name);
                    if (!seen.add(name)) throw new IOException("Duplicate package entry " + name);
                    if (!name.equals(PackageManifest.NAME) && !manifest.files().containsKey(name))
                        throw new IOException("Unexpected package entry " + name);
                    Path out = target.resolve(name).normalize();
                    if (!out.getParent().equals(target.normalize())) throw new IOException("Unsafe package entry " + name);
                    total += copy(in, out, maxBytes - total);
                }
            }
            for (var file : manifest.files().entrySet()) {
                Path extracted = target.resolve(file.getKey());
                if (!Files.isRegularFile(extracted)) throw new IOException("The package is missing " + file.getKey());
                if (!Hashes.sha256(extracted).equals(file.getValue()))
                    throw new IOException("Checksum mismatch for " + file.getKey());
            }
            if (!Files.isRegularFile(target.resolve(PackageManifest.NAME))) throw new IOException("The package has no manifest");
            return manifest;
        } catch (IOException | RuntimeException failure) {
            deleteTree(target);
            throw failure;
        }
    }

    private static void checkName(String name) throws IOException {
        if (name.isEmpty() || name.endsWith("/") || name.contains("/") || name.contains("\\") || name.contains(":")
                || name.startsWith(".") || name.equals("..") || name.contains("\0"))
            throw new IOException("Unsafe package entry " + name);
    }

    private static long copy(InputStream in, Path out, long room) throws IOException {
        long written = 0;
        byte[] buffer = new byte[64 * 1024];
        try (OutputStream file = Files.newOutputStream(out, java.nio.file.StandardOpenOption.CREATE_NEW)) {
            for (int read; (read = in.read(buffer)) > 0; ) {
                written += read;
                if (written > room) throw new IOException("The package expands beyond its size limit");
                file.write(buffer, 0, read);
            }
        }
        return written;
    }

    private static byte[] readSmall(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int read; (read = in.read(buffer)) > 0; ) {
            bytes.write(buffer, 0, read);
            if (bytes.size() > MAX_MANIFEST_BYTES) throw new IOException("The package manifest is too large");
        }
        return bytes.toByteArray();
    }

    /** Deletes a folder and everything in it, if it exists. */
    public static void deleteTree(Path folder) throws IOException {
        if (!Files.exists(folder)) return;
        try (var paths = Files.walk(folder)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}

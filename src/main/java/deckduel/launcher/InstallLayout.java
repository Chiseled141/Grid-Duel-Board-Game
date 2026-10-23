package deckduel.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A per-user, writable installation:
 * <pre>
 * root/current.properties      which version is active, the previous one, unconfirmed launches
 * root/versions/&lt;version&gt;/     one folder per installed release, never changed once complete
 * root/launcher/                the stable copy of the launcher that shortcuts start
 * root/staging/                 downloads in progress and verified packages
 * root/logs/                    launcher and client output
 * </pre>
 * New releases go into a new folder, so files of the running version are never overwritten and
 * Windows file locks do not matter; activation only replaces the small pointer file atomically.
 * Preferences and match data live elsewhere ({@code ~/.deckduel}) and are never touched.
 */
public final class InstallLayout {
    private static final Logger LOG = Logger.getLogger(InstallLayout.class.getName());
    static final String POINTER = "current.properties";
    /** Written by a client once it has started properly; a version without it may be rolled back. */
    public static final String STARTED_OK = ".started-ok";
    /** Launches a new version may take without confirming a start before it is rolled back. */
    static final int MAX_UNCONFIRMED_LAUNCHES = 2;

    /** Which version starts, the one before it, and launches since activation without a confirmed start. */
    public record Pointer(String active, String previous, int launches, String failed) { }

    private final Path root;

    /** An installation rooted at {@code root}. */
    public InstallLayout(Path root) { this.root = root.toAbsolutePath().normalize(); }

    /** The conventional per-user location for this platform. */
    public static Path defaultRoot() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.startsWith("windows")) {
            String local = System.getenv("LOCALAPPDATA");
            return Path.of(local != null ? local : home, "DeckDuel");
        }
        if (os.startsWith("mac")) return Path.of(home, "Library", "Application Support", "DeckDuel");
        String data = System.getenv("XDG_DATA_HOME");
        return data != null && !data.isBlank() ? Path.of(data, "deckduel") : Path.of(home, ".local", "share", "deckduel");
    }

    public Path root() { return root; }
    public Path versions() { return root.resolve("versions"); }
    public Path staging() { return root.resolve("staging"); }
    public Path logs() { return root.resolve("logs"); }
    /** The launcher copy that shortcuts start. */
    public Path stableLauncher() { return root.resolve("launcher").resolve(PackageManifest.LAUNCHER_JAR); }

    /** An installed version's folder. */
    public Path versionDir(String version) {
        ReleaseVersion.parse(version);
        return versions().resolve(version);
    }

    /** The current pointer, or empty before the first installation. */
    public Optional<Pointer> pointer() throws IOException {
        Path file = root.resolve(POINTER);
        if (!Files.isRegularFile(file)) return Optional.empty();
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        String active = properties.getProperty("active");
        if (active == null || !ReleaseVersion.isValid(active)) throw new IOException("The installation pointer is damaged");
        String previous = properties.getProperty("previous");
        int launches = Integer.parseInt(properties.getProperty("launches", "0"));
        return Optional.of(new Pointer(active, ReleaseVersion.isValid(previous) ? previous : null, launches,
                properties.getProperty("failed")));
    }

    /** Replaces the pointer in one step: a crash leaves either the old or the new file, never half of one. */
    void writePointer(Pointer pointer) throws IOException {
        Files.createDirectories(root);
        Properties properties = new Properties();
        properties.setProperty("active", pointer.active());
        if (pointer.previous() != null) properties.setProperty("previous", pointer.previous());
        properties.setProperty("launches", String.valueOf(pointer.launches()));
        if (pointer.failed() != null) properties.setProperty("failed", pointer.failed());
        Path temporary = Files.createTempFile(root, "current", ".tmp");
        try (OutputStream out = Files.newOutputStream(temporary)) {
            properties.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), "Deck Duel installation");
        }
        moveReplacing(temporary, root.resolve(POINTER));
    }

    /**
     * Installs a package's files into {@code versions/<version>} without activating them. A
     * complete earlier copy that still verifies is reused; anything else is extracted afresh
     * through a temporary folder, so a half-written version folder never appears.
     */
    public PackageManifest installPackage(Path zip) throws IOException {
        PackageManifest manifest = PackageArchive.manifest(zip);
        Path target = versionDir(manifest.version());
        if (Files.isDirectory(target)) {
            try {
                verify(manifest.version());
                return manifest;
            } catch (IOException damaged) {
                if (isActive(manifest.version())) throw new IOException("The active version's files are damaged", damaged);
                LOG.info(() -> "replacing an incomplete copy of " + manifest.version());
                PackageArchive.deleteTree(target);
            }
        }
        Path incoming = versions().resolve(manifest.version() + ".incoming");
        PackageArchive.deleteTree(incoming);
        Files.createDirectories(versions());
        PackageArchive.extract(zip, incoming, PackageArchive.DEFAULT_MAX_BYTES);
        moveReplacing(incoming, target);
        return manifest;
    }

    /** Checks every file of an installed version against its manifest. */
    public PackageManifest verify(String version) throws IOException {
        Path folder = versionDir(version);
        PackageManifest manifest;
        try (InputStream in = Files.newInputStream(folder.resolve(PackageManifest.NAME))) {
            manifest = PackageManifest.read(in);
        }
        if (!manifest.version().equals(version)) throw new IOException("Version folder " + version + " holds " + manifest.version());
        for (var file : manifest.files().entrySet()) {
            Path installed = folder.resolve(file.getKey());
            if (!Files.isRegularFile(installed) || !Hashes.sha256(installed).equals(file.getValue()))
                throw new IOException("Installed file " + file.getKey() + " of " + version + " does not match its checksum");
        }
        return manifest;
    }

    /**
     * Makes an installed, verified version the one that starts, keeping the current one as the
     * rollback target, then refreshes the stable launcher and removes older versions.
     */
    public void activate(String version) throws IOException {
        verify(version);
        Optional<Pointer> current = pointer();
        String previous = current.map(Pointer::active).filter(active -> !active.equals(version))
                .orElse(current.map(Pointer::previous).orElse(null));
        writePointer(new Pointer(version, previous, 0, null));
        refreshLauncher(version);
        prune();
        LOG.info(() -> "activated " + version + (previous == null ? "" : ", keeping " + previous + " for rollback"));
    }

    /**
     * Chooses the version to start and counts the launch. A version that has been launched
     * {@link #MAX_UNCONFIRMED_LAUNCHES} times without confirming a start is rolled back to the
     * previous version when that one still verifies.
     */
    public String chooseForLaunch() throws IOException {
        Pointer pointer = pointer().orElseThrow(() -> new IOException("Deck Duel is not installed in " + root));
        if (confirmed(pointer.active())) return pointer.active();
        if (pointer.launches() >= MAX_UNCONFIRMED_LAUNCHES && pointer.previous() != null) {
            try {
                verify(pointer.previous());
                writePointer(new Pointer(pointer.previous(), null, 0, pointer.active()));
                LOG.warning(() -> pointer.active() + " never started properly; rolled back to " + pointer.previous());
                return pointer.previous();
            } catch (IOException damaged) {
                LOG.log(Level.WARNING, "cannot roll back to " + pointer.previous(), damaged);
            }
        }
        writePointer(new Pointer(pointer.active(), pointer.previous(), pointer.launches() + 1, pointer.failed()));
        return pointer.active();
    }

    /** Whether a version has confirmed a proper start. */
    public boolean confirmed(String version) { return Files.exists(versionDir(version).resolve(STARTED_OK)); }

    /** Records that this version started properly, so it is never rolled back. */
    public void confirm(String version) throws IOException {
        Path marker = versionDir(version).resolve(STARTED_OK);
        if (Files.isDirectory(marker.getParent()) && !Files.exists(marker)) Files.createFile(marker);
    }

    private boolean isActive(String version) throws IOException {
        return pointer().map(p -> p.active().equals(version)).orElse(false);
    }

    /** Copies the activated version's launcher over the stable one; a locked copy keeps working. */
    private void refreshLauncher(String version) {
        try {
            Files.createDirectories(stableLauncher().getParent());
            Path temporary = Files.createTempFile(stableLauncher().getParent(), "launcher", ".tmp");
            Files.copy(versionDir(version).resolve(PackageManifest.LAUNCHER_JAR), temporary, StandardCopyOption.REPLACE_EXISTING);
            moveReplacing(temporary, stableLauncher());
        } catch (IOException locked) {
            LOG.log(Level.WARNING, "kept the previous launcher copy", locked);
        }
    }

    /** Removes version folders other than the active and previous ones, and leftover temporary folders. */
    void prune() throws IOException {
        Optional<Pointer> pointer = pointer();
        if (pointer.isEmpty() || !Files.isDirectory(versions())) return;
        try (var folders = Files.list(versions())) {
            for (Path folder : folders.toList()) {
                String name = folder.getFileName().toString();
                if (name.equals(pointer.get().active()) || name.equals(pointer.get().previous())) continue;
                try {
                    PackageArchive.deleteTree(folder);
                } catch (IOException inUse) {
                    LOG.log(Level.FINE, "could not remove " + folder, inUse);
                }
            }
        }
    }

    private static void moveReplacing(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

package deckduel.client.update;

import deckduel.client.startup.ReleasePackage;
import deckduel.launcher.Hashes;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.DoubleConsumer;
import java.util.logging.Logger;

/**
 * Downloads an update package into {@code staging}. Bytes go to {@code <name>.part}, which an
 * interrupted download resumes with an HTTP Range request; only a file whose size and SHA-256
 * match the signed metadata is renamed to its final name. A wrong file is deleted, never used.
 */
public final class PackageDownloader {
    private static final Logger LOG = Logger.getLogger(PackageDownloader.class.getName());
    /** No package may exceed this, whatever the metadata says. */
    static final long MAX_PACKAGE_BYTES = 768L << 20;
    private final int attempts;
    private final long retryDelayMillis;

    /** Tries up to {@code attempts} times, waiting {@code retryDelayMillis} times the attempt number between tries. */
    public PackageDownloader(int attempts, long retryDelayMillis) {
        this.attempts = attempts;
        this.retryDelayMillis = retryDelayMillis;
    }

    /** Returns the verified package file, downloading or resuming it as needed. */
    public Path download(ReleasePackage release, Path staging, DoubleConsumer progress) throws IOException {
        if (release.size() > MAX_PACKAGE_BYTES) throw new IOException("The update package is larger than allowed");
        Files.createDirectories(staging);
        String name = "deck-duel-" + release.version() + "-" + release.platform() + ".zip";
        Path done = staging.resolve(name), part = staging.resolve(name + ".part");
        if (Files.isRegularFile(done) && matches(done, release)) { progress.accept(1); return done; }
        Files.deleteIfExists(done);
        IOException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                fetch(release, part, progress);
                if (!matches(part, release)) {
                    Files.deleteIfExists(part);
                    throw new IOException("The downloaded update did not match its checksum and was discarded.");
                }
                Files.move(part, done, StandardCopyOption.REPLACE_EXISTING);
                return done;
            } catch (IOException failure) {
                last = failure;
                int tried = attempt;
                LOG.info(() -> "update download attempt " + tried + " failed: " + failure.getMessage());
                if (attempt < attempts) pause(retryDelayMillis * attempt);
            }
        }
        throw last;
    }

    private void fetch(ReleasePackage release, Path part, DoubleConsumer progress) throws IOException {
        long have = Files.isRegularFile(part) ? Files.size(part) : 0;
        if (have > release.size()) { Files.delete(part); have = 0; }
        if (have == release.size()) { progress.accept(1); return; }
        HttpURLConnection connection = Http.open(release.url(), have);
        try {
            int status = Http.status(connection, release.url());
            boolean resumed = status == 206;
            if (status == 416 && have > 0) { Files.delete(part); throw new IOException("The partial download could not be resumed."); }
            if (status != 200 && !resumed) throw new IOException(Http.describe(status, release.url()));
            // A server that ignores Range sends the whole file again.
            if (!resumed) have = 0;
            long expected = release.size() - have, length = connection.getContentLengthLong();
            if (length > expected) throw new IOException("The update server sent more data than the package size.");
            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(part, StandardOpenOption.CREATE,
                         resumed ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[64 * 1024];
                long written = have;
                for (int read; (read = in.read(buffer)) > 0; ) {
                    written += read;
                    if (written > release.size()) throw new IOException("The update server sent more data than the package size.");
                    out.write(buffer, 0, read);
                    progress.accept(written / (double) release.size());
                }
                if (written < release.size()) throw new IOException("The download stopped early; it will resume where it left off.");
            }
        } catch (java.net.SocketTimeoutException slow) {
            throw new IOException("The download stalled; it will resume where it left off.", slow);
        } finally {
            connection.disconnect();
        }
    }

    private static boolean matches(Path file, ReleasePackage release) throws IOException {
        return Files.size(file) == release.size() && Hashes.sha256(file).equals(release.sha256());
    }

    private static void pause(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("The update was cancelled.", interrupted);
        }
    }
}

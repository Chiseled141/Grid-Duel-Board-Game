package deckduel.server;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Operator commands over a local Unix domain socket (for example {@code /run/deckduel/control.sock}).
 * Only accounts that the file permissions allow (the service user and its group) can connect;
 * nothing listens on the network. One line in, a short text answer out:
 * {@code status}, {@code drain}, {@code resume}, {@code reload-tls}.
 */
final class ControlServer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ControlServer.class.getName());
    private static final int MAX_COMMAND_BYTES = 64;

    private final ServerSocketChannel channel;
    private final Path path;
    private final GameServer server;

    private ControlServer(ServerSocketChannel channel, Path path, GameServer server) {
        this.channel = channel;
        this.path = path;
        this.server = server;
    }

    /** Binds the socket (replacing a stale one left by a crash) and starts answering. */
    static ControlServer start(Path path, GameServer server) throws IOException {
        if (Files.exists(path) && !Files.isRegularFile(path) && !Files.isDirectory(path)) Files.delete(path);
        if (Files.exists(path)) throw new IOException(path + " exists and is not a socket");
        Files.createDirectories(path.toAbsolutePath().getParent());
        ServerSocketChannel channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        channel.bind(UnixDomainSocketAddress.of(path));
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-rw----"));
        } catch (UnsupportedOperationException windows) {
            // Not a POSIX file system; the containing folder's permissions apply.
        }
        ControlServer control = new ControlServer(channel, path, server);
        Thread acceptor = new Thread(control::accept, "deckduel-control");
        acceptor.setDaemon(true);
        acceptor.start();
        LOG.info(() -> "operator control socket " + path);
        return control;
    }

    private void accept() {
        while (channel.isOpen()) {
            try {
                SocketChannel client = channel.accept();
                Thread handler = new Thread(() -> answer(client), "deckduel-control-command");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException closed) {
                if (channel.isOpen()) LOG.log(Level.WARNING, "control socket accept failed", closed);
            }
        }
    }

    private void answer(SocketChannel client) {
        try (client) {
            ByteBuffer buffer = ByteBuffer.allocate(MAX_COMMAND_BYTES);
            while (buffer.hasRemaining() && client.read(buffer) > 0) {
                if (new String(buffer.array(), 0, buffer.position(), StandardCharsets.US_ASCII).contains("\n")) break;
            }
            String command = new String(buffer.array(), 0, buffer.position(), StandardCharsets.US_ASCII).trim();
            String reply = execute(command);
            client.write(ByteBuffer.wrap((reply + "\n").getBytes(StandardCharsets.UTF_8)));
        } catch (IOException dropped) {
            LOG.log(Level.FINE, "control client went away", dropped);
        }
    }

    /** Runs one command and returns the answer; the first word is "ok" or "error". */
    String execute(String command) {
        LOG.info(() -> "operator command: " + command);
        try {
            switch (command) {
                case "status" -> { return "ok " + server.status().describe(); }
                case "drain" -> { server.drain(); return "ok " + server.status().describe(); }
                case "resume" -> { server.resume(); return "ok " + server.status().describe(); }
                case "reload-tls" -> {
                    TlsMaterial tls = server.config().tls();
                    if (tls == null) return "error TLS is not configured";
                    tls.reload();
                    return "ok certificateExpires=" + tls.certificate().getNotAfter().toInstant();
                }
                default -> { return "error unknown command '" + command + "'; use status, drain, resume or reload-tls"; }
            }
        } catch (IOException | RuntimeException failure) {
            return "error " + failure.getMessage();
        }
    }

    @Override public void close() {
        try {
            channel.close();
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Shutting down; a stale socket file is replaced at the next start.
        }
    }
}

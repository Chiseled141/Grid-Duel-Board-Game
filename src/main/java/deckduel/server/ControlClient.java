package deckduel.server;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * The operator's side of the control socket: {@code ctl status|drain|resume|reload-tls}, and
 * {@code ctl wait-safe}, which polls until a restart would lose no match (exit 0) or the timeout
 * passes (exit 3). Deployment scripts use {@code wait-safe} to refuse an unsafe restart.
 */
final class ControlClient {
    static final Path DEFAULT_SOCKET = Path.of("/run/deckduel/control.sock");

    private ControlClient() { }

    static int run(String[] args) throws IOException, InterruptedException {
        if (args.length == 0) throw new IllegalArgumentException("Name a command: status, drain, resume, reload-tls or wait-safe");
        String command = args[0];
        Path socket = DEFAULT_SOCKET;
        long timeoutSeconds = 3600;
        for (int i = 1; i + 1 < args.length; i += 2) {
            if (args[i].equals("--socket")) socket = Path.of(args[i + 1]);
            else if (args[i].equals("--timeout")) timeoutSeconds = Long.parseLong(args[i + 1]);
            else throw new IllegalArgumentException("Unknown option " + args[i]);
        }
        if (command.equals("wait-safe")) return waitSafe(socket, timeoutSeconds);
        String reply = send(socket, command);
        System.out.println(reply);
        return reply.startsWith("ok") ? 0 : 1;
    }

    /** Sends one command and returns the server's one-line answer. */
    static String send(Path socket, String command) throws IOException {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socket));
            channel.write(ByteBuffer.wrap((command + "\n").getBytes(StandardCharsets.US_ASCII)));
            channel.shutdownOutput();
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) { /* read the whole answer */ }
            return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8).trim();
        } catch (java.net.SocketException | java.nio.channels.ClosedChannelException unreachable) {
            throw new IOException("Cannot reach the server's control socket " + socket + " (is the service running, and are you in its group?)", unreachable);
        }
    }

    private static int waitSafe(Path socket, long timeoutSeconds) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
        String last = "";
        while (System.currentTimeMillis() < deadline) {
            last = send(socket, "status");
            if (last.contains("safeToRestart=true")) { System.out.println(last); return 0; }
            if (!last.contains("state=draining")) {
                System.out.println(last);
                System.err.println("The server is not draining; run 'ctl drain' first.");
                return 3;
            }
            System.out.println(last);
            Thread.sleep(Math.min(10_000, Math.max(200, deadline - System.currentTimeMillis())));
        }
        System.err.println("Not safe to restart yet: " + last);
        return 3;
    }
}

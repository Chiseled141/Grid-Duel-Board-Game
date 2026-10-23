package deckduel.client;

import deckduel.client.startup.InstalledVersion;
import deckduel.client.startup.ServerRoute;
import deckduel.client.startup.UpdateRequiredException;
import deckduel.client.state.ServerConnection;
import deckduel.net.Message;
import deckduel.net.UpdateRequired;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The client's network endpoint: one persistent TCP connection (TLS for every remote server) with a reader
 * thread and a writer thread. Incoming messages are handed to {@code receiver}
 * on the reader thread (the caller forwards them to the Swing EDT); outgoing
 * messages are enqueued by {@link #send(Message)} — never blocking the caller —
 * and written by the single writer thread (single-writer rule). The UI never
 * performs socket I/O itself.
 */
public final class DeckDuelClient implements AutoCloseable, ServerConnection {

    private static final Logger LOG = Logger.getLogger(DeckDuelClient.class.getName());
    /** How long connecting, the TLS handshake and the greeting may each take. */
    public static final int HANDSHAKE_TIMEOUT_MILLIS = 10_000;
    /** Heartbeat interval while the connection is idle. */
    private static final int PING_INTERVAL_SECONDS = 30;
    /** A peer silent for this long is considered gone. */
    private static final int SILENCE_LIMIT_SECONDS = 90;

    private final LinkedBlockingQueue<Message> outgoing = new LinkedBlockingQueue<>();
    private final Consumer<Message> receiver;
    private final Consumer<String> connectionLostHandler;
    private final SSLContext tls;
    private final String clientVersion;
    private final int timeoutMillis;
    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "deckduel-client-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    private volatile Socket socket;
    private volatile boolean running;
    // Each connection's reader and writer stop once a newer connection replaces it.
    private volatile long generation;
    private volatile long lastReceivedAt;
    private volatile java.util.concurrent.ScheduledFuture<?> heartbeatTask;

    /**
     * Creates a client that trusts the Java runtime's certificate authorities.
     *
     * @param receiver called on the reader thread for every incoming message
     * @param connectionLostHandler called once when the connection drops
     *        unexpectedly (not on a deliberate {@link #close()})
     */
    public DeckDuelClient(Consumer<Message> receiver, Consumer<String> connectionLostHandler) {
        this(receiver, connectionLostHandler, ClientTls.systemTrust(), InstalledVersion.current(), HANDSHAKE_TIMEOUT_MILLIS);
    }

    /**
     * Creates a client.
     *
     * @param tls the trusted certificate authorities for TLS routes
     * @param clientVersion this client's release version, sent before login
     * @param timeoutMillis how long connecting, the TLS handshake and the protocol greeting may each take
     */
    public DeckDuelClient(Consumer<Message> receiver, Consumer<String> connectionLostHandler, SSLContext tls,
                          String clientVersion, int timeoutMillis) {
        this.receiver = receiver;
        this.connectionLostHandler = connectionLostHandler;
        this.tls = tls;
        this.clientVersion = clientVersion;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Connects to the server, or does nothing when already connected. A TLS route is verified
     * (trusted certificate, matching host name) before anything is sent; a plain route exists
     * only for this computer. Every failure closes the socket before it is reported.
     *
     * @throws UpdateRequiredException if the server requires a newer client
     * @throws IOException if the connection cannot be established
     */
    public synchronized void ensureConnected(ServerRoute route) throws IOException {
        if (isConnected()) {
            return;
        }
        Socket newSocket = route.tls() ? tls.getSocketFactory().createSocket() : new Socket();
        ObjectOutputStream out;
        ObjectInputStream in;
        try {
            newSocket.connect(new InetSocketAddress(route.host(), route.port()), timeoutMillis);
            newSocket.setSoTimeout(timeoutMillis);
            if (newSocket instanceof SSLSocket secure) {
                try {
                    handshake(secure, route.host());
                } catch (SSLException | SocketTimeoutException reported) {
                    throw reported;
                } catch (IOException dropped) {
                    // A peer that is not a TLS server usually just drops the connection.
                    throw new SSLException("the server did not complete a TLS handshake", dropped);
                }
            }
            out = new ObjectOutputStream(newSocket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(newSocket.getInputStream());
            // Reject any serialized class outside the protocol allowlist before
            // readObject can instantiate it (deserialization gadget defense).
            in.setObjectInputFilter(Message.WIRE_FILTER);
            greet(in, out);
            newSocket.setSoTimeout(0);
        } catch (IOException | RuntimeException failure) {
            closeQuietly(newSocket);
            throw explain(failure, route);
        }

        socket = newSocket;
        // Messages queued for a dropped connection are stale; the server needs a fresh login first.
        outgoing.clear();
        long current = ++generation;
        running = true;
        lastReceivedAt = System.currentTimeMillis();
        Thread reader = new Thread(() -> readLoop(in, current), "deckduel-client-reader");
        Thread writer = new Thread(() -> writeLoop(out, current), "deckduel-client-writer");
        reader.setDaemon(true);
        writer.setDaemon(true);
        reader.start();
        writer.start();
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        heartbeatTask = heartbeat.scheduleWithFixedDelay(this::checkLiveness,
                PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
        LOG.info(() -> "connected to " + route.describe());
    }

    /** Verifies the server certificate and host name, offering TLS 1.3 and 1.2 only. */
    private static void handshake(SSLSocket secure, String host) throws IOException {
        SSLParameters parameters = secure.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        parameters.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
        // Server Name Indication carries DNS names only, never IP literals.
        if (!host.matches("[0-9.]+|\\[?[0-9a-fA-F]*:[0-9a-fA-F:]+]?")) parameters.setServerNames(List.of(new SNIHostName(host)));
        secure.setSSLParameters(parameters);
        secure.startHandshake();
    }

    /** The protocol greeting: the server's banner, our hello with our version, its answer. */
    private void greet(ObjectInputStream in, ObjectOutputStream out) throws IOException {
        try {
            Object greetingObj = in.readObject();
            if (!(greetingObj instanceof deckduel.net.ErrorMessage greeting) || !Message.GREETING.equals(greeting.text())) {
                throw new IOException("server handshake failed: expected " + Message.GREETING + " greeting, got " + greetingObj);
            }
            out.reset();
            out.writeObject(new deckduel.net.ProtocolHello(Message.PROTOCOL_VERSION, clientVersion));
            out.flush();
            Object reply = in.readObject();
            if (reply instanceof UpdateRequired required)
                throw new UpdateRequiredException(required.minimumVersion(), required.text());
            if (!(reply instanceof deckduel.net.ProtocolReady ready) || ready.version() != Message.PROTOCOL_VERSION) {
                throw new IOException("server handshake failed: expected ProtocolReady(" + Message.PROTOCOL_VERSION + "), got " + reply);
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("protocol error during handshake", e);
        }
    }

    /** Turns low-level failures into reasons a player can understand, keeping the cause. */
    static IOException explain(Exception failure, ServerRoute route) {
        if (failure instanceof UpdateRequiredException required) return required;
        String server = route.host() + ":" + route.port();
        String reason;
        if (failure instanceof SocketTimeoutException) reason = server + " did not answer in time";
        else if (failure instanceof java.net.ConnectException) reason = "nothing is accepting connections at " + server;
        else if (failure instanceof java.net.UnknownHostException) reason = "cannot find " + route.host();
        else if (failure instanceof SSLException) reason = "the secure connection to " + server + " was refused: " + certificateProblem(failure);
        else return failure instanceof IOException io ? io : new IOException(failure.getMessage(), failure);
        return new IOException(reason, failure);
    }

    private static String certificateProblem(Exception failure) {
        // Expiry sits deepest in the cause chain, under the generic path-validation failure, so look for it first.
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.security.cert.CertificateExpiredException) return "the server's certificate has expired";
            if (cause instanceof java.security.cert.CertificateNotYetValidException) return "the server's certificate is not valid yet";
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String text = String.valueOf(cause.getMessage());
            if (text.contains("No subject alternative") || text.contains("No name matching"))
                return "the server's certificate is for a different host name";
            if (text.contains("PKIX") || text.contains("unable to find valid certification path"))
                return "the server's certificate is not issued by a trusted authority";
        }
        return "the TLS handshake failed (" + failure.getMessage() + ")";
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already failed; nothing else to release.
        }
    }

    /** Pings the server and drops the connection when it stayed silent too long. */
    private void checkLiveness() {
        if (!isConnected()) {
            return;
        }
        if (System.currentTimeMillis() - lastReceivedAt
                > SILENCE_LIMIT_SECONDS * 1000L) {
            LOG.info("server silent for too long, dropping the connection");
            connectionLostHandler.accept("no response from server for "
                    + SILENCE_LIMIT_SECONDS + " seconds");
            close();
            return;
        }
        outgoing.offer(new deckduel.net.Ping(System.currentTimeMillis()));
    }

    /** Returns true while the socket is open. */
    public boolean isConnected() {
        Socket current = socket;
        return current != null && current.isConnected() && !current.isClosed() && running;
    }

    /**
     * Enqueues a message for the writer thread. Returns immediately; when not
     * connected the message is dropped (the caller is told via the connection
     * handler soon after).
     */
    public void send(Message message) {
        if (isConnected()) {
            outgoing.offer(message);
        }
    }

    /** Closes the socket and stops both threads. Safe to call repeatedly. */
    @Override
    public synchronized void close() {
        running = false;
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        Socket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException e) {
                LOG.log(Level.FINE, "closing socket failed", e);
            }
        }
    }

    private boolean active(long connection) { return running && generation == connection; }

    private void readLoop(ObjectInputStream in, long connection) {
        try {
            while (active(connection)) {
                Object raw = in.readObject();
                lastReceivedAt = System.currentTimeMillis();
                if (raw instanceof Message message) {
                    receiver.accept(message);
                }
            }
        } catch (Exception e) {
            if (active(connection)) {
                LOG.log(Level.INFO, e, () -> "connection lost");
                // Close first so isConnected() is false before the model reacts.
                close();
                connectionLostHandler.accept(String.valueOf(e.getMessage()));
            }
        }
    }

    private void writeLoop(ObjectOutputStream out, long connection) {
        try {
            while (active(connection)) {
                Message message = outgoing.poll(500, TimeUnit.MILLISECONDS);
                if (message != null) {
                    // Full snapshot per message, same reason as on the server:
                    // without reset(), repeated object references would arrive
                    // as stale back-handles on this end.
                    out.reset();
                    out.writeObject(message);
                    out.flush();
                }
            }
        } catch (Exception e) {
            if (active(connection)) {
                LOG.log(Level.INFO, e, () -> "writer stopped");
            }
        }
    }
}

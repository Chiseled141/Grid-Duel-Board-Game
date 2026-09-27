package onitama.client;

import onitama.client.state.ServerConnection;
import onitama.net.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The client's network endpoint: one persistent TCP connection with a reader
 * thread and a writer thread. Incoming messages are handed to {@code receiver}
 * on the reader thread (the caller forwards them to the Swing EDT); outgoing
 * messages are enqueued by {@link #send(Message)} — never blocking the caller —
 * and written by the single writer thread (single-writer rule). The UI never
 * performs socket I/O itself.
 */
public final class OnitamaClient implements AutoCloseable, ServerConnection {

    private static final Logger LOG = Logger.getLogger(OnitamaClient.class.getName());
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    /** Heartbeat interval while the connection is idle. */
    private static final int PING_INTERVAL_SECONDS = 30;
    /** A peer silent for this long is considered gone. */
    private static final int SILENCE_LIMIT_SECONDS = 90;

    private final LinkedBlockingQueue<Message> outgoing = new LinkedBlockingQueue<>();
    private final Consumer<Message> receiver;
    private final Consumer<String> connectionLostHandler;
    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "onitama-client-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    private volatile Socket socket;
    private volatile boolean running;
    private volatile long lastReceivedAt;
    private volatile java.util.concurrent.ScheduledFuture<?> heartbeatTask;

    /**
     * Creates a client.
     *
     * @param receiver called on the reader thread for every incoming message
     * @param connectionLostHandler called once when the connection drops
     *        unexpectedly (not on a deliberate {@link #close()})
     */
    public OnitamaClient(Consumer<Message> receiver, Consumer<String> connectionLostHandler) {
        this.receiver = receiver;
        this.connectionLostHandler = connectionLostHandler;
    }

    /**
     * Connects to the server, or does nothing when already connected.
     *
     * @throws IOException if the connection cannot be established
     */
    public synchronized void ensureConnected(String host, int port) throws IOException {
        if (isConnected()) {
            return;
        }
        Socket newSocket = new Socket();
        newSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        ObjectOutputStream out = new ObjectOutputStream(newSocket.getOutputStream());
        out.flush();
        ObjectInputStream in = new ObjectInputStream(newSocket.getInputStream());
        socket = newSocket;
        running = true;
        lastReceivedAt = System.currentTimeMillis();
        Thread reader = new Thread(() -> readLoop(in), "onitama-client-reader");
        Thread writer = new Thread(() -> writeLoop(out), "onitama-client-writer");
        reader.setDaemon(true);
        writer.setDaemon(true);
        reader.start();
        writer.start();
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        heartbeatTask = heartbeat.scheduleWithFixedDelay(this::checkLiveness,
                PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
        LOG.info(() -> "connected to " + host + ":" + port);
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
        outgoing.offer(new onitama.net.Ping(System.currentTimeMillis()));
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

    private void readLoop(ObjectInputStream in) {
        try {
            while (running) {
                Object raw = in.readObject();
                lastReceivedAt = System.currentTimeMillis();
                if (raw instanceof Message message) {
                    receiver.accept(message);
                }
            }
        } catch (Exception e) {
            if (running) {
                LOG.log(Level.INFO, "connection lost: " + e);
                connectionLostHandler.accept(String.valueOf(e.getMessage()));
            }
        }
    }

    private void writeLoop(ObjectOutputStream out) {
        try {
            while (running) {
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
            if (running) {
                LOG.log(Level.INFO, "writer stopped: " + e);
            }
        }
    }
}

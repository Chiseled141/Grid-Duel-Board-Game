package onitama.client;

import onitama.client.state.ServerConnection;
import onitama.net.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
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

    private final LinkedBlockingQueue<Message> outgoing = new LinkedBlockingQueue<>();
    private final Consumer<Message> receiver;
    private final Consumer<String> connectionLostHandler;

    private volatile Socket socket;
    private volatile boolean running;

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
        Thread reader = new Thread(() -> readLoop(in), "onitama-client-reader");
        Thread writer = new Thread(() -> writeLoop(out), "onitama-client-writer");
        reader.setDaemon(true);
        writer.setDaemon(true);
        reader.start();
        writer.start();
        LOG.info(() -> "connected to " + host + ":" + port);
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

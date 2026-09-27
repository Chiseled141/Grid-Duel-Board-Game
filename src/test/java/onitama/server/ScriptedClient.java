package onitama.server;

import onitama.net.Message;
import onitama.net.RegisterRequest;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Headless test client: a raw socket speaking the object protocol, with a
 * blocking {@code expect} that fails the test on timeout or on the wrong
 * message type. Used by the integration tests and, later, by the load-test
 * bots (which use their own production-class client).
 */
final class ScriptedClient implements AutoCloseable {

    private static final int READ_TIMEOUT_MILLIS = 10_000;

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    ScriptedClient(int port) throws IOException {
        socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(READ_TIMEOUT_MILLIS);
        // Both ends must create the output stream first and flush the header.
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
    }

    /** Sends one message. */
    void send(Message message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            fail("sending " + message + " failed: " + e);
        }
    }

    /** Registers an account, asserting success. */
    void register(String username, String password) {
        send(new RegisterRequest(username, password));
        expectLoginOk();
    }

    /** Blocks until a message of the given type arrives; fails on anything else. */
    <T extends Message> T expect(Class<T> type) {
        Object raw;
        try {
            raw = in.readObject();
        } catch (SocketTimeoutException e) {
            return fail("timed out waiting for " + type.getSimpleName());
        } catch (Exception e) {
            return fail("connection error while waiting for " + type.getSimpleName() + ": " + e);
        }
        if (!type.isInstance(raw)) {
            return fail("expected " + type.getSimpleName() + " but received " + raw);
        }
        return type.cast(raw);
    }

    /** Expects a successful LoginResponse and returns it. */
    onitama.net.LoginResponse expectLoginOk() {
        onitama.net.LoginResponse response = expect(onitama.net.LoginResponse.class);
        if (!response.ok()) {
            fail("login/register failed: " + response.errorText());
        }
        return response;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // closing a test socket is best-effort
        }
    }
}

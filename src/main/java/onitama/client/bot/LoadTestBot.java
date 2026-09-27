package onitama.client.bot;

import onitama.core.GameState;
import onitama.core.Move;
import onitama.core.PlayerColor;
import onitama.core.RulesEngine;
import onitama.net.CreateMatchRequest;
import onitama.net.ErrorMessage;
import onitama.net.GameOver;
import onitama.net.JoinMatchRequest;
import onitama.net.LoginRequest;
import onitama.net.LoginResponse;
import onitama.net.MatchCreated;
import onitama.net.MatchStart;
import onitama.net.Message;
import onitama.net.MoveApplied;
import onitama.net.MoveRejected;
import onitama.net.MoveRequest;
import onitama.net.Pong;
import onitama.net.RegisterRequest;
import onitama.net.RematchRequest;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * One headless load-test client: registers a generated account, pairs up
 * through the lobby, and plays random legal games as fast as the server
 * allows. Measures the per-move round-trip (send MoveRequest → receive the
 * matching MoveApplied). Runs on its own thread; all failures are captured
 * in {@link #failure} for the harness to report.
 */
public final class LoadTestBot implements Runnable {

    private static final int EXPECT_TIMEOUT_SECONDS = 60;

    private final int id;
    private final String host;
    private final int port;
    private final int games;
    private final Random random;
    private final BlockingQueue<String> roomCodesFromCreators;
    private final BlockingQueue<Message> inbox = new LinkedBlockingQueue<>();

    private Socket socket;
    private ObjectOutputStream out;

    private final List<Double> moveRttMillis = new ArrayList<>();
    private int gamesCompleted;
    private volatile Exception failure;

    /**
     * Creates a bot.
     *
     * @param roomCodesFromCreators shared FIFO queue: creators put their room
     *        codes, joiners take one — any joiner can join any creator's room
     */
    public LoadTestBot(int id, String host, int port, int games, long seed,
                       BlockingQueue<String> roomCodesFromCreators) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.games = games;
        this.random = new Random(seed + id);
        this.roomCodesFromCreators = roomCodesFromCreators;
    }

    @Override
    public void run() {
        try {
            playAllGames();
        } catch (Exception e) {
            failure = e;
            closeQuietly();
        }
    }

    public Exception failure() {
        return failure;
    }

    /** This bot's index in the fleet. */
    public int id() {
        return id;
    }

    public int gamesCompleted() {
        return gamesCompleted;
    }

    /** Harvests the measured round-trips in milliseconds. */
    public List<Double> moveRtts() {
        return List.copyOf(moveRttMillis);
    }

    // ------------------------------------------------------------------
    // Bot protocol flow
    // ------------------------------------------------------------------

    private void playAllGames() throws Exception {
        connect();
        String username = registerOrLogin();

        if (id % 2 == 0) {
            send(new CreateMatchRequest());
            MatchCreated created = expect(MatchCreated.class);
            roomCodesFromCreators.put(created.roomCode());
        } else {
            send(new JoinMatchRequest(roomCodesFromCreators.take()));
        }

        MatchStart start = expect(MatchStart.class);
        for (int game = 1; game <= games; game++) {
            if (game > 1) {
                send(new RematchRequest());
                start = expect(MatchStart.class);
            }
            playOneGame(start);
            gamesCompleted++;
        }
        closeQuietly();
    }

    /** Plays one game to completion with random legal half-moves. */
    private void playOneGame(MatchStart start) throws Exception {
        GameState state = start.initialState();
        PlayerColor me = start.yourColor();
        while (state.isOngoing()) {
            if (state.turn() == me) {
                List<Move> legal = RulesEngine.legalMoves(state);
                if (legal.isEmpty()) {
                    send(new onitama.net.PassTurn(state.hand(me).get(0).id()));
                    state = expect(MoveApplied.class).state();
                } else {
                    Move move = legal.get(random.nextInt(legal.size()));
                    long sentAt = System.nanoTime();
                    send(new MoveRequest(move.cardId(), move.from(), move.to()));
                    MoveApplied applied = expect(MoveApplied.class);
                    moveRttMillis.add((System.nanoTime() - sentAt) / 1_000_000.0);
                    state = applied.state();
                }
            } else {
                state = expect(MoveApplied.class).state();
            }
        }
        expect(GameOver.class);
    }

    /** Registers a fresh account, falling back to login if the name is taken. */
    private String registerOrLogin() throws Exception {
        String username = "bot" + id + "x" + System.currentTimeMillis() % 1000000;
        send(new RegisterRequest(username, "botpw" + id));
        LoginResponse response = expect(LoginResponse.class);
        if (!response.ok()) {
            send(new LoginRequest(username, "botpw" + id));
            response = expect(LoginResponse.class);
        }
        if (!response.ok()) {
            throw new IOException("bot " + id + " could not register: " + response.errorText());
        }
        return username;
    }

    // ------------------------------------------------------------------
    // Socket plumbing (single writer: only the bot thread sends)
    // ------------------------------------------------------------------

    private void connect() throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    Object raw = in.readObject();
                    if (raw instanceof Message message) {
                        inbox.put(message);
                    }
                }
            } catch (Exception e) {
                // the harness ends the bots' lives by closing their sockets
            }
        }, "onitama-bot-" + id + "-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void send(Message message) throws IOException {
        // Full snapshot per message, same reason as everywhere else.
        out.reset();
        out.writeObject(message);
        out.flush();
    }

    /**
     * Takes the next matching message, skipping heartbeats; a rejection or
     * server error is a bot failure (bots only send legal moves).
     */
    private <T extends Message> T expect(Class<T> type) throws Exception {
        long deadline = System.currentTimeMillis() + EXPECT_TIMEOUT_SECONDS * 1000L;
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new IOException("bot " + id + " timed out waiting for "
                        + type.getSimpleName());
            }
            Message message = inbox.poll(remaining, TimeUnit.MILLISECONDS);
            if (message == null) {
                throw new IOException("bot " + id + " timed out waiting for "
                        + type.getSimpleName());
            }
            if (type.isInstance(message)) {
                return type.cast(message);
            }
            if (message instanceof MoveRejected rejected) {
                throw new IOException("bot " + id + " move rejected: " + rejected.reason());
            }
            if (message instanceof ErrorMessage error) {
                throw new IOException("bot " + id + " server error: " + error.text());
            }
            // Pong and similar housekeeping messages are skipped.
        }
    }

    private void closeQuietly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // closing a bot socket is best-effort
        }
    }
}

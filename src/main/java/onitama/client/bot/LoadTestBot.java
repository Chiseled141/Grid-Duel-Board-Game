package onitama.client.bot;

import onitama.core.PlayerColor;
import onitama.core.duel.CardInstance;
import onitama.core.duel.GameAction;
import onitama.core.duel.PlayerView;
import onitama.core.duel.Targeting;
import onitama.core.duel.TurnPhase;
import onitama.net.ActionReply;
import onitama.net.ActionRequest;
import onitama.net.CreateMatchRequest;
import onitama.net.ErrorMessage;
import onitama.net.GameOver;
import onitama.net.GameUpdate;
import onitama.net.JoinMatchRequest;
import onitama.net.LoginRequest;
import onitama.net.LoginResponse;
import onitama.net.MatchCreated;
import onitama.net.MatchSync;
import onitama.net.Message;
import onitama.net.ProtocolHello;
import onitama.net.ProtocolReady;
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
 * allows. Measures the per-move round-trip (send ActionRequest → receive the
 * matching ActionReply/GameUpdate). Runs on its own thread; all failures are captured
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
        } finally {
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

        MatchSync sync = expect(MatchSync.class);
        for (int game = 1; game <= games; game++) {
            if (game > 1) {
                send(new RematchRequest());
                sync = expect(MatchSync.class);
            }
            playOneGame(sync);
            gamesCompleted++;
        }
    }

    /** Plays one game to completion with random legal actions. */
    private void playOneGame(MatchSync initialSync) throws Exception {
        MatchSync currentSync = initialSync;
        PlayerColor me = currentSync.view().viewer();
        String matchId = currentSync.matchId();
        long nextSeq = currentSync.nextSequence();

        while (currentSync != null && currentSync.view().outcome() == null
                && currentSync.view().phase() != TurnPhase.FINISHED) {
            PlayerView view = currentSync.view();
            if (view.turn() == me) {
                GameAction action = onitama.core.PracticeBot.chooseAction(view, random);
                if (action == null) {
                    break;
                }
                long sentAt = System.nanoTime();
                send(new ActionRequest(matchId, nextSeq++, view.revision(), action));
                currentSync = awaitMyAction();
                moveRttMillis.add((System.nanoTime() - sentAt) / 1_000_000.0);
            } else {
                currentSync = awaitUpdate();
            }
        }
        expect(GameOver.class);
    }

    private MatchSync awaitMyAction() throws Exception {
        MatchSync latest = null;
        boolean gotReply = false;
        boolean gotUpdate = false;
        while (!gotReply || !gotUpdate) {
            Message msg = inbox.poll(EXPECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (msg == null) {
                throw new IOException("timeout waiting for action response");
            }
            if (msg instanceof ActionReply reply) {
                gotReply = true;
                latest = reply.current();
                if (!reply.receipt().accepted()) {
                    break;
                }
            } else if (msg instanceof GameUpdate update) {
                gotUpdate = true;
                latest = update.current();
            } else if (msg instanceof GameOver) {
                inbox.offer(msg);
                break;
            }
        }
        return latest;
    }

    private MatchSync awaitUpdate() throws Exception {
        while (true) {
            Message msg = inbox.poll(EXPECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (msg == null) {
                throw new IOException("timeout waiting for game update");
            }
            if (msg instanceof GameUpdate update) {
                return update.current();
            } else if (msg instanceof GameOver) {
                inbox.offer(msg);
                return null;
            }
        }
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
        ObjectInputStream in;
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            in.setObjectInputFilter(Message.WIRE_FILTER);

            ErrorMessage greeting = (ErrorMessage) in.readObject();
            if (!"GRID_DUEL_PROTOCOL:2".equals(greeting.text())) {
                throw new IOException("unexpected greeting: " + greeting.text());
            }
            out.reset();
            out.writeObject(new ProtocolHello(2));
            out.flush();
            ProtocolReady ready = (ProtocolReady) in.readObject();
            if (ready.version() != 2) {
                throw new IOException("unexpected ready version: " + ready.version());
            }
        } catch (Exception e) {
            closeQuietly();
            throw (e instanceof IOException ioe) ? ioe : new IOException(e);
        }
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
        out.reset();
        out.writeObject(message);
        out.flush();
    }

    private <T extends Message> T expect(Class<T> type) throws Exception {
        long deadline = System.currentTimeMillis() + EXPECT_TIMEOUT_SECONDS * 1000L;
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new IOException("bot " + id + " timed out waiting for " + type.getSimpleName());
            }
            Message message = inbox.poll(remaining, TimeUnit.MILLISECONDS);
            if (message == null) {
                throw new IOException("bot " + id + " timed out waiting for " + type.getSimpleName());
            }
            if (type.isInstance(message)) {
                return type.cast(message);
            }
            if (message instanceof ErrorMessage error) {
                throw new IOException("bot " + id + " server error: " + error.text());
            }
        }
    }

    private void closeQuietly() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}

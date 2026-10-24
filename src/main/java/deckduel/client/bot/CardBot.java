package deckduel.client.bot;

import deckduel.client.ClientTls;
import deckduel.client.DeckDuelClient;
import deckduel.client.startup.InstalledVersion;
import deckduel.client.startup.ServerRoute;
import javax.net.ssl.SSLContext;
import deckduel.core.card.GreedyBot;
import deckduel.core.card.MatchPhase;
import deckduel.net.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * A headless player for the demo and load-test commands. It speaks the real protocol, sees only
 * its own {@link MatchUpdate}s and chooses every move with {@link GreedyBot}, exactly like a client.
 */
public final class CardBot implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(CardBot.class.getName());
    private final String username, password;
    private final int games;
    private final boolean host;
    private final CompletableFuture<String> room;
    private final DeckDuelClient client;
    private final CompletableFuture<List<MatchUpdate>> finished = new CompletableFuture<>();
    private final List<MatchUpdate> results = new ArrayList<>();
    private boolean triedLogin;
    private int playedRevision = -1;
    private String finishedKey;
    private long requests = 1;

    /**
     * Creates a bot that plays {@code games} games against one partner, then leaves. The host
     * creates a room and publishes its code to {@code room}; the partner joins that code. Games
     * after the first are rematches.
     */
    public CardBot(String username, String password, int games, boolean host, CompletableFuture<String> room) {
        this(username, password, games, host, room, ClientTls.systemTrust());
    }

    /** As above, trusting {@code tls} for TLS routes (a local test authority for development). */
    public CardBot(String username, String password, int games, boolean host, CompletableFuture<String> room, SSLContext tls) {
        this.username = username;
        this.password = password;
        this.games = games;
        this.host = host;
        this.room = room;
        client = new DeckDuelClient(this::receive, reason -> finished.completeExceptionally(new IOException(reason)), tls,
                InstalledVersion.current(), 10_000);
    }

    /** Connects, signs in (registering on first use) and hosts or joins; completes with each final view. */
    public CompletableFuture<List<MatchUpdate>> start(ServerRoute route) throws IOException {
        client.ensureConnected(route);
        client.send(new RegisterRequest(username, password));
        return finished;
    }

    // Runs on the client's reader thread; one message at a time.
    private synchronized void receive(Message message) {
        if (message instanceof LoginResponse response) signedIn(response);
        else if (message instanceof MatchCreated created) room.complete(created.roomCode());
        else if (message instanceof MatchUpdate update) onUpdate(update);
        else if (message instanceof ErrorMessage error) finished.completeExceptionally(new IllegalStateException(error.text()));
        else if (message instanceof ActionRejected rejected) LOG.warning(() -> username + " move rejected: " + rejected.reason());
        else if (message instanceof Ping ping) client.send(new Pong(ping.timestamp()));
    }

    private void signedIn(LoginResponse response) {
        if (response.ok()) {
            if (host) client.send(new CreateMatchRequest());
            else room.thenAccept(code -> client.send(new JoinMatchRequest(code)));
            return;
        }
        if (triedLogin) { finished.completeExceptionally(new IllegalStateException(response.errorText())); return; }
        triedLogin = true;
        client.send(new LoginRequest(username, password));
    }

    private void onUpdate(MatchUpdate update) {
        if (update.view().phase() == MatchPhase.FINISHED) {
            String key = update.matchId() + "/" + update.game();
            if (key.equals(finishedKey)) return;
            finishedKey = key;
            results.add(update);
            playedRevision = -1;
            if (results.size() < games) { client.send(new RematchRequest()); return; }
            client.send(new LeaveMatchRequest());
            finished.complete(List.copyOf(results));
            return;
        }
        if (!update.view().needsAction() || update.view().revision() == playedRevision) return;
        playedRevision = update.view().revision();
        client.send(new MoveRequest(requests++, playedRevision, GreedyBot.choose(update.view())));
    }

    /** The account this bot plays as. */
    public String username() { return username; }

    @Override public void close() { client.close(); }
}

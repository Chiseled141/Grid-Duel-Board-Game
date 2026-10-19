package deckduel.client.bot;

import deckduel.client.DeckDuelClient;
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
    private final DeckDuelClient client;
    private final CompletableFuture<List<MatchUpdate>> finished = new CompletableFuture<>();
    private final List<MatchUpdate> results = new ArrayList<>();
    private boolean triedLogin;
    private int playedRevision = -1;
    private String finishedKey;
    private long requests = 1;

    /** Creates a bot that will play {@code games} matchmaking games with the server's automatic deck. */
    public CardBot(String username, String password, int games) {
        this.username = username;
        this.password = password;
        this.games = games;
        client = new DeckDuelClient(this::receive, reason -> finished.completeExceptionally(new IOException(reason)));
    }

    /** Connects, signs in (registering on first use) and starts searching; completes with each final view. */
    public CompletableFuture<List<MatchUpdate>> start(String host, int port) throws IOException {
        client.ensureConnected(host, port);
        client.send(new RegisterRequest(username, password));
        return finished;
    }

    // Runs on the client's reader thread; one message at a time.
    private synchronized void receive(Message message) {
        if (message instanceof LoginResponse response) signedIn(response);
        else if (message instanceof MatchUpdate update) onUpdate(update);
        else if (message instanceof MatchmakingStatus status && status.error() != null)
            finished.completeExceptionally(new IllegalStateException(status.error()));
        else if (message instanceof ActionRejected rejected) LOG.warning(() -> username + " move rejected: " + rejected.reason());
        else if (message instanceof Ping ping) client.send(new Pong(ping.timestamp()));
    }

    private void signedIn(LoginResponse response) {
        if (response.ok()) { client.send(new FindMatchRequest(true)); return; }
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
            client.send(new LeaveMatchRequest());
            playedRevision = -1;
            if (results.size() >= games) finished.complete(List.copyOf(results));
            else client.send(new FindMatchRequest(true));
            return;
        }
        boolean waitedOn = update.view().choosingLandmarks() || update.view().yourTurn();
        if (!waitedOn || update.view().revision() == playedRevision) return;
        playedRevision = update.view().revision();
        client.send(new MoveRequest(requests++, playedRevision, GreedyBot.choose(update.view())));
    }

    /** The account this bot plays as. */
    public String username() { return username; }

    @Override public void close() { client.close(); }
}

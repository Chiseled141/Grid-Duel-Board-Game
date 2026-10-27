package deckduel.client.update;

import deckduel.client.startup.UpdateFlow;
import deckduel.client.startup.UpdateRestarter;
import deckduel.client.state.ClientModel;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connects the update steps to the session on the EDT: new matches wait while an update is
 * installing, and once it is verified the helper takes over and the game closes, but only when
 * no match is in progress; a running match is always played to the end first.
 */
public final class UpdateFinisher {
    private static final Logger LOG = Logger.getLogger(UpdateFinisher.class.getName());

    private final ClientModel model;
    private final UpdateFlow updates;
    private final UpdateRestarter restarter;
    private final Runnable closeGame;
    private final Consumer<String> notice;
    private boolean finishing;

    /**
     * @param restarter hands over to the helper, or null when nothing real is installed (fixtures)
     * @param closeGame saves preferences and exits the game process
     * @param notice tells the player, for example over a match, that the update waits for it to end
     */
    public UpdateFinisher(ClientModel model, UpdateFlow updates, UpdateRestarter restarter, Runnable closeGame,
                          Consumer<String> notice) {
        this.model = model;
        this.updates = updates;
        this.restarter = restarter;
        this.closeGame = closeGame;
        this.notice = notice;
        updates.addListener(this::changed);
    }

    /** Whether a verified update restarts the game by itself. */
    public boolean restartsAutomatically() { return restarter != null; }

    private void changed(UpdateFlow.State state) {
        if (state.offer() != null && state.offer().required()) model.requireUpdate(state.offer().latestVersion());
        boolean installing = state.stage() == UpdateFlow.Stage.INSTALLING
                || state.stage() == UpdateFlow.Stage.INSTALLED && restarter != null;
        model.setUpdating(installing);
        if (state.stage() == UpdateFlow.Stage.INSTALLED && restarter != null && !finishing) finish(state.offer().latestVersion());
    }

    private void finish(String version) {
        finishing = true;
        if (model.matchInProgress()) notice.accept("Update " + version + " is ready. Deck Duel restarts when this match ends.");
        model.whenNoMatchInProgress(() -> {
            try {
                restarter.handOver(version);
                closeGame.run();
            } catch (IOException | RuntimeException failed) {
                LOG.log(Level.WARNING, "could not hand over to the updater", failed);
                finishing = false;
                updates.handOverFailed("Could not restart to finish the update: " + failed.getMessage());
            }
        });
    }
}

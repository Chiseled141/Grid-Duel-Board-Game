package deckduel.client.ui;

import java.util.ArrayList;
import java.util.List;
import deckduel.core.PlayerColor;
import deckduel.core.duel.PlayerView;
import deckduel.core.duel.PublicEvent;

/** Derives visual destinations from permitted views; never accesses a hidden card identity. */
final class CardDraws {
    private CardDraws() { }

    record Draw(boolean movement, PlayerColor actor, int slot) { }

    static List<Draw> between(PlayerView before, PlayerView after, List<PublicEvent> events) {
        if (after.revision() <= before.revision() || after.viewer() != before.viewer()) return List.of();
        var draws = new ArrayList<Draw>();
        PlayerColor actor = before.turn();
        if (actor == after.viewer()) {
            var oldIds = before.ownSkillHand().stream().map(c -> c.instanceId()).toList();
            for (int slot = 0; slot < after.ownSkillHand().size(); slot++) {
                if (!oldIds.contains(after.ownSkillHand().get(slot).instanceId()))
                    draws.add(new Draw(false, actor, slot));
            }
        } else {
            long burns = events.stream().filter(e -> e instanceof PublicEvent.CardBurned).count();
            int received = Math.max(0, before.skillDeckCount() - after.skillDeckCount() - (int) burns);
            // Theft transfers one concealed card without drawing from the shared deck.
            if (events.stream().anyMatch(e -> e instanceof PublicEvent.SkillCommitted sc
                    && sc.skill().definitionId().equals("steal"))) received = 1;
            received = Math.min(received, after.opponentSkillCount());
            for (int slot = after.opponentSkillCount() - received; slot < after.opponentSkillCount(); slot++)
                draws.add(new Draw(false, actor, slot));
        }
        return List.copyOf(draws);
    }

    static List<String> moves(PlayerView view, PlayerColor actor) {
        return actor == PlayerColor.BLUE ? view.blueMovementHand() : view.redMovementHand();
    }
}

package onitama.client.ui;

import java.util.ArrayList;
import java.util.List;
import onitama.core.PlayerColor;
import onitama.core.duel.PlayerView;
import onitama.core.duel.PublicEvent;

/** Derives visual destinations from permitted views; never accesses a hidden card identity. */
final class CardDraws {
    private CardDraws() { }

    record Draw(boolean movement, PlayerColor actor, int slot) { }

    static List<Draw> between(PlayerView before, PlayerView after, List<PublicEvent> events) {
        if (after.revision() <= before.revision() || after.viewer() != before.viewer()) return List.of();
        var draws = new ArrayList<Draw>();
        for (PublicEvent event : events) {
            if (event instanceof PublicEvent.MovementPlayed played) {
                // The engine appends the replacement, including when it draws the same card again.
                draws.add(new Draw(true, played.actor(), moves(after, played.actor()).size() - 1));
            } else if (event instanceof PublicEvent.SkillCommitted skill
                    && skill.skill().definitionId().equals("refocus")) {
                for (int i = 0; i < moves(after, skill.actor()).size(); i++)
                    draws.add(new Draw(true, skill.actor(), i));
            }
        }
        // Consumption appends a spent skill and removes one deck card if a replacement exists.
        // This also catches Foresight resolution, whose accepted event list is empty.
        if (after.spentSkills().size() > before.spentSkills().size()
                && after.skillDeckCount() < before.skillDeckCount()) {
            PlayerColor actor = before.turn();
            int size = actor == after.viewer() ? after.ownSkillHand().size() : after.opponentSkillCount();
            if (size > 0) draws.add(new Draw(false, actor, size - 1));
        }
        return List.copyOf(draws);
    }

    static List<String> moves(PlayerView view, PlayerColor actor) {
        return actor == PlayerColor.BLUE ? view.blueMovementHand() : view.redMovementHand();
    }
}

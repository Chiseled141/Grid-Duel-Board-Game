package deckduel.client.state;

import deckduel.core.card.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Component deltas must reconcile even when a multiplier, King or round changes. */
class ReplayScoreChangeTest {
    private ScoreBreakdown score(long units, long pairs, Formation formation, long nations, int kings) {
        return new ScoreBreakdown(units, pairs, formation, nations, List.of(), List.of(),
                kings == 0 ? List.of() : List.of(new KingBonus(King.VARKHAN, kings)));
    }
    private PlayerView view(int revision, int round, ScoreBreakdown score, long banked) {
        var side = new SideView(Row.EMPTY, score, 0, 0, 10, 35, 0, banked);
        return new PlayerView(Seat.FIRST, revision, MatchPhase.IN_PROGRESS, round, Seat.FIRST, side, side,
                List.of(), List.of(), List.of(), null, null, List.of(), List.of());
    }
    private void reconciles(ReplayScoreChange.Side side) {
        assertEquals(side.delta(), side.components().stream().mapToLong(ReplayScoreChange.Component::delta).sum());
    }

    @Test void everyFormationTransitionAndItsReverseReconcile() {
        var forms = new java.util.ArrayList<Formation>(); forms.add(null); forms.addAll(List.of(Formation.values()));
        for (Formation old : forms) for (Formation next : forms) {
            var before = view(1, 1, score(18, 12, old, 0, 2), 0);
            var after = view(2, 1, score(23, 18, next, 15, 8), 0);
            var change = ReplayScoreChange.between(before, after);
            reconciles(change.you()); reconciles(change.opponent());
            assertEquals(after.you().score().total() - before.you().score().total(), change.you().delta());
            assertEquals(-change.you().delta(), ReplayScoreChange.between(after, before).you().delta());
        }
    }

    @Test void kingAndSevenNationPointsStayOutsideTheMultiplierEffect() {
        var before = view(1, 1, score(20, 12, Formation.BANNER_HOST, 0, 0), 0);
        var after = view(2, 1, score(20, 12, Formation.BANNER_HOST, 15, 10), 0);
        var change = ReplayScoreChange.between(before, after).you();
        assertEquals(25, change.delta());
        assertEquals(0, change.components().stream().filter(c -> c.title().equals("Multiplier effect"))
                .findFirst().orElseThrow().delta());
        reconciles(change);
    }

    @Test void removingAFormationProducesNegativeComponentsWithoutBankingAgain() {
        var before = view(1, 1, score(25, 30, Formation.BANNER_HOST, 15, 10), 100);
        var after = view(2, 1, score(10, 3, null, 0, 2), 100);
        var change = ReplayScoreChange.between(before, after).you();
        assertTrue(change.delta() < 0); assertEquals(0, change.bankedDelta()); reconciles(change);
        assertTrue(change.components().stream().anyMatch(component -> component.description().contains("(-")));
    }

    @Test void openingHasNoImaginaryGainAndRoundResetKeepsBankingSeparate() {
        var opening = view(0, 1, score(10, 3, null, 0, 0), 0);
        var initial = ReplayScoreChange.between(null, opening);
        assertTrue(initial.context().contains("no previous")); assertEquals(0, initial.you().delta());
        var second = view(1, 2, score(0, 0, null, 0, 0), 13);
        var change = ReplayScoreChange.between(opening, second);
        assertTrue(change.context().contains("rows reset"));
        assertEquals(-13, change.you().delta()); assertEquals(13, change.you().bankedDelta());
        reconciles(change.you());
    }

    @Test void storedScoresAreUsedRatherThanRecomputedFromEmptyFixtureRows() {
        var before = view(1, 1, score(19, 9, Formation.FULL_REGIMENT, 0, 4), 0);
        var after = view(4, 1, score(25, 30, Formation.BANNER_HOST, 15, 8), 0);
        var change = ReplayScoreChange.between(before, after);
        assertTrue(change.context().contains("multiple actions"));
        assertEquals(before.you().score().total(), change.you().before());
        assertEquals(after.you().score().total(), change.you().after()); reconciles(change.you());
        assertThrows(UnsupportedOperationException.class, () -> change.you().components().clear());
    }

    @Test void kingDetailsExplainTheAggregateAndFormationNamesFollowTheRecordedScores() {
        var before = view(1, 1, score(20, 10, Formation.COMPANY, 0, 2), 0);
        var after = view(2, 1, score(25, 10, Formation.FULL_REGIMENT, 0, 8), 0);
        var side = ReplayScoreChange.between(before, after).you();
        assertEquals("Company", side.formationBefore());
        assertEquals("Full Regiment", side.formationAfter());
        assertEquals(1, side.multiplierBefore()); assertEquals(2, side.multiplierAfter());
        assertEquals(List.of(new ReplayScoreChange.Component(King.VARKHAN.title(), 2, 8)), side.kingChanges());
        long totalKingChange = side.components().stream().filter(component -> component.title().equals("Kings"))
                .mapToLong(ReplayScoreChange.Component::delta).sum();
        assertEquals(totalKingChange, side.kingChanges().stream().mapToLong(ReplayScoreChange.Component::delta).sum());
        assertThrows(UnsupportedOperationException.class, () -> side.kingChanges().clear());
    }

    @Test void zeroPointKingsStayVisibleAndRemovalIsANegativeKingChange() {
        var withKing = view(1, 1, new ScoreBreakdown(10, 3, null, 0, List.of(), List.of(),
                List.of(new KingBonus(King.NETHRION, 0), new KingBonus(King.VARKHAN, 4))), 0);
        var withoutKing = view(2, 1, score(10, 3, null, 0, 0), 0);
        var side = ReplayScoreChange.between(withKing, withoutKing).you();
        assertEquals(2, side.kingChanges().size());
        assertTrue(side.kingChanges().stream().anyMatch(component -> component.title().equals(King.NETHRION.title())
                && component.delta() == 0));
        assertEquals(-4, side.kingChanges().stream().mapToLong(ReplayScoreChange.Component::delta).sum());
        reconciles(side);
    }
}

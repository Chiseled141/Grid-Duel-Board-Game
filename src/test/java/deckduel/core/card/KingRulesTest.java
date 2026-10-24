package deckduel.core.card;

import static deckduel.core.card.Cards.*;
import static org.junit.jupiter.api.Assertions.*;

import deckduel.cards.CatalogLoader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** King formulas, immutable transitions and recipient privacy using reproducible inputs. */
class KingRulesTest {
    private static final CardDef ASSASSIN = unit("a", 2, "Ashkar", "Assassin");
    private static final CardDef BOWMAN = unit("b", 3, "Nerath", "Bowman");
    private static final CardDef SPELL = spell("draw", new Effect.Draw(2));

    private static int bonus(King king, Row row, int spells) {
        return KingScoring.bonuses(row, new KingStatus(List.of(king), spells, false)).get(0).points();
    }
    private static Row group(int count) {
        Row row = Row.EMPTY;
        for (int i = 0; i < count; i++) row = row.place(i, copy(unit("u" + i, 2, "N" + i, "Assassin")));
        return row;
    }
    static MatchState offered(King king, List<CardDef> deck) {
        for (long seed = 0; ; seed++) {
            MatchState state = MatchState.start(deck, deck, seed);
            if (state.view(Seat.FIRST).kingOffer().contains(king)) return state;
        }
    }
    static MatchState choose(MatchState state, King king) {
        return state.apply(Seat.FIRST, new ChooseKing(king)).apply(Seat.SECOND, new ChooseKing(king));
    }
    private static MatchState ready(King king, int deckSize) {
        return choose(offered(king, copies(ASSASSIN, deckSize)), king);
    }
    private static MatchState chooseRound(MatchState state) {
        if (state.phase() != MatchPhase.KING_SELECTION) return state;
        King king = state.view(Seat.FIRST).kingOffer().get(0);
        return choose(state, king);
    }

    @Test void assaultCountsOnlyItsTwoClassesAndCapsAtTen() {
        assertEquals(4, bonus(King.VARKHAN, row(ASSASSIN, BOWMAN, unit("w", 6, "Ashkar", "Wizard")), 0));
        assertEquals(10, bonus(King.VARKHAN, group(7), 0));
        assertEquals(0, bonus(King.VARKHAN, Row.EMPTY, 0));
    }

    @Test void councilAndAllianceCountDistinctActiveTagsAndCapAtTen() {
        List<CardDef> units = DeckRules.deck(CatalogLoader.loadBundled()).stream().filter(c -> c.kind() == CardKind.UNIT).toList();
        assertEquals(10, bonus(King.ALTHARION, row(units.subList(0, 5).toArray(CardDef[]::new)), 0));
        assertEquals(2, bonus(King.ALTHARION, group(7), 0));
        assertEquals(10, bonus(King.THALVAREN, group(7), 0));
        assertEquals(2, bonus(King.THALVAREN, row(ASSASSIN, ASSASSIN), 0));
    }

    @ParameterizedTest @CsvSource({"0,0", "1,0", "2,0", "3,3", "4,6", "5,9", "6,10", "7,10"})
    void legionStartsAtTheThirdUnit(int count, int expected) {
        assertEquals(expected, bonus(King.MORDRAVEN, group(count), 0));
    }

    @Test void tiedLargestGroupsAreNotAddedTwice() {
        assertEquals(3, bonus(King.MORDRAVEN, row(ASSASSIN, ASSASSIN, ASSASSIN, BOWMAN, BOWMAN, BOWMAN), 0));
    }

    @Test void twinBannersCountsQualifyingNationsOnceAndCapsAtTen() {
        assertEquals(5, bonus(King.VAELTHOR, row(ASSASSIN, ASSASSIN, ASSASSIN, ASSASSIN, ASSASSIN), 0));
        assertEquals(10, bonus(King.VAELTHOR, row(ASSASSIN, ASSASSIN, BOWMAN, BOWMAN,
                unit("c", 4, "Third", "Guardman"), unit("d", 5, "Third", "Knight")), 0));
        assertEquals(0, bonus(King.VAELTHOR, row(ASSASSIN, BOWMAN), 0));
    }

    @Test void decreeNeedsAnActiveTagAndCapsAtNine() {
        assertEquals(0, bonus(King.AURENDOR, Row.EMPTY, 5));
        assertEquals(3, bonus(King.AURENDOR, row(ASSASSIN), 1));
        assertEquals(9, bonus(King.AURENDOR, row(ASSASSIN), 4));
        Row row = row(ASSASSIN);
        assertEquals(0, bonus(King.AURENDOR, row.update(row.at(0).with(Suppression.TAGS)), 3));
        assertEquals(9, bonus(King.AURENDOR, row.update(row.at(0).with(Suppression.POINTS)), 3));
    }

    @Test void disablesRemovalReplacementAndClearingRecalculateBonusesWithoutAccumulating() {
        Row original = row(ASSASSIN, BOWMAN);
        Row disabled = original.update(original.at(0).with(Suppression.TAGS));
        assertEquals(2, bonus(King.VARKHAN, disabled, 0));
        assertEquals(4, bonus(King.VARKHAN, original.update(original.at(0).with(Suppression.POINTS)), 0));
        assertEquals(4, bonus(King.VARKHAN, disabled.update(disabled.at(0).cleared()), 0));
        assertEquals(2, bonus(King.VARKHAN, original.remove(original.at(0).card().id()), 0));
        assertEquals(2, bonus(King.VARKHAN, original.place(0, copy(unit("w", 6, "Ashkar", "Wizard"))), 0));
        for (int i = 0; i < 10; i++) assertEquals(4, bonus(King.VARKHAN, original, 0));
    }

    @Test void kingPointsAreAddedAfterFormationAndSevenNations() {
        Row row = group(7);
        ScoreBreakdown base = Scoring.score(row);
        ScoreBreakdown score = Scoring.score(row, new KingStatus(List.of(King.VARKHAN, King.THALVAREN), 0, false));
        assertTrue(base.multiplier() > 1);
        assertEquals(15, score.bonus());
        assertEquals(base.total() + 20, score.total());
        assertEquals(List.of(new KingBonus(King.VARKHAN, 10), new KingBonus(King.THALVAREN, 10)), score.kings());
    }

    @Test void commonDisjointOffersSimultaneousPrivacyValidationAndStarter() {
        MatchState start = offered(King.VARKHAN, copies(ASSASSIN, 45));
        Seat starter = start.turn();
        PlayerView first = start.view(Seat.FIRST), second = start.view(Seat.SECOND);
        assertEquals(first.kingOffer(), second.kingOffer()); assertEquals(3, first.kingOffer().stream().distinct().count());
        assertEquals(10, first.hand().size()); assertEquals(35, first.you().deckCount());
        assertTrue(first.legalPlays().isEmpty()); assertFalse(first.canDraw()); assertFalse(first.canSkip());
        assertThrows(RuleViolation.class, () -> start.apply(starter, new DrawCard()));
        assertThrows(RuleViolation.class, () -> start.apply(starter, new SkipTurn()));
        King outside = List.of(King.values()).stream().filter(k -> !first.kingOffer().contains(k)).findFirst().orElseThrow();
        assertThrows(RuleViolation.class, () -> start.apply(Seat.FIRST, new ChooseKing(outside)));
        assertThrows(RuleViolation.class, () -> start.apply(Seat.FIRST, new ChooseKing(null)));
        assertThrows(IllegalArgumentException.class, () -> new KingStatus(List.of(King.VARKHAN, King.VARKHAN), 0, false));
        assertThrows(IllegalArgumentException.class, () -> new KingStatus(List.of(King.VARKHAN, King.NETHRION, King.AURENDOR), 0, false));
        MatchState waiting = start.apply(Seat.SECOND, new ChooseKing(King.VARKHAN));
        assertEquals(King.VARKHAN, waiting.view(Seat.SECOND).selectedKing());
        assertNull(waiting.view(Seat.FIRST).selectedKing());
        assertTrue(waiting.view(Seat.FIRST).opponent().kings().kings().isEmpty());
        assertFalse(waiting.awaits(Seat.SECOND)); assertTrue(waiting.awaits(Seat.FIRST));
        assertThrows(RuleViolation.class, () -> waiting.apply(Seat.SECOND, new ChooseKing(King.VARKHAN)));
        MatchState ready = waiting.apply(Seat.FIRST, new ChooseKing(King.VARKHAN));
        assertEquals(MatchPhase.IN_PROGRESS, ready.phase()); assertEquals(starter, ready.turn());
        assertEquals(List.of(King.VARKHAN), ready.view(Seat.FIRST).opponent().kings().kings());
        assertEquals(0, ready.view(Seat.FIRST).you().playsUsed());
        MatchState state = ready;
        while (state.round() == 1) state = state.apply(state.turn(), new SkipTurn());
        assertEquals(MatchPhase.KING_SELECTION, state.phase());
        assertTrue(state.view(Seat.FIRST).kingOffer().stream().noneMatch(first.kingOffer()::contains));
        state = chooseRound(state);
        assertEquals(2, state.view(Seat.FIRST).you().kings().kings().size());
        while (state.round() == 2) state = state.apply(state.turn(), new SkipTurn());
        assertEquals(MatchPhase.IN_PROGRESS, state.phase());
        assertTrue(state.view(Seat.FIRST).kingOffer().isEmpty());
        MatchState third = state;
        assertThrows(RuleViolation.class, () -> third.apply(third.turn(), new ChooseKing(King.VARKHAN)));
        assertEquals(first, start.view(Seat.FIRST), "immutable rejected/start state");
    }

    @Test void secretEdictEmptyDeckDoesNotSpendAnythingAndSingleCardDrawsNormally() {
        MatchState empty = ready(King.NETHRION, 10); Seat emptyActor = empty.turn();
        PlayerView before = empty.view(emptyActor);
        assertThrows(RuleViolation.class, () -> empty.apply(emptyActor, new DrawCard()));
        assertEquals(before, empty.view(emptyActor)); assertFalse(before.you().kings().edictUsed());
        MatchState one = ready(King.NETHRION, 11); Seat actor = one.turn();
        PlayerView after = one.apply(actor, new DrawCard()).view(actor);
        assertEquals(11, after.hand().size()); assertEquals(1, after.you().drawsUsed());
        assertTrue(after.you().kings().edictUsed()); assertFalse(after.keeping());
    }

    @Test void secretEdictIsPrivateBlocksOtherActionsKeepsTurnAndPutsOtherCardOnBottom() {
        MatchState state = ready(King.NETHRION, 12); Seat actor = state.turn();
        MatchState pending = state.apply(actor, new DrawCard());
        PlayerView mine = pending.view(actor), theirs = pending.view(actor.other());
        assertEquals(ChoiceKind.SECRET_EDICT, mine.choiceKind()); assertEquals(2, mine.looking().size());
        assertEquals(10, mine.hand().size()); assertEquals(1, mine.you().drawsUsed());
        assertTrue(theirs.looking().isEmpty()); assertEquals(ChoiceKind.NONE, theirs.choiceKind());
        assertEquals(0, mine.you().playsUsed()); assertFalse(mine.canDraw()); assertFalse(mine.canSkip());
        assertThrows(RuleViolation.class, () -> pending.apply(actor, new DrawCard()));
        assertThrows(RuleViolation.class, () -> pending.apply(actor, new SkipTurn()));
        assertThrows(RuleViolation.class, () -> pending.apply(actor, PlayCard.place(mine.hand().get(0).id(), 0)));
        assertThrows(RuleViolation.class, () -> pending.apply(actor, new KeepCard(mine.looking().get(0).id())));
        assertThrows(RuleViolation.class, () -> pending.apply(actor, new KeepEdictCard(-1)));
        CardInstance kept = mine.looking().get(1), bottom = mine.looking().get(0);
        MatchState done = pending.apply(actor, new KeepEdictCard(kept.id()));
        assertEquals(actor, done.turn()); assertEquals(11, done.view(actor).hand().size());
        assertEquals(1, done.view(actor).you().deckCount());
        MatchState drawn = done.apply(actor, new DrawCard());
        assertTrue(drawn.view(actor).hand().contains(bottom)); assertFalse(drawn.view(actor).keeping());
        assertEquals(2, drawn.view(actor).you().drawsUsed());
        assertEquals(mine, pending.view(actor), "a reconnect projects the same pending private snapshot");
    }

    @Test void spellDrawsDoNotUseSecretEdictAndRoundCountersReset() {
        MatchState state = choose(offered(King.NETHRION, copies(SPELL, 45)), King.NETHRION);
        Seat actor = state.turn();
        state = state.apply(actor, PlayCard.cast(state.view(actor).hand().get(0).id(), null));
        assertFalse(state.view(actor).you().kings().edictUsed()); assertEquals(0, state.view(actor).you().drawsUsed());
        assertEquals(1, state.view(actor).you().kings().spellsPlayed());
        state = state.apply(state.turn(), new SkipTurn());
        state = state.apply(actor, new DrawCard());
        assertTrue(state.view(actor).you().kings().edictUsed());
        assertEquals(ChoiceKind.SECRET_EDICT, state.view(actor).choiceKind());
        state = state.apply(actor, new KeepEdictCard(state.view(actor).looking().get(0).id()));
        while (state.round() == 1) state = state.apply(state.turn(), new SkipTurn());
        assertEquals(0, state.view(actor).you().kings().spellsPlayed());
        assertFalse(state.view(actor).you().kings().edictUsed());
    }

    @Test void exchangeAndReconnaissanceNeverTriggerEdict() {
        for (Effect effect : List.of(new Effect.Exchange(1, 2), new Effect.Reconnaissance(3))) {
            CardDef spell = spell("spell", effect);
            MatchState state = choose(offered(King.NETHRION, copies(spell, 45)), King.NETHRION);
            Seat actor = state.turn();
            PlayerView view = state.view(actor);
            state = state.apply(actor, PlayCard.cast(view.hand().get(0).id(), effect instanceof Effect.Exchange ? view.hand().get(1).id() : null));
            assertFalse(state.view(actor).you().kings().edictUsed());
            assertEquals(0, state.view(actor).you().drawsUsed());
            if (effect instanceof Effect.Reconnaissance) {
                assertEquals(ChoiceKind.RECONNAISSANCE, state.view(actor).choiceKind());
                state = state.apply(actor, new KeepCard(state.view(actor).looking().get(0).id()));
                assertEquals(actor.other(), state.turn());
            }
        }
    }

    @Test void edictPreservesUninspectedTopCardsAndBottomsOnlyTheUnchosenCard() {
        MatchState start = offered(King.NETHRION, copies(ASSASSIN, 13));
        King other = start.view(Seat.FIRST).kingOffer().stream().filter(k -> k != King.NETHRION).findFirst().orElseThrow();
        MatchState reference = choose(start, other); Seat actor = reference.turn();
        reference = reference.apply(actor, new DrawCard()).apply(actor, new DrawCard()).apply(actor, new DrawCard());
        CardInstance originalThird = reference.view(actor).hand().get(12);
        MatchState pending = choose(start, King.NETHRION).apply(actor, new DrawCard());
        CardInstance bottom = pending.view(actor).looking().get(0), kept = pending.view(actor).looking().get(1);
        MatchState actual = pending.apply(actor, new KeepEdictCard(kept.id()));
        actual = actual.apply(actor, new DrawCard());
        assertEquals(originalThird, actual.view(actor).hand().get(11));
        actual = actual.apply(actor, new DrawCard());
        assertEquals(bottom, actual.view(actor).hand().get(12));
        assertEquals(3, actual.view(actor).you().drawsUsed());
        assertEquals(0, actual.view(actor).you().playsUsed());
    }

    @Test void decreeCountsAcceptedNoTargetSpellsButNotRejectedRequests() {
        CardDef destroy = spell("destroy", new Effect.Destroy(TargetSide.OPPONENT));
        MatchState state = choose(offered(King.AURENDOR, copies(destroy, 45)), King.AURENDOR);
        Seat actor = state.turn(); PlayerView view = state.view(actor);
        assertThrows(RuleViolation.class, () -> state.apply(actor, PlayCard.cast(-1, null)));
        assertEquals(0, state.view(actor).you().kings().spellsPlayed());
        MatchState cast = state.apply(actor, PlayCard.cast(view.hand().get(0).id(), null));
        assertEquals(1, cast.view(actor).you().kings().spellsPlayed());
        assertEquals(0, cast.view(actor).you().score().kingPoints(), "no active Unit yet");
    }

    @Test void finalRoundBankIncludesKingsExactlyOnceAndForfeitPreservesState() {
        MatchState state = ready(King.VARKHAN, 45);
        while (state.phase() != MatchPhase.FINISHED) {
            if (state.phase() == MatchPhase.KING_SELECTION) { state = chooseRound(state); continue; }
            PlayerView view = state.view(state.turn());
            state = state.apply(state.turn(), view.you().row().cards().isEmpty()
                    ? PlayCard.place(view.hand().get(0).id(), 0) : new SkipTurn());
        }
        assertEquals(3, state.rounds().size());
        assertEquals(4, state.rounds().get(0).firstScore(), "one Assassin plus Assault, banked once");
        assertEquals(state.rounds().stream().mapToLong(RoundResult::firstScore).sum(), state.matchTotal(Seat.FIRST));
        assertTrue(state.rounds().stream().allMatch(r -> r.firstScore() >= 4));
        MatchState finished = state;
        assertThrows(RuleViolation.class, () -> finished.apply(Seat.FIRST, new SkipTurn()));
        MatchState waiting = offered(King.VARKHAN, copies(ASSASSIN, 45)).apply(Seat.FIRST, new ChooseKing(King.VARKHAN));
        assertEquals(MatchPhase.FINISHED, waiting.forfeit(Seat.SECOND).phase());
        assertEquals(King.VARKHAN, waiting.forfeit(Seat.SECOND).view(Seat.FIRST).selectedKing());
    }

    @Test void bothBotBrainsResolveEdictAndFinishAllThreeRoundsUsingOnlyTheirViews() {
        for (boolean hard : List.of(false, true)) {
            MatchState state = ready(King.NETHRION, 45);
            Seat actor = state.turn();
            state = state.apply(actor, new DrawCard());
            Move keep = hard ? PlannerBot.choose(state.view(actor)) : GreedyBot.choose(state.view(actor));
            assertInstanceOf(KeepEdictCard.class, keep);
            state = state.apply(actor, keep);
            assertEquals(actor, state.turn());
            int moves = 0;
            while (state.phase() != MatchPhase.FINISHED) {
                Seat seat = state.awaits(Seat.FIRST) ? Seat.FIRST : Seat.SECOND;
                PlayerView view = state.view(seat);
                state = state.apply(seat, hard ? PlannerBot.choose(view) : GreedyBot.choose(view));
                assertTrue(++moves < 120, "every choice and play progresses");
            }
            assertEquals(3, state.rounds().size());
        }
    }
}

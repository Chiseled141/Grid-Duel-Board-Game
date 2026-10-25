package deckduel.client.guide;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class TutorialLibraryTest {
    private static List<TutorialLibrary.Lesson> lessons;
    @BeforeAll static void load() throws Exception { lessons = TutorialLibrary.load(); }
    private static List<PlayerView> frames(String id) {
        return lessons.stream().filter(lesson -> lesson.id().equals(id)).findFirst().orElseThrow().frames();
    }
    private static PlayerView end(String id) { var frames = frames(id); return frames.get(frames.size() - 1); }

    @Test void allSeventeenBundledLessonsReplayContiguousAcceptedMovesFromTheRealDeck() {
        assertEquals(17, lessons.size());
        assertEquals(17, lessons.stream().map(TutorialLibrary.Lesson::id).distinct().count());
        for (var lesson : lessons) {
            assertFalse(lesson.text().isBlank()); assertFalse(lesson.before().isBlank()); assertFalse(lesson.after().isBlank());
            assertTrue(lesson.frames().size() >= 2);
            for (int i = 0; i < lesson.frames().size(); i++) {
                var frame = lesson.frames().get(i); assertEquals(Seat.FIRST, frame.seat());
                if (i > 0) assertEquals(lesson.frames().get(i - 1).revision() + 1, frame.revision());
            }
        }
        assertEquals(MatchPhase.KING_SELECTION, frames("basics").get(0).phase());
        assertEquals(10, frames("basics").get(0).hand().size());
        assertEquals(35, frames("basics").get(0).you().deckCount());
        assertEquals(MatchPhase.IN_PROGRESS, end("basics").phase());
    }

    @Test void comboFormationAndReplacementExamplesReallyPerformTheTaughtChanges() {
        assertEquals(6, end("combos").you().score().combos());
        assertEquals(3, end("combos").you().row().cards().size());
        assertEquals(Formation.COMPANY, end("formations").you().score().formation());
        var before = frames("replacement").get(0).you().row();
        var after = end("replacement").you().row();
        assertEquals(6, before.cards().size()); assertEquals(7, after.cards().size());
        assertNotEquals(before.at(0).card().id(), after.at(0).card().id());
    }

    @Test void drawingAndSkippingHaveTheExplainedTurnHandAndAllowanceEffects() {
        var frames = frames("draw-skip"); var before = frames.get(0);
        for (int i = 1; i <= 3; i++) {
            assertTrue(frames.get(i).yourTurn()); assertEquals(10, frames.get(i).you().playsRemaining());
            assertEquals(3 - i, frames.get(i).you().drawsRemaining());
            assertEquals(before.hand().size() + i, frames.get(i).hand().size());
        }
        var last = end("draw-skip");
        assertEquals(8, last.you().playsRemaining()); assertEquals(0, last.you().drawsRemaining());
        assertEquals(12, last.hand().size()); assertEquals(1, last.you().row().cards().size());
    }

    @Test void kingRecordingKeepsConfirmationsPrivateAndRecordsARealSecretEdictChoice() {
        var frames = frames("kings");
        assertTrue(frames.get(0).you().kings().kings().isEmpty());
        assertEquals(King.NETHRION, frames.get(1).selectedKing());
        assertTrue(frames.get(1).you().kings().kings().isEmpty());
        var looked = frames.stream().filter(view -> view.choiceKind() == ChoiceKind.SECRET_EDICT).findFirst().orElseThrow();
        assertEquals(2, looked.looking().size());
        var last = end("kings");
        assertTrue(last.looking().isEmpty()); assertTrue(last.hand().contains(looked.looking().get(0)));
        assertTrue(last.yourTurn()); assertEquals(10, last.you().playsRemaining());
        assertEquals(1, last.you().drawsUsed()); assertTrue(last.you().kings().edictUsed());
    }

    @Test void roundRecordingBanksThreeRoundsAndFinishesWithoutForfeit() {
        var last = end("rounds");
        assertEquals(1, frames("rounds").get(0).round());
        assertEquals(MatchPhase.FINISHED, last.phase()); assertFalse(last.outcome().forfeit());
        assertEquals(3, last.rounds().size()); assertEquals(2, last.you().kings().kings().size());
        assertEquals(last.rounds().stream().mapToLong(round -> round.scoreOf(Seat.FIRST)).sum(), last.you().matchTotal());
    }

    @ParameterizedTest @ValueSource(strings = {"disable-tags", "disable-points", "destroy-unit", "random-discard",
            "random-steal", "draw-two", "exchange", "reconnaissance", "spy", "clear-disable"})
    void everySpellRecordingActuallyResolvesItsNamedEffect(String id) {
        var frames = frames(id); var before = frames.get(0); var after = end(id);
        assertTrue(frames.stream().skip(1).anyMatch(frame -> frame.lastPlay() != null && frame.lastPlay().card().def().id().equals(id)));
        assertEquals(before.you().playsUsed() + 1, after.you().playsUsed());
        assertEquals(before.you().drawsUsed(), after.you().drawsUsed());
        switch (id) {
            case "disable-tags" -> assertTrue(after.opponent().row().at(0).suppressed().contains(Suppression.TAGS));
            case "disable-points" -> assertEquals(0, after.opponent().score().units());
            case "destroy-unit" -> assertTrue(after.opponent().row().cards().isEmpty());
            case "random-discard", "random-steal" -> assertEquals(before.opponent().handCount() - 1, after.opponent().handCount());
            case "draw-two" -> assertEquals(before.hand().size() + 1, after.hand().size());
            case "exchange" -> { assertEquals(before.hand().size(), after.hand().size()); assertEquals(before.you().deckCount() - 2, after.you().deckCount()); }
            case "spy" -> { assertEquals(3, after.revealed().size()); assertEquals(before.opponent().handCount(), after.opponent().handCount()); }
            case "reconnaissance" -> { assertEquals(3, frames.get(1).looking().size()); assertTrue(after.looking().isEmpty()); assertEquals(before.hand().size(), after.hand().size()); }
            case "clear-disable" -> { assertFalse(before.you().row().at(0).suppressed().isEmpty()); assertTrue(after.you().row().at(0).suppressed().isEmpty()); }
            default -> fail(id);
        }
    }

    @Test void malformedOrRejectedRecordedActionsFailClosed() {
        var catalog = CatalogLoader.loadBundled();
        for (String text : List.of("unknown", "DECK_DUEL_TUTORIAL:1\nseed 0\nclip 0\nFIRST P 99999 0 -\n",
                "DECK_DUEL_TUTORIAL:1\nseed 0\nclip -1\nFIRST D\n")) {
            assertThrows(IOException.class, () -> TutorialRecording.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), catalog));
        }
    }
}

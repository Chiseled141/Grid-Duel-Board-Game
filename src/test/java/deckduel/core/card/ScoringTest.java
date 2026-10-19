package deckduel.core.card;

import static deckduel.core.card.Cards.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** §5.3–5.4 scoring. Landmark numbers here are the catalog's provisional values (+3, ≤2/+1, +2, all nations +15). */
class ScoringTest {
    private final CardDef bowA = unit("bowA", 3, "N1", "Bowmen");
    private final CardDef bowB = unit("bowB", 3, "N1", "Bowmen");
    private final CardDef bowC = unit("bowC", 3, "N1", "Bowmen");

    @Test void pairBonusIsThreeForOneSharedCategoryAndNineForBoth() {
        assertEquals(3, Scoring.pairBonus(unit("a", 1, "N1", "Knights"), unit("b", 1, "N1", "Bowmen")), "nation only");
        assertEquals(3, Scoring.pairBonus(unit("a", 1, "N1", "Knights"), unit("b", 1, "N2", "Knights")), "class only");
        assertEquals(9, Scoring.pairBonus(unit("a", 1, "N1", "Knights"), unit("b", 1, "N1", "Knights")), "both replace the two +3s");
        assertEquals(0, Scoring.pairBonus(unit("a", 1, "N1", "Knights"), unit("b", 1, "N2", "Bowmen")));
    }

    @Test void designExampleScoresFifteenThenThirtySix() {
        ScoreBreakdown two = Scoring.score(row(bowA, bowB));
        assertEquals(6, two.units()); assertEquals(9, two.combos()); assertEquals(15, two.total());
        ScoreBreakdown three = Scoring.score(row(bowA, bowB, bowC));
        assertEquals(9, three.units()); assertEquals(27, three.combos()); assertEquals(36, three.total());
        assertEquals(3, three.pairs().size());
    }

    @Test void eachUnitEarnsASeparateBonusAgainstEveryMatchingUnitAndRemovalDropsItsPairs() {
        var hub = unit("hub", 1, "N1", "Knights");
        var nation = unit("nation", 1, "N1", "Bowmen");
        var type = unit("type", 1, "N2", "Knights");
        assertEquals(6, Scoring.score(row(hub, nation, type)).combos(), "hub–nation +3, hub–type +3, nation–type 0");
        Row three = row(bowA, bowB, bowC);
        assertEquals(15, Scoring.score(three.remove(three.at(1).card().id())).total());
    }

    @Test void disabledPointsKeepCombosButDisabledTagsDropThem() {
        Row row = row(bowA, bowB, bowC);
        Row noPoints = row.update(row.at(0).with(Suppression.POINTS));
        assertEquals(6, Scoring.score(noPoints).units()); assertEquals(27, Scoring.score(noPoints).combos());
        Row noTags = row.update(row.at(0).with(Suppression.TAGS));
        assertEquals(9, Scoring.score(noTags).units()); assertEquals(9, Scoring.score(noTags).combos());
        assertEquals(Scoring.score(row).total(), Scoring.score(noTags.update(noTags.at(0).cleared())).total(), "clearing restores it");
    }

    @Test void allSevenActiveNationsEarnTheBaseComboAndADisabledTagBreaksIt() {
        CardDef[] seven = new CardDef[7];
        for (int i = 0; i < 7; i++) seven[i] = unit("u" + i, 1, NATIONS.get(i), CLASSES.get(i % 5) + i);
        Row row = row(seven);
        ScoreBreakdown score = Scoring.score(row);
        assertTrue(Scoring.allNations(row));
        assertEquals(Scoring.ALL_NATIONS_BONUS, score.combos(), "no pairs share anything; only the seven-nation combo");
        assertTrue(score.effects().contains("All seven nations: +" + Scoring.ALL_NATIONS_BONUS));
        Row broken = row.update(row.at(3).with(Suppression.TAGS));
        assertFalse(Scoring.allNations(broken));
        assertEquals(0, Scoring.score(broken).combos());
        assertEquals(0, Scoring.score(row.remove(row.at(0).card().id())).combos(), "removing a required Unit breaks it");
    }

    @Test void embassyAndGuildHallRaiseMatchingPairsAndStack() {
        var nationPair = row(unit("a", 1, "N1", "Knights"), unit("b", 1, "N1", "Bowmen"));
        var classPair = row(unit("a", 1, "N1", "Knights"), unit("b", 1, "N2", "Knights"));
        var bothPair = row(bowA, bowB);
        var embassy = landmarks(new Effect.SharedTagBonus(TagType.REGION, 3));
        var guild = landmarks(new Effect.SharedTagBonus(TagType.CLASS, 3));
        assertEquals(6, Scoring.score(nationPair, embassy).combos());
        assertEquals(3, Scoring.score(classPair, embassy).combos(), "Embassy ignores class-only pairs");
        assertEquals(6, Scoring.score(classPair, guild).combos());
        assertEquals(12, Scoring.score(bothPair, embassy).combos(), "a +9 pair shares a nation too");
        var both = new java.util.ArrayList<>(embassy);
        both.addAll(guild);
        assertEquals(15, Scoring.score(bothPair, both).combos());
        var twoEmbassies = landmarks(new Effect.SharedTagBonus(TagType.REGION, 3), new Effect.SharedTagBonus(TagType.REGION, 3));
        assertEquals(15, Scoring.score(bothPair, twoEmbassies).combos(), "identical copies each contribute");
        assertEquals(15, Scoring.score(bothPair, twoEmbassies).pairs().get(0).points(), "the pair shows its raised bonus");
    }

    @Test void armoryRaisesOnlyWeakerUnitsAndDisabledPointsCancelIt() {
        Row row = row(unit("foot", 1, "N1", "Footmen"), unit("pike", 2, "N2", "Pikemen"), unit("bow", 3, "N3", "Bowmen"));
        var armory = landmarks(new Effect.WeakUnitBonus(2, 1));
        assertEquals(2 + 3 + 3, Scoring.score(row, armory).units(), "1→2 and 2→3; the 3-point Unit is not weaker");
        assertEquals(3 + 3, Scoring.score(row.update(row.at(0).with(Suppression.POINTS)), armory).units());
    }

    @Test void ruinedKeepScoresEachEmptySpaceLive() {
        var keep = landmarks(new Effect.EmptySpaceBonus(2));
        assertEquals(2 * Row.SIZE, Scoring.score(Row.EMPTY, keep).bonus());
        assertEquals(2 * (Row.SIZE - 2), Scoring.score(row(bowA, bowB), keep).bonus());
    }

    @Test void aSabotagedLandmarkContributesNothingAndSaysSo() {
        var embassy = landmarks(new Effect.SharedTagBonus(TagType.REGION, 3));
        var sabotaged = List.of(new LandmarkCard(embassy.get(0).card(), true));
        Row row = row(bowA, bowB);
        assertEquals(12, Scoring.score(row, embassy).combos());
        assertEquals(9, Scoring.score(row, sabotaged).combos());
        assertTrue(Scoring.score(row, sabotaged).effects().get(0).endsWith("sabotaged this round"));
    }
}

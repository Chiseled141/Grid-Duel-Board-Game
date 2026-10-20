package deckduel.core.card;

import static deckduel.core.card.Cards.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** §5.3 scoring and the Balatro-style formations; all nations +15 is the provisional value. */
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

    @Test void designExampleScoresFifteenThenThirtySixPlusItsRetinue() {
        ScoreBreakdown two = Scoring.score(row(bowA, bowB));
        assertEquals(6, two.units()); assertEquals(9, two.combos()); assertEquals(15, two.total());
        assertNull(two.formation());
        ScoreBreakdown three = Scoring.score(row(bowA, bowB, bowC));
        assertEquals(9, three.units()); assertEquals(27, three.combos());
        assertEquals(Formation.RETINUE, three.formation(), "three of one nation");
        assertEquals(36 + 10, three.total());
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
        assertEquals(0, score.combos(), "no pairs share anything");
        assertEquals(Scoring.ALL_NATIONS_BONUS, score.bonus(), "only the seven-nation combo");
        assertTrue(score.effects().contains("All seven nations: +" + Scoring.ALL_NATIONS_BONUS));
        Row broken = row.update(row.at(3).with(Suppression.TAGS));
        assertFalse(Scoring.allNations(broken));
        assertEquals(0, Scoring.score(broken).bonus());
        assertEquals(0, Scoring.score(row.remove(row.at(0).card().id())).bonus(), "removing a required Unit breaks it");
    }

    @Test void eachFormationIsRecognisedAndTheStrongestWins() {
        assertNull(Formation.best(row(unit("a", 1, "N1", "Knights"), unit("b", 1, "N2", "Bowmen"))));
        assertEquals(Formation.COMPANY, Formation.best(row(unit("a", 1, "N1", "K"), unit("b", 1, "N2", "K"), unit("c", 1, "N3", "K"))));
        assertEquals(Formation.RETINUE, Formation.best(row(unit("a", 1, "N1", "A"), unit("b", 1, "N1", "B"), unit("c", 1, "N1", "C"))));
        assertEquals(Formation.TWO_BANNERS, Formation.best(row(unit("a", 1, "N1", "A"), unit("b", 1, "N1", "B"),
                unit("c", 1, "N2", "C"), unit("d", 1, "N2", "D"))));
        assertEquals(Formation.FULL_MUSTER, Formation.best(row(unit("a", 1, "N1", "A"), unit("b", 1, "N1", "B"), unit("c", 1, "N1", "C"),
                unit("d", 1, "N2", "A"), unit("e", 1, "N2", "B"))));
        assertEquals(Formation.REGIMENT, Formation.best(row(unit("a", 1, "N1", "K"), unit("b", 1, "N2", "K"),
                unit("c", 1, "N3", "K"), unit("d", 1, "N4", "K"))));
        assertEquals(Formation.ROYAL_LINE, Formation.best(row(unit("a", 1, "N1", CLASSES.get(0)), unit("b", 1, "N2", CLASSES.get(1)),
                unit("c", 1, "N3", CLASSES.get(2)), unit("d", 1, "N4", CLASSES.get(3)), unit("e", 1, "N5", CLASSES.get(4)))));
        CardDef[] host = new CardDef[5];
        for (int i = 0; i < 5; i++) host[i] = unit("h" + i, 1, "N1", CLASSES.get(i));
        assertEquals(Formation.BANNER_HOST, Formation.best(row(host)), "all five of a nation outranks the Royal Line it also makes");
    }

    @Test void theFormationBonusJoinsBeforeItsMultiplierAndTheSevenNationBonusIsNeverMultiplied() {
        Row muster = row(unit("a", 2, "N1", "A"), unit("b", 3, "N1", "B"), unit("c", 4, "N1", "C"),
                unit("d", 5, "N2", "A"), unit("e", 6, "N2", "B"));
        ScoreBreakdown score = Scoring.score(muster);
        assertEquals(Formation.FULL_MUSTER, score.formation());
        assertEquals((score.units() + score.combos() + 20) * 2, score.total());
        assertTrue(score.effects().contains("Full Muster: +20 ×2"));
        CardDef[] seven = new CardDef[7];
        for (int i = 0; i < 7; i++) seven[i] = unit("s" + i, 2, NATIONS.get(i), CLASSES.get(i % 5));
        ScoreBreakdown royal = Scoring.score(row(seven));
        assertEquals(Formation.ROYAL_LINE, royal.formation());
        assertEquals((royal.units() + royal.combos() + 30) * 2 + Scoring.ALL_NATIONS_BONUS, royal.total());
    }

    @Test void disabledTagsLeaveAFormationButDisabledPointsDoNot() {
        Row row = row(bowA, bowB, bowC);
        assertEquals(Formation.RETINUE, Scoring.score(row.update(row.at(0).with(Suppression.POINTS))).formation());
        assertNull(Scoring.score(row.update(row.at(0).with(Suppression.TAGS))).formation());
    }
}

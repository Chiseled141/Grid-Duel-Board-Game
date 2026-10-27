package deckduel.client.guide;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** English lessons paired with immutable, recorded training-game views. Load off the UI thread. */
public final class TutorialLibrary {
    /** One rule, its observation prompts and a contiguous excerpt of an accepted-action recording. */
    public record Lesson(String id, String title, String text, String before, String after, List<PlayerView> frames) {
        public Lesson { frames = List.copyOf(frames); }
        @Override public String toString() { return title; }
    }

    private TutorialLibrary() { }

    /** Reads the shipped examples and checks them against the current bundled catalog and rules. */
    public static List<Lesson> load() throws IOException {
        Catalog catalog = CatalogLoader.loadBundled();
        List<Lesson> lessons = new ArrayList<>();
        add(lessons, catalog, "basics", "1 · Start a match", """
                Play three rounds. Add the final score of every round to your match total; the higher total wins. Equal totals are a draw.

                Open Play > Practice for an Easy or Hard bot. To play a friend, Create match and share its code, or Join a game by code / the open-room list.

                Every player has the same 45-card deck: 35 Units (seven nations, five classes each) and 10 neutral Spells. Decks shuffle independently. There is no deck building, resource or play cost.

                Each round deals ten fresh cards. Players take turns playing one Unit or Spell; no replacement is drawn automatically. Each player has ten plays, including voluntary skips, and three optional manual draws.
                """, "The recording begins with the real opening hand and shared King offer.",
                "Both Kings are public now, and normal turns have begun. Watch the hand and plays-left counters.");
        String unitText = """
                Each Unit has one nation, one class and printed points: Assassin 2, Bowman 3, Guardman 4, Knight 5, Wizard 6.

                Every pair of active Units is counted once: same nation only +3; same class only +3; both nation and class +9 total, replacing the separate bonuses.

                Points come from the current row. Replacing or destroying a Unit removes its points and pairs. Disabled tags stop its nation/class combos, formations and King conditions; disabled printed points still allow those tag-based effects.
                """;
        add(lessons, catalog, "combos", "2 · Units & pair combos", unitText,
                "Watch Units and Combos in the score breakdown as matching cards enter the row.",
                "Matching nations and matching classes have added separate pair bonuses. Each pair counts once.");
        String formations = Arrays.stream(Formation.values()).map(f -> f.title() + ": +" + f.bonus() + " ×" + f.multiplier())
                .collect(Collectors.joining("\n"));
        add(lessons, catalog, "formations", "3 · Formations & scoring", """
                Only the strongest formation on your row counts. A class Company needs three of one class; Retinue needs three of one nation. Two/Three Companies use 2+2 / 2+2+2 classes; Banners use nations. Full Regiment is 3+2 classes; Full Muster is 3+2 nations. Regiment needs four of one class; Royal Line needs all five classes; Banner Host needs all five of one nation.

                Score = (Unit points + pair combos + formation bonus) × formation multiplier, then add the seven-nation bonus and King points. Disabled tags do not count toward formations.

                Current formation balance (provisional):
                """ + formations + "\n\nAll seven active nations add +" + Scoring.ALL_NATIONS_BONUS
                        + " after the multiplier (also provisional). King bonuses are never multiplied.",
                "A third Unit of the same class is about to complete a Company.",
                "The formation line has changed. Its bonus joins the base before the multiplier.");
        add(lessons, catalog, "replacement", "4 · Full rows & replacement", """
                Your Unit row has seven spaces. Fill empty spaces first. Once all seven are occupied, play another Unit onto the one you want to replace.

                The old Unit leaves play; its points, pairs and formation contribution disappear. The new row is scored immediately. Replacement uses one normal play.

                A full row does not end the round. You may still cast Spells, replace Units or skip until both players have used their ten plays (or neither can act).

                With click controls, choose a hand card and then a space/target. With drag controls, drag it to the space/target. Change Card controls in Settings.
                """, "The row is nearly full. Watch its seventh space and the first Unit.",
                "The row still has seven Units: the new card replaced the old one and the score was recalculated.");
        add(lessons, catalog, "draw-skip", "5 · Drawing & skipping", """
                Press Draw (D) on your turn to draw one card. Three manual draws are available each round. Drawing spends no play and keeps your turn; it never happens automatically after a play.

                Spell draws do not spend that allowance. The round's new deal resets the allowance to three.

                Skip turn (S) keeps your hand and row, spends one of your ten plays and passes the turn. Finish a pending Reconnaissance or Secret Edict choice first.

                Current empty-hand rule: no cards and no available draw passes the turn automatically, preserving unused plays. If neither player can act, the round ends.
                """, "Three manual draws are available. Compare plays left with draws left.",
                "Draws kept the turn and spent no plays. Playing and skipping each spent a play, with no automatic replacement draw.");
        String kings = Arrays.stream(King.values()).map(k -> k.title() + " — " + k.description()).collect(Collectors.joining("\n\n"));
        add(lessons, catalog, "kings", "6 · Kings & Secret Edict", """
                After the deals in rounds 1 and 2, choose one of the same three Kings offered to both players. Click a painting, then CONFIRM. Choices are private until both confirm; both players may choose the same King.

                You have 30 seconds. If you do not confirm, the server chooses the first (leftmost) offer. The eye button shows/hides the offer to see your hand, without pausing the timer. Unit/Spell hover details are disabled throughout this choice.

                Round 2 uses three Kings never offered in round 1. Keep both Kings in round 3. Kings use no deck, hand or Unit space, play or draw. Rematches start with fresh offers.

                Nethrion's first manual draw each round lets you look at two, keep one and bottom the other. It spends one manual draw and keeps your turn; Spell draws do not trigger it. One card draws normally; an empty deck cannot spend the ability.

                King bonuses require active tags, are capped and are added after the formation multiplier. Current abilities:
                """ + kings, "Only your own confirmation is visible at first. Then watch Secret Edict's private two-card choice.",
                "One inspected card entered your hand, the other went to the deck bottom. One manual draw was spent; it is still your turn.");
        add(lessons, catalog, "rounds", "7 · Rounds & winning", """
                Each player gets ten plays per round. A play or voluntary skip spends one; manual draws spend none. Both rows may be full while turns continue.

                At round end, bank each row's final score once. Clear the rows, reshuffle the decks and deal ten fresh cards each. Round 2 includes a new King choice; round 3 retains both Kings.

                The loser of the previous round starts next. After a tie, alternate the previous starter. The opening starter is random.

                After round 3 compare the sum of all three round scores, even if one player won more individual rounds. Equal match totals are a draw.

                This excerpt continues to the end of a complete recorded training match. Use the speed button or Next to inspect the round transitions and final totals.
                """, "Round 1 is about to bank its final score. Watch BANKED ROUNDS and Match total.",
                "Three round scores have been added to each match total. The final comparison decides the winner.");
        for (CardDef spell : catalog.spells()) {
            add(lessons, catalog, spell.id(), (lessons.size() + 1) + " · " + spell.name(), spell.name() + "\n\n" + spell.text()
                    + "\n\nSpells are neutral, use no Unit space and are discarded after resolving. Casting uses one of your ten plays, even on a full row."
                    + spellHelp(spell.id()),
                    "Before casting: compare the visible hand, rows and score counters.",
                    "The effect has resolved. Compare the changed cards, disables or counters with the previous frame.");
        }
        return List.copyOf(lessons);
    }

    private static void add(List<Lesson> lessons, Catalog catalog, String id, String title, String text, String before, String after) throws IOException {
        lessons.add(new Lesson(id, title, text.strip(), before, after, TutorialRecording.load(id, catalog)));
    }

    private static String spellHelp(String id) {
        return "\n\n" + switch (id) {
            case "disable-tags" -> "Target an enemy Unit. Its printed points remain, but its nation/class combos, formations and King conditions stop until cleared or the round ends.";
            case "disable-points" -> "Target an enemy Unit. Its printed points stop, but active tags still count for pairs, formations and Kings. Lasts until cleared or round end.";
            case "destroy-unit" -> "Target an enemy Unit. It leaves the row, removing its printed points and all combos involving it.";
            case "clear-disable" -> "Target one of your disabled Units. Both existing point and tag disables are removed permanently; this gives no immunity to a later disable.";
            case "exchange" -> "Choose another card in your hand to discard, then draw two if available. It spends no manual draws. With no other hand card it has no effect.";
            case "reconnaissance" -> "Choose one inspected card to keep; the other cards go to the deck bottom in their original order. Fewer than three remaining means inspect as many as there are. Only you see this choice; keeping completes the Spell turn.";
            case "spy" -> "Only you see the random revealed enemy cards, while they stay in that hand until round end. Fewer than three means reveal as many as possible; the cards stay in the enemy's hand.";
            case "draw-two" -> "Draw as many as the deck contains, up to two. This spends no manual draws, but casting still uses a normal play and ends that turn.";
            case "random-discard" -> "An empty enemy hand makes this a no-effect cast. The discarded card is shown to both players.";
            case "random-steal" -> "An empty enemy hand makes this a no-effect cast. The stolen card is shown to both players and enters your hand.";
            default -> throw new IllegalArgumentException("Missing Spell lesson: " + id);
        };
    }
}

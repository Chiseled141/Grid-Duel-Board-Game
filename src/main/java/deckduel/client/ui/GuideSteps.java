package deckduel.client.ui;

import java.util.List;

/**
 * The How to Play guide: a few short steps, each one line of text over a clip recorded from a
 * real practice match ({@code /assets/guide/}). Shared by the menu's How to Play screen and the
 * in-match pause menu, so both teach the same thing.
 */
final class GuideSteps {
    /** One step: a title, a line of text and the bundled clip (an animated GIF) that shows it. */
    record Step(String title, String text, String clip) {
        /** The clip's classpath resource. */
        String resource() { return "/assets/guide/" + clip; }
    }

    static final List<Step> STEPS = List.of(
            new Step("Play your cards", "Drag a Unit onto your row. You get ten plays each round.", "play.gif"),
            new Step("Match for combos", "Units of the same nation or class score bonus points together.", "combos.gif"),
            new Step("Build formations", "Sets of classes or nations multiply your whole row.", "formation.gif"),
            new Step("Cast Spells", "Pick a Spell, then press Cast under your score.", "cast.gif"),
            new Step("Swap cards", "Press Swap, pick up to three cards, then press Swap again.", "swap.gif"),
            new Step("Choose Kings", "In rounds one and two, pick a King for extra points.", "kings.gif"),
            new Step("Win the match", "Three rounds. The higher total wins.", "win.gif"));

    private GuideSteps() { }
}

package deckduel.client.ui;

import deckduel.core.card.CardDef;
import deckduel.core.card.CardKind;
import deckduel.core.card.Effect;
import deckduel.core.card.Suppression;
import deckduel.core.card.TargetSide;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Locale;

/** Composes the ten neutral pxl illustrations with catalog names and concise effect labels. */
final class PixelSpellArt {
    private static final Color PAPER = new Color(0xF4E9CE), PALE = new Color(0xB6A1C7);
    // Text rows sit between the frame ornaments (y 5-7, 112-114) and the art window (y 17-91).
    private static final int TITLE_Y = 8, CAPTION_Y = 96, DETAIL_Y = 103;
    // Compact 3x5 lettering keeps the complete Reconnaissance name legible on an 80px card.
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ";
    private static final String[] FONT = {
            "010101111101101", "110101110101110", "011100100100011", "110101101101110",
            "111100110100111", "111100110100100", "011100101101011", "101101111101101",
            "111010010010111", "001001001101010", "101101110101101", "100100100100111",
            "101111111101101", "101111111111101", "010101101101010", "110101110100100",
            "010101101111011", "110101110101101", "011100010001110", "111010010010010",
            "101101101101111", "101101101101010", "101101111111101", "101101010101101",
            "101101010010010", "111001010100111", "111101101101111", "010110010010111",
            "110001010100111", "110001010001110", "101101111001001", "111100110001110",
            "011100111101111", "111001010010010", "111101111101111", "111101111001110",
            "000000000000000"};

    private PixelSpellArt() { }

    /** Match identity and effect, so changed catalog content never receives misleading art. */
    static boolean supports(CardDef card) {
        if (card == null || card.kind() != CardKind.SPELL) return false;
        return switch (card.id()) {
            case "disable-tags" -> card.effect().equals(new Effect.Disable(TargetSide.OPPONENT, Suppression.TAGS));
            case "disable-points" -> card.effect().equals(new Effect.Disable(TargetSide.OPPONENT, Suppression.POINTS));
            case "destroy-unit" -> card.effect().equals(new Effect.Destroy(TargetSide.OPPONENT));
            case "random-discard" -> card.effect().equals(new Effect.RandomDiscard(1));
            case "random-steal" -> card.effect().equals(new Effect.RandomSteal(1));
            case "draw-two" -> card.effect().equals(new Effect.Draw(2));
            case "exchange" -> card.effect().equals(new Effect.Exchange(1, 2));
            case "reconnaissance" -> card.effect().equals(new Effect.Reconnaissance(3));
            case "spy" -> card.effect().equals(new Effect.Spy(3));
            case "clear-disable" -> card.effect() instanceof Effect.ClearDisable;
            default -> false;
        };
    }

    static void paint(Graphics2D g, CardDef card) {
        PxlCardArt.paint(g, card.id());
        centered(g, card.name(), TITLE_Y, PAPER);
        String[] caption = caption(card.effect());
        centered(g, caption[0], CAPTION_Y, PAPER);
        centered(g, caption[1], DETAIL_Y, PALE);
    }

    private static String[] caption(Effect effect) {
        if (effect instanceof Effect.Disable disable) return new String[] {
                disable.what() == Suppression.TAGS ? "ENEMY TAGS OFF" : "ENEMY POINTS OFF", "THIS ROUND"};
        if (effect instanceof Effect.Destroy) return new String[] {"REMOVE ENEMY UNIT", "POINTS AND COMBOS"};
        if (effect instanceof Effect.RandomDiscard discard) return new String[] {
                "DISCARD " + discard.cards(), "RANDOM ENEMY CARD"};
        if (effect instanceof Effect.RandomSteal steal) return new String[] {
                "STEAL " + steal.cards() + " RANDOM", "ENEMY CARD TO YOU"};
        if (effect instanceof Effect.Draw draw) return new String[] {"DRAW " + draw.cards(), "FROM YOUR DECK"};
        if (effect instanceof Effect.Exchange exchange) return new String[] {
                "DISCARD " + exchange.discard() + " DRAW " + exchange.draw(), "YOUR CARDS"};
        if (effect instanceof Effect.Reconnaissance recon) return new String[] {
                "LOOK " + recon.look() + " KEEP 1", "YOUR DECK TOP"};
        if (effect instanceof Effect.Spy spy) return new String[] {
                "REVEAL " + spy.cards() + " TO YOU", "RANDOM ENEMY HAND"};
        if (effect instanceof Effect.ClearDisable) return new String[] {"RESTORE YOUR UNIT", "CLEAR ITS DISABLES"};
        throw new IllegalArgumentException("No pixel caption for " + effect);
    }

    private static void centered(Graphics2D g, String text, int y, Color color) {
        String value = text.toUpperCase(Locale.ROOT);
        int left = (PixelCardFace.WIDTH - (value.length() * 4 - 1)) / 2;
        g.setColor(color);
        for (int i = 0; i < value.length(); i++) {
            int glyphIndex = ALPHABET.indexOf(value.charAt(i));
            if (glyphIndex < 0) continue;
            String glyph = FONT[glyphIndex];
            for (int pixel = 0; pixel < 15; pixel++)
                if (glyph.charAt(pixel) == '1') g.fillRect(left + i * 4 + pixel % 3, y + pixel / 3, 1, 1);
        }
    }
}

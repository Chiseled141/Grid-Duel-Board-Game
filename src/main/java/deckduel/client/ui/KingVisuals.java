package deckduel.client.ui;

import deckduel.core.card.King;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;

/** Presentation accents matching the seven original King paintings; no gameplay values. */
final class KingVisuals {
    private KingVisuals() { }

    static Color color(King king) {
        return Color.web(switch (king) {
            case VARKHAN -> "#F69A3D";
            case ALTHARION -> "#E0E9F3";
            case MORDRAVEN -> "#E45C64";
            case NETHRION -> "#64B6ED";
            case AURENDOR -> "#F5CD62";
            case THALVAREN -> "#80CB95";
            case VAELTHOR -> "#BB94EB";
        });
    }

    /** Follows the PNG's alpha edge, preserving its painted frame instead of drawing a rectangle. */
    static DropShadow aura(King king) {
        Color tint = color(king);
        DropShadow halo = new DropShadow(BlurType.GAUSSIAN, tint.deriveColor(0, 1, 1, .72), 20, .22, 0, 0);
        halo.setInput(new DropShadow(BlurType.GAUSSIAN, tint, 6, .38, 0, 0));
        return halo;
    }
}

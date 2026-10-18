package deckduel.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import deckduel.core.PlayerColor;
import deckduel.core.Square;

/** Public, immutable objective placement chosen once per match, mirrored for equal access. */
public record ObjectiveLayout(List<Square> scoring, List<Square> mana) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public ObjectiveLayout {
        scoring = List.copyOf(scoring); mana = List.copyOf(mana);
        if (scoring.size() != 2 || mana.size() != 2)
            throw new IllegalArgumentException("two scoring and two mana squares required");
        if (scoring.stream().anyMatch(s -> !Board.isInside(s) || s.y() != 3)
                || mana.stream().anyMatch(s -> !Board.isInside(s) || (s.y() != 2 && s.y() != 4)))
            throw new IllegalArgumentException("objectives must occupy the three central rows");
        var unique = new HashSet<>(scoring); unique.addAll(mana);
        if (unique.size() != 4 || !mirrored(scoring) || !mirrored(mana)
                || mana.get(0).y() == mana.get(1).y())
            throw new IllegalArgumentException("objectives must be distinct mirrored pairs");
    }

    private static boolean mirrored(List<Square> pair) {
        return pair.get(0).x() + pair.get(1).x() == 6 && pair.get(0).y() + pair.get(1).y() == 6;
    }

    /** A valid fixed layout for explicit fixture/custom-state construction. */
    public static ObjectiveLayout standard() {
        return new ObjectiveLayout(List.of(new Square(1, 3), new Square(5, 3)),
                List.of(new Square(3, 2), new Square(3, 4)));
    }

    static ObjectiveLayout generate(GameRandom random) {
        int scoreX = random.nextInt(3), manaX = random.nextInt(7);
        return new ObjectiveLayout(List.of(new Square(scoreX, 3), new Square(6 - scoreX, 3)),
                List.of(new Square(manaX, 2), new Square(6 - manaX, 4)));
    }

    /** Points earned by pieces still occupying scoring squares at own-turn start. */
    public int scoreBonus(Board board, PlayerColor color) { return occupied(board, color, scoring); }

    /** Additional income from occupied mana squares, before applying the normal cap. */
    public int manaBonus(Board board, PlayerColor color) { return occupied(board, color, mana); }

    private int occupied(Board board, PlayerColor color, List<Square> squares) {
        return (int) squares.stream().filter(s -> board.pieceAt(s) != null
                && board.pieceAt(s).color() == color).count();
    }
}

package deckduel.core.duel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import deckduel.core.PlayerColor;
import deckduel.core.Square;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityValidationTest {

    @Test
    void pieceIdsMustMatchTheirStartingSide() {
        for (int id = 0; id < 14; id++) {
            int candidate = id;
            PlayerColor owner = id < 6 || id == 12 ? PlayerColor.BLUE : PlayerColor.RED;
            assertDoesNotThrow(() -> new Piece(candidate, owner));
            assertThrows(IllegalArgumentException.class, () -> new Piece(candidate, owner.opponent()));
        }
        for (int id : new int[] {-1, 14, 99, Integer.MAX_VALUE}) {
            for (PlayerColor color : PlayerColor.values()) {
                assertThrows(IllegalArgumentException.class, () -> new Piece(id, color));
            }
        }
        assertThrows(NullPointerException.class, () -> new Piece(0, null));
    }

    @Test
    void occupiedSquaresMustBeOnTheSevenBySevenBoard() {
        Piece piece = new Piece(0, PlayerColor.BLUE);
        for (Square square : new Square[] {new Square(-1, 0), new Square(0, -1),
                new Square(7, 0), new Square(0, 7)}) {
            assertThrows(IllegalArgumentException.class, () -> new PlacedPiece(piece, square));
        }
        assertDoesNotThrow(() -> new PlacedPiece(piece, new Square(0, 0)));
        assertDoesNotThrow(() -> new PlacedPiece(piece, new Square(6, 6)));
    }

    @Test
    void skillIdentitiesRequireKnownDefinitionsAndCanonicalCopyNumbers() {
        for (String definition : RulesConfig.CANONICAL_SKILL_IDS) {
            for (int ordinal = 1; ordinal <= 4; ordinal++) {
                String instance = definition + "#" + ordinal;
                assertDoesNotThrow(() -> new CardInstance(instance, definition));
            }
        }
        for (String instance : new String[] {"", "sidestep", "strike#1", "sidestep#0",
                "sidestep#5", "sidestep#01", "sidestep#-1", "sidestep#x", "x".repeat(65)}) {
            assertThrows(IllegalArgumentException.class, () -> new CardInstance(instance, "sidestep"));
        }
        assertThrows(IllegalArgumentException.class, () -> new CardInstance("unknown#1", "unknown"));
        assertThrows(IllegalArgumentException.class, () -> new CardInstance("x#1", "x".repeat(65)));
        assertThrows(NullPointerException.class, () -> new CardInstance(null, "sidestep"));
        assertThrows(NullPointerException.class, () -> new CardInstance("sidestep#1", null));
    }

    @Test
    void deserializationAlsoRejectsInvalidSkillIdentity() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes) {
            { enableReplaceObject(true); }
            @Override
            protected Object replaceObject(Object object) {
                return "sidestep".equals(object) ? "unknown" : object;
            }
        }) {
            output.writeObject(new CardInstance("sidestep#1", "sidestep"));
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThrows(InvalidObjectException.class, input::readObject);
        }
    }
}

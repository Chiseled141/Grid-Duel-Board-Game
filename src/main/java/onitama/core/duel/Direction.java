package onitama.core.duel;

/**
 * Cardinal directions in canonical coordinate space.
 */
public enum Direction {
    NORTH, // y - 1
    EAST,  // x + 1
    SOUTH, // y + 1
    WEST;  // x - 1

    public int dx() {
        return switch (this) {
            case EAST -> 1;
            case WEST -> -1;
            default -> 0;
        };
    }

    public int dy() {
        return switch (this) {
            case SOUTH -> 1;
            case NORTH -> -1;
            default -> 0;
        };
    }

    public onitama.core.Square step(onitama.core.Square from) {
        return new onitama.core.Square(from.x() + dx(), from.y() + dy());
    }
}

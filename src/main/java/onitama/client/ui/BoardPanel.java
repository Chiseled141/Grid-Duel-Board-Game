package onitama.client.ui;

import onitama.core.Board;
import onitama.core.GameState;
import onitama.core.Move;
import onitama.core.Piece;
import onitama.core.PlayerColor;
import onitama.core.Square;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JComponent;

/**
 * The board canvas: a custom {@code paintComponent} drawing the 5x5 grid,
 * pieces, selection ring, legal-move dots and the last-move highlight. The
 * board is always drawn from the viewer's side (the viewer's home row at the
 * bottom); canonical coordinates are mapped through a 180-degree flip for
 * Red. Reused by the game screen and the replay viewer.
 */
public final class BoardPanel extends JComponent {

    private static final int CELL = 72;
    private static final int MARGIN = 16;
    private static final int SIZE = 2 * MARGIN + Board.SIZE * CELL;

    private GameState state;
    private PlayerColor viewColor = PlayerColor.BLUE;
    private Square selectedSquare;
    private List<Square> targets = List.of();
    private Consumer<Square> clickHandler;

    /** Creates the panel and wires the mouse click mapping. */
    public BoardPanel() {
        setPreferredSize(new Dimension(SIZE, SIZE));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                Square square = squareAt(event.getX(), event.getY());
                if (square != null && clickHandler != null) {
                    clickHandler.accept(square);
                }
            }
        });
    }

    /** Sets the state to render and the side to view from. */
    public void setView(GameState state, PlayerColor viewColor) {
        this.state = state;
        this.viewColor = viewColor;
        repaint();
    }

    /** Sets the selected square and the highlighted legal destinations. */
    public void setSelection(Square selectedSquare, List<Square> targets) {
        this.selectedSquare = selectedSquare;
        this.targets = targets == null ? List.of() : targets;
        repaint();
    }

    /** Sets the move to highlight (from/to squares), or null for none. */
    public void setLastMove(Move lastMove) {
        this.lastMove = lastMove;
        repaint();
    }

    /** Registers the callback invoked with the clicked canonical square. */
    public void onClick(Consumer<Square> clickHandler) {
        this.clickHandler = clickHandler;
    }

    private Move lastMove;

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.BACKGROUND);
        g.fillRect(0, 0, getWidth(), getHeight());
        if (state == null) {
            return;
        }
        for (int y = 0; y < Board.SIZE; y++) {
            for (int x = 0; x < Board.SIZE; x++) {
                drawSquare(g, x, y);
            }
        }
        if (lastMove != null) {
            overlay(g, lastMove.from(), Theme.LAST_MOVE);
            overlay(g, lastMove.to(), Theme.LAST_MOVE);
        }
        for (int y = 0; y < Board.SIZE; y++) {
            for (int x = 0; x < Board.SIZE; x++) {
                Piece piece = state.board().pieceAt(x, y);
                if (piece != null) {
                    drawPiece(g, x, y, piece);
                }
            }
        }
        for (Square target : targets) {
            drawTargetDot(g, target);
        }
        if (selectedSquare != null) {
            drawSelectionRing(g, selectedSquare);
        }
    }

    private void drawSquare(Graphics2D g, int x, int y) {
        int px = displayX(x) * CELL + MARGIN;
        int py = displayY(y) * CELL + MARGIN;
        g.setColor((x + y) % 2 == 0 ? Theme.BOARD_LIGHT : Theme.BOARD_DARK);
        g.fillRect(px, py, CELL, CELL);
        g.setColor(new Color(0, 0, 0, 40));
        g.drawRect(px, py, CELL, CELL);
    }

    private void drawPiece(Graphics2D g, int x, int y, Piece piece) {
        int px = displayX(x) * CELL + MARGIN;
        int py = displayY(y) * CELL + MARGIN;
        int cx = px + CELL / 2;
        int cy = py + CELL / 2;
        int radius = CELL / 2 - 10;
        g.setColor(piece.color() == PlayerColor.BLUE ? Theme.BLUE_PIECE : Theme.RED_PIECE);
        g.fillOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
        g.setColor(Color.WHITE);
        if (piece.master()) {
            // Masters wear a white ring; students are plain discs.
            g.drawOval(cx - radius + 5, cy - radius + 5, 2 * radius - 10, 2 * radius - 10);
        }
        g.setFont(Theme.FONT_BOLD);
        String label = piece.master() ? "M" : "S";
        var metrics = g.getFontMetrics();
        g.drawString(label, cx - metrics.stringWidth(label) / 2,
                cy + metrics.getAscent() / 2 - 2);
    }

    private void drawTargetDot(Graphics2D g, Square target) {
        int cx = displayX(target.x()) * CELL + MARGIN + CELL / 2;
        int cy = displayY(target.y()) * CELL + MARGIN + CELL / 2;
        g.setColor(Theme.TARGET_DOT);
        Piece occupant = state.board().pieceAt(target);
        int radius = occupant == null ? 8 : CELL / 2 - 6;
        if (occupant == null) {
            g.fillOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
        } else {
            // Enemy piece: ring around it, signaling a capture.
            g.drawOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
            g.drawOval(cx - radius + 3, cy - radius + 3, 2 * radius - 6, 2 * radius - 6);
        }
    }

    private void drawSelectionRing(Graphics2D g, Square square) {
        int px = displayX(square.x()) * CELL + MARGIN;
        int py = displayY(square.y()) * CELL + MARGIN;
        g.setColor(Theme.SELECTION);
        for (int width = 1; width <= 3; width++) {
            g.drawRect(px + width, py + width, CELL - 2 * width, CELL - 2 * width);
        }
    }

    private void overlay(Graphics2D g, Square square, Color color) {
        int px = displayX(square.x()) * CELL + MARGIN;
        int py = displayY(square.y()) * CELL + MARGIN;
        g.setColor(color);
        g.fillRect(px, py, CELL, CELL);
    }

    /** Maps a mouse position to a canonical square, or null outside the grid. */
    private Square squareAt(int mouseX, int mouseY) {
        int displayCol = (mouseX - MARGIN) / CELL;
        int displayRow = (mouseY - MARGIN) / CELL;
        if (displayCol < 0 || displayCol >= Board.SIZE || displayRow < 0 || displayRow >= Board.SIZE) {
            return null;
        }
        int x = viewColor == PlayerColor.BLUE ? displayCol : Board.SIZE - 1 - displayCol;
        int y = viewColor == PlayerColor.BLUE ? displayRow : Board.SIZE - 1 - displayRow;
        return new Square(x, y);
    }

    private int displayX(int x) {
        return viewColor == PlayerColor.BLUE ? x : Board.SIZE - 1 - x;
    }

    private int displayY(int y) {
        return viewColor == PlayerColor.BLUE ? y : Board.SIZE - 1 - y;
    }
}

package onitama.client.ui;

import onitama.core.Board;
import onitama.core.GameState;
import onitama.core.Move;
import onitama.core.Piece;
import onitama.core.PlayerColor;
import onitama.core.Square;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
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
 * Red. Hand-designed tiles/pieces from the AssetStore replace the drawn
 * versions file by file. Reused by the game screen and the replay viewer.
 */
public final class BoardPanel extends JComponent {

    private static final int CELL = 64;
    private static final int MARGIN = 8;
    private static final int SIZE = 2 * MARGIN + Board.SIZE * CELL;

    private GameState state;
    private PlayerColor viewColor = PlayerColor.BLUE;
    private Square selectedSquare;
    private List<Square> targets = List.of();
    private Consumer<Square> clickHandler;

    private Move lastMove;

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

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = UiKit.nice(graphics);
        g.setColor(Theme.BG);
        g.fillRect(0, 0, getWidth(), getHeight());
        if (state == null) {
            g.dispose();
            return;
        }
        for (int y = 0; y < Board.SIZE; y++) {
            for (int x = 0; x < Board.SIZE; x++) {
                drawSquare(g, x, y);
            }
        }
        if (lastMove != null) {
            overlay(g, lastMove.from());
            overlay(g, lastMove.to());
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
        g.dispose();
    }

    private void drawSquare(Graphics2D g, int x, int y) {
        Image tile = AssetStore.optional(
                (x + y) % 2 == 0 ? "board-tile-light.png" : "board-tile-dark.png");
        int px = displayX(x) * CELL + MARGIN;
        int py = displayY(y) * CELL + MARGIN;
        if (tile != null) {
            g.drawImage(tile, px, py, CELL, CELL, null);
        } else {
            g.setColor((x + y) % 2 == 0 ? Theme.BOARD_LIGHT : Theme.BOARD_DARK);
            g.fillRect(px, py, CELL, CELL);
        }
        g.setColor(Theme.OUTLINE);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(px, py, CELL, CELL);
    }

    private void drawPiece(Graphics2D g, int x, int y, Piece piece) {
        Image sprite = AssetStore.optional(
                "piece-" + piece.color().name().toLowerCase()
                        + (piece.master() ? "-master" : "-student") + ".png");
        int px = displayX(x) * CELL + MARGIN;
        int py = displayY(y) * CELL + MARGIN;
        int cx = px + CELL / 2;
        int cy = py + CELL / 2;
        if (sprite != null) {
            g.drawImage(sprite, px + 4, py + 4, CELL - 8, CELL - 8, null);
            return;
        }
        int radius = CELL / 2 - 12;
        g.setColor(piece.color() == PlayerColor.BLUE ? Theme.SKY : Theme.CORAL);
        g.fillOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(2.5f));
        g.drawOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
        if (piece.master()) {
            // Masters carry the gold starburst mark.
            UiKit.starburst(g, cx, cy, radius - 7);
        } else {
            g.setColor(Theme.CREAM);
            g.setFont(Theme.bold(16f));
            var metrics = g.getFontMetrics();
            g.drawString("S", cx - metrics.stringWidth("S") / 2,
                    cy + metrics.getAscent() / 2 - 2);
        }
    }

    private void drawTargetDot(Graphics2D g, Square target) {
        int cx = displayX(target.x()) * CELL + MARGIN + CELL / 2;
        int cy = displayY(target.y()) * CELL + MARGIN + CELL / 2;
        Piece occupant = state.board().pieceAt(target);
        g.setColor(Theme.TEAL);
        if (occupant == null) {
            g.fillOval(cx - 9, cy - 9, 18, 18);
        } else {
            // Enemy piece: double ring signals a capture.
            g.setStroke(new BasicStroke(3f));
            int radius = CELL / 2 - 7;
            g.drawOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
            g.drawOval(cx - radius + 4, cy - radius + 4, 2 * radius - 8, 2 * radius - 8);
        }
    }

    private void drawSelectionRing(Graphics2D g, Square square) {
        int px = displayX(square.x()) * CELL + MARGIN;
        int py = displayY(square.y()) * CELL + MARGIN;
        g.setColor(Theme.AMBER);
        g.setStroke(new BasicStroke(3.5f));
        g.drawRect(px + 2, py + 2, CELL - 4, CELL - 4);
    }

    private void overlay(Graphics2D g, Square square) {
        int px = displayX(square.x()) * CELL + MARGIN;
        int py = displayY(square.y()) * CELL + MARGIN;
        g.setColor(new Color(252, 186, 40, 56));
        g.fillRect(px, py, CELL, CELL);
    }

    /** Maps a mouse position to a canonical square, or null outside the grid. */
    private Square squareAt(int mouseX, int mouseY) {
        int displayCol = (mouseX - MARGIN) / CELL;
        int displayRow = (mouseY - MARGIN) / CELL;
        if (displayCol < 0 || displayCol >= Board.SIZE || displayRow < 0 || displayRow >= Board.SIZE) {
            return null;
        }
        // Same flip as painting: the viewer's own home row (Blue y=0, Red y=4)
        // must sit at the BOTTOM of the screen.
        int x = viewColor == PlayerColor.BLUE ? displayCol : Board.SIZE - 1 - displayCol;
        int y = viewColor == PlayerColor.BLUE ? Board.SIZE - 1 - displayRow : displayRow;
        return new Square(x, y);
    }

    private int displayX(int x) {
        return viewColor == PlayerColor.BLUE ? x : Board.SIZE - 1 - x;
    }

    private int displayY(int y) {
        return viewColor == PlayerColor.BLUE ? Board.SIZE - 1 - y : y;
    }
}

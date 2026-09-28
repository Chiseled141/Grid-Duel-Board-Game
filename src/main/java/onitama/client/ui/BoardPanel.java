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
 * board is responsive — the cell size is recomputed from the panel's current
 * size, so the board fills whatever space the window gives it — and always
 * drawn from the viewer's side (own home row at the bottom); canonical
 * coordinates are mapped through a 180-degree flip for Red. Hand-designed
 * tiles/pieces from the AssetStore replace the drawn versions file by file.
 * Reused by the game screen and the replay viewer.
 */
public final class BoardPanel extends JComponent {

    private static final int MARGIN = 10;
    private static final int MIN_CELL = 30;

    private GameState state;
    private PlayerColor viewColor = PlayerColor.BLUE;
    private Square selectedSquare;
    private List<Square> targets = List.of();
    private Consumer<Square> clickHandler;

    // Current geometry, recomputed on every paint and reused by click mapping.
    private int cell = 64;
    private int originX;
    private int originY;

    private Move lastMove;
    private Square hoveredSquare;
    private CaptureEffect captureEffect;
    private Move animMove;
    private long animStart;
    private Color animFill;
    private boolean animMaster;

    /** A short expanding-ring animation where a piece was captured. */
    private record CaptureEffect(Square square, Color color, long startMillis) {
    }

    /** Creates the panel and wires the mouse click mapping. */
    public BoardPanel() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                Square square = squareAt(event.getX(), event.getY());
                if (square != null && clickHandler != null) {
                    clickHandler.accept(square);
                }
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                Square square = squareAt(event.getX(), event.getY());
                if (square != hoveredSquare
                        && (square == null || !square.equals(hoveredSquare))) {
                    hoveredSquare = square;
                    repaint();
                }
            }
        });
    }

    /**
     * Plays a brief expanding-ring effect where a piece was just captured,
     * in the capturing player's color (§23: tasteful, no neon).
     */
    public void playCaptureEffect(Square square, PlayerColor byColor) {
        captureEffect = new CaptureEffect(square, Theme.playerColor(byColor),
                System.currentTimeMillis());
        javax.swing.Timer animation = new javax.swing.Timer(40, event -> {
            if (System.currentTimeMillis() - captureEffect.startMillis > 450) {
                ((javax.swing.Timer) event.getSource()).stop();
                captureEffect = null;
            }
            repaint();
        });
        animation.start();
    }

    /**
     * Fits the board into the panel: the largest square cell size that fits
     * both dimensions, centered.
     */
    private void computeGeometry() {
        int width = Math.max(getWidth(), 2 * MARGIN + Board.SIZE * MIN_CELL);
        int height = Math.max(getHeight(), 2 * MARGIN + Board.SIZE * MIN_CELL);
        cell = Math.max(MIN_CELL, Math.min((width - 2 * MARGIN) / Board.SIZE,
                (height - 2 * MARGIN) / Board.SIZE));
        originX = (width - Board.SIZE * cell) / 2;
        originY = (height - Board.SIZE * cell) / 2;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(2 * MARGIN + Board.SIZE * 64, 2 * MARGIN + Board.SIZE * 64);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(2 * MARGIN + Board.SIZE * MIN_CELL,
                2 * MARGIN + Board.SIZE * MIN_CELL);
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

    /**
     * Animates the latest half-move: the moving figurine slides from its
     * origin to the destination instead of teleporting (§13).
     */
    public void animateMove(Move move, PlayerColor mover, boolean master) {
        animMove = move;
        animStart = System.currentTimeMillis();
        animFill = Theme.playerColor(mover);
        animMaster = master;
        javax.swing.Timer animation = new javax.swing.Timer(16, event -> {
            if (System.currentTimeMillis() - animStart > 240) {
                ((javax.swing.Timer) event.getSource()).stop();
                animMove = null;
            }
            repaint();
        });
        animation.start();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = UiKit.nice(graphics);
        g.setColor(Theme.BG);
        g.fillRect(0, 0, getWidth(), getHeight());
        computeGeometry();
        if (state == null) {
            g.dispose();
            return;
        }
        // Premium tabletop frame: shadow, warm wooden band, inner rim, rivets.
        int grid = Board.SIZE * cell;
        int frameOut = 12;
        g.setColor(Theme.SHADOW);
        g.fillRoundRect(originX - frameOut + 6, originY - frameOut + 6,
                grid + 2 * frameOut, grid + 2 * frameOut, 22, 22);
        g.setColor(Theme.BOARD_FRAME);
        g.fillRoundRect(originX - frameOut, originY - frameOut,
                grid + 2 * frameOut, grid + 2 * frameOut, 22, 22);
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(originX - frameOut, originY - frameOut,
                grid + 2 * frameOut, grid + 2 * frameOut, 22, 22);
        g.setColor(Theme.BOARD_DARK);
        g.drawRoundRect(originX - frameOut + 4, originY - frameOut + 4,
                grid + 2 * frameOut - 8, grid + 2 * frameOut - 8, 16, 16);
        for (int[] corner : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
            int rivetX = originX - frameOut / 2 + corner[0] * (grid + frameOut);
            int rivetY = originY - frameOut / 2 + corner[1] * (grid + frameOut);
            g.setColor(Theme.INK);
            g.fillOval(rivetX - 3, rivetY - 3, 6, 6);
        }
        for (int y = 0; y < Board.SIZE; y++) {
            for (int x = 0; x < Board.SIZE; x++) {
                drawSquare(g, x, y);
            }
        }
        // Temple arch markers on the two home-row center squares.
        drawTempleMark(g, new Square(2, 0));
        drawTempleMark(g, new Square(2, 4));
        if (lastMove != null) {
            overlay(g, lastMove.from());
            overlay(g, lastMove.to());
        }
        boolean animating = animMove != null;
        Square animTo = animating ? animMove.to() : null;
        for (int y = 0; y < Board.SIZE; y++) {
            for (int x = 0; x < Board.SIZE; x++) {
                if (animating && animTo.equals(new Square(x, y))) {
                    continue; // the moving figurine is drawn mid-flight below
                }
                Piece piece = state.board().pieceAt(x, y);
                if (piece != null) {
                    drawPiece(g, x, y, piece);
                }
            }
        }
        if (animating) {
            drawMovingFigurine(g);
        }
        for (Square target : targets) {
            drawTargetDot(g, target);
        }
        if (selectedSquare != null) {
            drawSelectionRing(g, selectedSquare);
        }
        if (captureEffect != null) {
            drawCaptureEffect(g, captureEffect);
        }
        g.dispose();
    }

    /** Expanding, fading ring at the capture square. */
    private void drawCaptureEffect(Graphics2D g, CaptureEffect effect) {
        long elapsed = System.currentTimeMillis() - effect.startMillis();
        double progress = Math.min(1.0, elapsed / 450.0);
        int cx = displayX(effect.square().x()) * cell + originX + cell / 2;
        int cy = displayY(effect.square().y()) * cell + originY + cell / 2;
        int radius = (int) (cell * 0.3 + cell * 0.45 * progress);
        g.setColor(new Color(effect.color().getRed(), effect.color().getGreen(),
                effect.color().getBlue(), (int) (200 * (1 - progress))));
        g.setStroke(new BasicStroke(4f));
        g.drawOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
    }

    /** Draws the sliding figurine between origin and destination. */
    private void drawMovingFigurine(Graphics2D g) {
        long elapsed = System.currentTimeMillis() - animStart;
        double progress = Math.min(1.0, elapsed / 240.0);
        double eased = 1 - (1 - progress) * (1 - progress); // ease-out
        int fromX = displayX(animMove.from().x()) * cell + originX + cell / 2;
        int fromY = displayY(animMove.from().y()) * cell + originY;
        int toX = displayX(animMove.to().x()) * cell + originX + cell / 2;
        int toY = displayY(animMove.to().y()) * cell + originY;
        int cx = (int) (fromX + (toX - fromX) * eased);
        int baseY = (int) (fromY + (toY - fromY) * eased + cell - 7
                - Math.sin(progress * Math.PI) * cell * 0.18);
        UiKit.drawFigurine(g, cx, baseY, (int) (cell * (animMaster ? 0.82 : 0.64)),
                animFill, animMaster);
    }

    /** A subtle torii-style marker on the temple arch squares. */
    private void drawTempleMark(Graphics2D g, Square square) {
        int px = displayX(square.x()) * cell + originX;
        int py = displayY(square.y()) * cell + originY;
        g.setColor(new Color(35, 31, 32, 70));
        g.setStroke(new BasicStroke(2f));
        int top = py + cell / 5;
        g.drawLine(px + cell / 4, top, px + cell - cell / 4, top);
        g.drawLine(px + cell / 3, top + 3, px + cell / 3, top + cell / 4);
        g.drawLine(px + cell - cell / 3, top + 3, px + cell - cell / 3, top + cell / 4);
    }

    private void drawSquare(Graphics2D g, int x, int y) {
        Image tile = AssetStore.optional(
                (x + y) % 2 == 0 ? "board-tile-light.png" : "board-tile-dark.png");
        int px = displayX(x) * cell + originX;
        int py = displayY(y) * cell + originY;
        if (tile != null) {
            g.drawImage(tile, px, py, cell, cell, null);
        } else {
            g.setColor((x + y) % 2 == 0 ? Theme.BOARD_LIGHT : Theme.BOARD_DARK);
            g.fillRect(px, py, cell, cell);
        }
        g.setColor(Theme.OUTLINE);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(px, py, cell, cell);
    }

    private void drawPiece(Graphics2D g, int x, int y, Piece piece) {
        Image sprite = AssetStore.optional(
                "piece-" + piece.color().name().toLowerCase()
                        + (piece.master() ? "-master" : "-student") + ".png");
        int px = displayX(x) * cell + originX;
        int py = displayY(y) * cell + originY;
        int cx = px + cell / 2;
        if (sprite != null) {
            int inset = cell / 10;
            g.drawImage(sprite, px + inset, py + inset, cell - 2 * inset, cell - 2 * inset, null);
            return;
        }
        // Pawn silhouettes: shorter students, taller masters (like the real game).
        int height = (int) (cell * (piece.master() ? 0.82 : 0.64));
        int baseY = py + cell - 7;
        Color fill = piece.color() == PlayerColor.BLUE ? Theme.SKY : Theme.CORAL;
        UiKit.drawFigurine(g, cx, baseY, height, fill, piece.master());
    }

    private void drawTargetDot(Graphics2D g, Square target) {
        int cx = displayX(target.x()) * cell + originX + cell / 2;
        int cy = displayY(target.y()) * cell + originY + cell / 2;
        Piece occupant = state.board().pieceAt(target);
        boolean hovered = target.equals(hoveredSquare);
        // Legal moves glow in the player's own accent color (§23).
        Color accent = Theme.playerColor(viewColor);
        if (hovered) {
            accent = UiKit.lighten(accent);
        }
        g.setColor(accent);
        if (occupant == null) {
            int dot = hovered ? Math.max(16, cell / 3) : Math.max(12, cell / 4);
            g.fillOval(cx - dot / 2, cy - dot / 2, dot, dot);
            g.setColor(Theme.INK);
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(cx - dot / 2, cy - dot / 2, dot, dot);
        } else {
            // Enemy piece: double ring signals a capture.
            g.setStroke(new BasicStroke(hovered ? 4f : 3f));
            int radius = cell / 2 - (hovered ? 4 : 6);
            g.drawOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
            g.drawOval(cx - radius + 4, cy - radius + 4, 2 * radius - 8, 2 * radius - 8);
        }
    }

    private void drawSelectionRing(Graphics2D g, Square square) {
        int px = displayX(square.x()) * cell + originX;
        int py = displayY(square.y()) * cell + originY;
        g.setColor(Theme.playerColor(viewColor));
        g.setStroke(new BasicStroke(3.5f));
        g.drawRect(px + 2, py + 2, cell - 4, cell - 4);
    }

    private void overlay(Graphics2D g, Square square) {
        int px = displayX(square.x()) * cell + originX;
        int py = displayY(square.y()) * cell + originY;
        g.setColor(new Color(252, 186, 40, 56));
        g.fillRect(px, py, cell, cell);
    }

    /** Maps a mouse position to a canonical square, or null outside the grid. */
    private Square squareAt(int mouseX, int mouseY) {
        computeGeometry();
        int displayCol = (mouseX - originX) / cell;
        int displayRow = (mouseY - originY) / cell;
        if (mouseX < originX || mouseY < originY
                || displayCol < 0 || displayCol >= Board.SIZE
                || displayRow < 0 || displayRow >= Board.SIZE) {
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

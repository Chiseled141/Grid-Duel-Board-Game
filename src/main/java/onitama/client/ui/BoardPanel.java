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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * The board canvas, rendered from the designed mat artwork (board-empty.png:
 * parchment tiles, ink grid, torii temple marks and the wooden frame are all
 * baked into the image). Pieces are the designed figurine sprites. The board
 * is responsive — geometry is recomputed from the panel's current size — and
 * always drawn from the viewer's side (own home row at the bottom). Highlights
 * (selection, legal moves, last move, capture effect, move animation) layer
 * on top; the drawn tiles/figurines remain the fallback when the artwork is
 * missing. Used by the game screen.
 */
public final class BoardPanel extends JComponent {

    private static final int MIN_CELL = 30;
    /** The playfield's share of the board image (measured from the artwork). */
    private static final double GRID_SHARE = 0.89;
    private static final double GRID_INSET = 0.053;

    private GameState state;
    private PlayerColor viewColor = PlayerColor.BLUE;
    private Square selectedSquare;
    private List<Square> targets = List.of();
    private Consumer<Square> clickHandler;

    // Current geometry, recomputed on every paint and reused by click mapping.
    private int cell = 64;
    private int originX;
    private int originY;
    private int imgX;
    private int imgY;
    private int imgSize;

    private Move lastMove;
    private Square hoveredSquare;
    private CaptureEffect captureEffect;
    private Move animMove;
    private long animStart;
    private PlayerColor animPlayer;
    private boolean animMaster;
    /** Gentle breathing of the selection ring while a piece is selected. */
    private float selectionPulse;
    private Timer selectionPulseTimer;

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
        addMouseMotionListener(new MouseMotionAdapter() {
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
     * Fits the board: the mat image scales to the panel, and the playfield
     * (its inner grid) defines the cell size and origin.
     */
    private void computeGeometry() {
        int width = Math.max(getWidth(), 2 * MIN_CELL * Board.SIZE);
        int height = Math.max(getHeight(), 2 * MIN_CELL * Board.SIZE);
        imgSize = Math.min(width, height) - 12;
        int gridPx = (int) (imgSize * GRID_SHARE);
        cell = Math.max(MIN_CELL, gridPx / Board.SIZE);
        int span = cell * Board.SIZE;
        originX = (width - span) / 2;
        originY = (height - span) / 2;
        imgX = originX - (int) (imgSize * GRID_INSET);
        imgY = originY - (int) (imgSize * GRID_INSET);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(2 * MIN_CELL * Board.SIZE + 40,
                2 * MIN_CELL * Board.SIZE + 40);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(2 * MIN_CELL * Board.SIZE, 2 * MIN_CELL * Board.SIZE);
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
        updateSelectionPulse();
        repaint();
    }

    /** Clears all selection highlights and stops their animation timer. */
    public void clearHighlights() {
        setSelection(null, List.of());
    }

    /** Runs the ring pulse exactly while a selection is on the board. */
    private void updateSelectionPulse() {
        boolean active = selectedSquare != null;
        if (active && selectionPulseTimer == null) {
            selectionPulseTimer = new Timer(60, event -> {
                selectionPulse = (selectionPulse + 0.09f) % 1f;
                repaint();
            });
            selectionPulseTimer.start();
        } else if (!active && selectionPulseTimer != null) {
            selectionPulseTimer.stop();
            selectionPulseTimer = null;
            selectionPulse = 0f;
        }
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

    // The app-wide piece style (1 ink tokens, 2 seal stones, 3 ink
    // silhouettes); shared by every board in the application.
    private static volatile int pieceStyle = 1;

    /** Sets the piece style used by all boards (lobby setting). */
    public static void setDefaultPieceStyle(int style) {
        pieceStyle = Math.max(1, Math.min(3, style));
    }

    /** The piece sprite for the current style, falling back to the legacy set. */
    private static Image pieceSprite(boolean master, PlayerColor color) {
        String kind = master ? "master" : "student";
        String colorName = color.name().toLowerCase();
        Image styled = AssetStore.optional("pieces/style" + pieceStyle
                + "-" + kind + "-" + colorName + ".png");
        if (styled != null) {
            return styled;
        }
        return AssetStore.optional("piece-" + kind + "-" + colorName + ".png");
    }

    /**
     * Animates the latest half-move: the moving figurine slides from its
     * origin to the destination instead of teleporting (§13).
     */
    public void animateMove(Move move, PlayerColor mover, boolean master) {
        animMove = move;
        animStart = System.currentTimeMillis();
        animPlayer = mover;
        animMaster = master;
        Timer animation = new Timer(16, event -> {
            if (System.currentTimeMillis() - animStart > 240) {
                ((Timer) event.getSource()).stop();
                animMove = null;
            }
            repaint();
        });
        animation.start();
    }

    /**
     * Plays a brief expanding-ring effect where a piece was just captured,
     * in the capturing player's color (§23: tasteful, no neon).
     */
    public void playCaptureEffect(Square square, PlayerColor byColor) {
        captureEffect = new CaptureEffect(square, Theme.playerColor(byColor),
                System.currentTimeMillis());
        Timer animation = new Timer(40, event -> {
            if (System.currentTimeMillis() - captureEffect.startMillis > 450) {
                ((Timer) event.getSource()).stop();
                captureEffect = null;
            }
            repaint();
        });
        animation.start();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = UiKit.nice(graphics);
        Image mat = AssetStore.optional("board-empty.png");
        if (mat == null) {
            // No mat artwork: paint the dark app background behind the
            // drawn fallback frame.
            g.setColor(Theme.BG);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        computeGeometry();
        if (state == null) {
            g.dispose();
            return;
        }
        if (mat != null) {
            // The mat (with its own frame) lies directly on the scene.
            g.drawImage(mat, imgX, imgY, imgSize, imgSize, null);
        } else {
            paintFallbackFrame(g);
        }
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
            drawMovingPiece(g);
        }
        if (selectedSquare != null) {
            drawTargetConnectors(g);
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

    /** Drawn wooden frame + parchment tiles when the mat artwork is absent. */
    private void paintFallbackFrame(Graphics2D g) {
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
        for (int y = 0; y < Board.SIZE; y++) {
            for (int x = 0; x < Board.SIZE; x++) {
                int px = displayX(x) * cell + originX;
                int py = displayY(y) * cell + originY;
                g.setColor((x + y) % 2 == 0 ? Theme.BOARD_LIGHT : Theme.BOARD_LIGHT.darker());
                g.fillRect(px, py, cell, cell);
                g.setColor(Theme.withAlpha(Theme.INK, 70));
                g.drawRect(px, py, cell, cell);
            }
        }
        drawTempleMark(g, new Square(2, 0));
        drawTempleMark(g, new Square(2, 4));
    }

    /** A subtle torii-style marker on the temple arch squares (fallback only). */
    private void drawTempleMark(Graphics2D g, Square square) {
        int px = displayX(square.x()) * cell + originX;
        int py = displayY(square.y()) * cell + originY;
        g.setColor(Theme.withAlpha(Theme.INK, 70));
        g.setStroke(new BasicStroke(2f));
        int top = py + cell / 5;
        g.drawLine(px + cell / 4, top, px + cell - cell / 4, top);
        g.drawLine(px + cell / 3, top + 3, px + cell / 3, top + cell / 4);
        g.drawLine(px + cell - cell / 3, top + 3, px + cell - cell / 3, top + cell / 4);
    }

    private void drawPiece(Graphics2D g, int x, int y, Piece piece) {
        Image sprite = pieceSprite(piece.master(), piece.color());
        int px = displayX(x) * cell + originX;
        int py = displayY(y) * cell + originY;
        if (sprite != null) {
            int inset = (int) (cell * (piece.master() ? 0.06 : 0.10));
            g.drawImage(sprite, px + inset, py + inset,
                    cell - 2 * inset, cell - 2 * inset, null);
            return;
        }
        Color fill = piece.color() == PlayerColor.BLUE ? Theme.P1 : Theme.P2;
        UiKit.drawFigurine(g, px + cell / 2, py + cell - 7,
                (int) (cell * (piece.master() ? 0.82 : 0.64)), fill, piece.master());
    }

    /** Draws the sliding figurine between origin and destination. */
    private void drawMovingPiece(Graphics2D g) {
        long elapsed = System.currentTimeMillis() - animStart;
        double progress = Math.min(1.0, elapsed / 240.0);
        double eased = 1 - (1 - progress) * (1 - progress); // ease-out
        boolean master = animMaster;
        Image sprite = pieceSprite(master, animPlayer);
        int fromX = displayX(animMove.from().x()) * cell + originX + cell / 2;
        int fromY = displayY(animMove.from().y()) * cell + originY;
        int toX = displayX(animMove.to().x()) * cell + originX + cell / 2;
        int toY = displayY(animMove.to().y()) * cell + originY;
        int cx = (int) (fromX + (toX - fromX) * eased);
        int cy = (int) (fromY + (toY - fromY) * eased - Math.sin(progress * Math.PI) * cell * 0.15);
        if (sprite != null) {
            int size = (int) (cell * (master ? 0.88 : 0.76));
            g.drawImage(sprite, cx - size / 2, cy - size / 2, size, size, null);
        } else {
            UiKit.drawFigurine(g, cx, cy + cell / 2 - 7,
                    (int) (cell * (master ? 0.82 : 0.64)),
                    Theme.playerColor(animPlayer), master);
        }
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
        // Free squares get a half-transparent dot (solid on hover); enemy
        // pieces get a double capture ring.
        g.setColor(new Color(accent.getRed(), accent.getGreen(),
                accent.getBlue(), hovered ? 255 : 190));
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
        // Bold 4px ring that breathes softly so the eye locks onto it.
        g.setColor(Theme.withAlpha(Theme.playerColor(viewColor),
                215 + (int) (40 * selectionPulse)));
        g.setStroke(new BasicStroke(4f));
        g.drawOval(px + 3, py + 3, cell - 6, cell - 6);
    }

    /** Faint dashed gold lines from the selected square to each legal target. */
    private void drawTargetConnectors(Graphics2D g) {
        int fromX = displayX(selectedSquare.x()) * cell + originX + cell / 2;
        int fromY = displayY(selectedSquare.y()) * cell + originY + cell / 2;
        g.setColor(Theme.withAlpha(Theme.AMBER, 128));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{4f, 4f}, 0f));
        for (Square target : targets) {
            g.drawLine(fromX, fromY,
                    displayX(target.x()) * cell + originX + cell / 2,
                    displayY(target.y()) * cell + originY + cell / 2);
        }
    }

    private void overlay(Graphics2D g, Square square) {
        int px = displayX(square.x()) * cell + originX;
        int py = displayY(square.y()) * cell + originY;
        // Pale gold wash + a thin gold outline so the last-played squares
        // also read clearly on the light parchment mat.
        g.setColor(new Color(Theme.LAST_MOVE.getRed(), Theme.LAST_MOVE.getGreen(),
                Theme.LAST_MOVE.getBlue(), 170));
        g.fillRect(px, py, cell, cell);
        g.setColor(Theme.AMBER);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(px + 1, py + 1, cell - 2, cell - 2);
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

    /** Maps a mouse position to a canonical square, or null outside the grid. */
    private Square squareAt(int mouseX, int mouseY) {
        if (getWidth() < 100 || getHeight() < 100) {
            // Not laid out yet (or sliver-sized): geometry from a zero area
            // would map every click to a bogus square.
            return null;
        }
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

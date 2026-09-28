package onitama.client.ui;

import onitama.core.Piece;
import onitama.core.PlayerColor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;

import javax.swing.JComponent;

/**
 * A row of small piece discs showing captured pieces (or a muted placeholder
 * when empty). Pure presentation — reads its pieces from the model.
 */
public final class PieceTray extends JComponent {

    private static final int DISC = 16;
    private static final int STEP = 20;

    private List<Piece> pieces = List.of();
    private final String emptyText;

    /** Creates the tray; {@code emptyText} shows when no pieces are held. */
    public PieceTray(String emptyText) {
        this.emptyText = emptyText;
        setOpaque(false);
    }

    /** Replaces the displayed pieces. */
    public void setPieces(List<Piece> pieces) {
        this.pieces = List.copyOf(pieces);
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Math.max(64, 6 + pieces.size() * STEP), DISC + 6);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = UiKit.nice(graphics);
        if (pieces.isEmpty()) {
            g.setFont(Theme.normal(12f));
            g.setColor(Theme.MUTED);
            g.drawString(emptyText, 2, DISC / 2 + 5);
        } else {
            for (int i = 0; i < pieces.size(); i++) {
                Piece piece = pieces.get(i);
                int cx = 8 + i * STEP + DISC / 2;
                int baseY = DISC + 4;
                Color fill = piece.color() == PlayerColor.BLUE ? Theme.SKY : Theme.CORAL;
                UiKit.drawFigurine(g, cx, baseY, DISC + 4, fill, piece.master());
            }
        }
        g.dispose();
    }
}

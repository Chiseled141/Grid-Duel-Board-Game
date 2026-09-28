package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;
import onitama.core.GameState;
import onitama.core.Piece;
import onitama.net.GameOver;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;

/**
 * The game screen: opponent cards on top (patterns drawn from your own
 * perspective), the board in the center, your hand cards, the transit card,
 * the turn banner, move history and captured trays. Click a card and a piece
 * (either order) to see legal destinations, then click a destination to move.
 * ESC clears the selection.
 */
public final class GamePanel extends JPanel {

    private final ClientModel model;

    private final BoardPanel boardPanel = new BoardPanel();
    private final UiKit.HeadingLabel turnLabel = new UiKit.HeadingLabel(15f);
    private final JLabel opponentLabel = UiKit.label("", 14f);
    private final JComponent avatar = new JComponent() {
        {
            setPreferredSize(new java.awt.Dimension(40, 44));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = myColor() == null ? Theme.AMBER : Theme.playerColor(myColor());
            UiKit.drawFigurine(g, getWidth() / 2, getHeight() - 6,
                    getHeight() - 12, fill, true);
            g.dispose();
        }
    };
    private final JLabel nameLabel = UiKit.boldLabel("", 14f);
    private final JLabel statsLabel = UiKit.label("", 10f);
    private final JComponent onlineDot = new JComponent() {
        {
            setPreferredSize(new java.awt.Dimension(10, 10));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Theme.GREEN);
            g.fillOval(0, 0, 9, 9);
            g.setColor(Theme.INK);
            g.drawOval(0, 0, 9, 9);
            g.dispose();
        }
    };

    private onitama.core.PlayerColor myColor() {
        return model.myColor();
    }
    private final JLabel transitLabel = UiKit.label("", 14f);
    private final JPanel opponentCards = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
    private final JPanel myCards = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
    private final JList<ClientModel.MoveInfo> historyList = new JList<>();
    private final PieceTray myCapturesTray = new PieceTray("—");
    private final PieceTray enemyCapturesTray = new PieceTray("—");
    private JPanel myColumn;
    private int lastEffectMove = -1;

    /** Builds the game screen and subscribes it to the model. */
    public GamePanel(ClientModel model) {
        this.model = model;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(Theme.BG);

        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(Theme.BG);
        opponentLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        opponentCards.setBackground(Theme.BG);
        north.add(opponentLabel, BorderLayout.NORTH);
        north.add(opponentCards, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        // My cards live in a left column so the board can grow big in the
        // center on widescreen windows.
        add(buildMyColumn(), BorderLayout.WEST);

        // The board stretches to fill all remaining space (responsive).
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Theme.BG);
        center.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        center.add(boardPanel, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        add(buildHistoryPanel(), BorderLayout.EAST);
        add(buildSouthPanel(), BorderLayout.SOUTH);

        boardPanel.onClick(model::boardClicked);
        // ESC clears the card/piece selection.
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSelection");
        getActionMap().put("clearSelection", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                model.clearSelection();
                refresh();
            }
        });

        model.addListener(new ClientModelListener() {
            @Override
            public void onScreenChanged(Screen screen) {
                if (screen == Screen.GAME) {
                    refresh();
                }
            }

            @Override
            public void onMatchChanged() {
                refresh();
            }

            @Override
            public void onGameOver(GameOver over) {
                showGameOverDialog(over);
            }

            @Override
            public void onOpponentLeft(int graceSeconds) {
                turnLabel.setText("OPPONENT LOST CONNECTION");
            }

            @Override
            public void onRematchOffered() {
                turnLabel.setText("OPPONENT WANTS A REMATCH!");
            }
        });
    }

    /** The player profile panel: avatar, name, ELO/record, online dot (§16). */
    private JPanel buildPlayerBadge() {
        JPanel badge = new JPanel(new BorderLayout(8, 2));
        badge.setBackground(Theme.SURFACE);
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.OUTLINE, 2),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        badge.add(avatar, BorderLayout.WEST);
        JPanel text = new JPanel(new GridLayout(2, 1, 0, -2));
        text.setBackground(Theme.SURFACE);
        text.add(nameLabel);
        text.add(statsLabel);
        badge.add(text, BorderLayout.CENTER);
        badge.add(onlineDot, BorderLayout.EAST);
        return badge;
    }

    private JPanel buildHistoryPanel() {
        JPanel panel = UiKit.surface(10);
        panel.setLayout(new BorderLayout(6, 6));
        panel.setPreferredSize(new java.awt.Dimension(262, 120));

        JLabel title = UiKit.boldLabel("MOVES", 13f);
        panel.add(title, BorderLayout.NORTH);

        historyList.setBackground(Theme.SURFACE);
        historyList.setForeground(Theme.CREAM);
        historyList.setCellRenderer(new MoveRowRenderer());
        historyList.setFixedCellHeight(30);
        JScrollPane scroll = new JScrollPane(historyList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scroll, BorderLayout.CENTER);

        // Capture trays: tiny figurines instead of words.
        JPanel trays = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        trays.setBackground(Theme.SURFACE);
        trays.add(UiKit.label("TOOK", 10f));
        trays.add(myCapturesTray);
        JPanel separator = new JPanel() { };
        separator.setBackground(Theme.SURFACE);
        separator.setPreferredSize(new java.awt.Dimension(2, 18));
        trays.add(separator);
        trays.add(UiKit.label("LOST", 10f));
        trays.add(enemyCapturesTray);
        panel.add(trays, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Compact move rows (§17): "01 TIGER C3 → D4", the latest move
     * highlighted, and a friendly illustrated empty state.
     */
    private final class MoveRowRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean selected, boolean focused) {
            ClientModel.MoveInfo row = (ClientModel.MoveInfo) value;
            JLabel label = (JLabel) super.getListCellRendererComponent(list,
                    row.cardName(), index, selected, focused);
            setFont(Theme.normal(12f));
            setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            if (row.pass() && row.number() == 0) {
                setText(row.cardName());
                setForeground(Theme.MUTED);
                setBackground(Theme.SURFACE);
                setIcon(placeholderIcon());
                setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
                return this;
            }
            String text = String.format("%02d  %s  %s → %s%s",
                    row.number(), row.cardName().toUpperCase(),
                    row.fromSquare(), row.toSquare(),
                    row.capture() ? "  ✕" : "");
            boolean latest = index == list.getModel().getSize() - 1;
            setText(text);
            setForeground(latest ? Theme.INK : Theme.CREAM);
            setBackground(latest ? Theme.AMBER : Theme.SURFACE);
            setOpaque(true);
            setIcon(null);
            return this;
        }

        private javax.swing.Icon placeholderIcon() {
            var image = new java.awt.image.BufferedImage(22, 22,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            AnimalIcon.paint((Graphics2D) image.getGraphics(), "frog", 0, 0, 22,
                    Theme.MUTED, Theme.INK, Theme.CREAM);
            return new javax.swing.ImageIcon(image);
        }
    }

    /** The left column: your name, your two hand cards, the transit card. */
    private JPanel buildMyColumn() {
        myColumn = UiKit.surface(12);
        myColumn.setLayout(new BorderLayout(8, 8));
        myColumn.setPreferredSize(new java.awt.Dimension(230, 100));

        myColumn.add(buildPlayerBadge(), BorderLayout.NORTH);

        myCards.setLayout(new GridLayout(2, 1, 8, 8));
        myCards.setBackground(Theme.SURFACE);
        myCards.setOpaque(true);
        myColumn.add(myCards, BorderLayout.CENTER);

        JPanel transitRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        transitRow.setBackground(Theme.SURFACE);
        transitLabel.setFont(Theme.bold(12f));
        transitRow.add(transitLabel);
        myColumn.add(transitRow, BorderLayout.SOUTH);
        return myColumn;
    }

    private JPanel buildSouthPanel() {
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setBackground(Theme.BG);

        JPanel bannerRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bannerRow.setBackground(Theme.BG);
        bannerRow.add(turnLabel);
        south.add(bannerRow, BorderLayout.CENTER);

        JPanel resignRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        resignRow.setBackground(Theme.BG);
        JButton resign = UiKit.pill("Resign", UiKit.Pill.DANGER);
        resign.addActionListener(event -> confirmResign());
        resignRow.add(resign);
        south.add(resignRow, BorderLayout.EAST);
        return south;
    }

    /** Redraws everything from the model (called on every match change). */
    private void refresh() {
        GameState state = model.state();
        if (state == null) {
            return;
        }
        opponentLabel.setText("vs  " + model.opponentName().toUpperCase());
        opponentLabel.setForeground(Theme.playerColor(model.myColor().opponent()));
        nameLabel.setText(model.me() == null ? "" : model.me().username().toUpperCase());
        nameLabel.setForeground(Theme.playerColor(model.myColor()));
        if (model.me() != null) {
            statsLabel.setText("ELO " + model.me().elo() + "   ·   "
                    + model.me().wins() + "W · " + model.me().losses() + "L");
        }
        avatar.repaint();
        // Player identity: your column carries your color's top border.
        myColumn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(3, 0, 0, 0,
                        Theme.playerColor(model.myColor())),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        opponentCards.removeAll();
        state.hand(model.myColor().opponent()).forEach(card -> {
            // Opponent patterns are drawn from MY perspective (pre-rotated)
            // and dimmed so your own cards stand out.
            opponentCards.add(new CardPanel(card, model.myColor().opponent(), true, null));
        });

        myCards.removeAll();
        state.hand(model.myColor()).forEach(card -> {
            CardPanel panel = new CardPanel(card, model.myColor(), false,
                    () -> model.cardClicked(card.id()));
            panel.setCardScale(0.72);
            panel.setSelected(card.id().equals(model.selectedCardId()));
            JPanel slot = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            slot.setBackground(Theme.SURFACE);
            slot.add(panel);
            myCards.add(slot);
        });

        transitLabel.setText("transit: " + state.transit().name().toUpperCase());
        turnLabel.setText(model.statusLine().toUpperCase());
        turnLabel.setAccent(Theme.playerColor(state.turn()));
        java.util.List<ClientModel.MoveInfo> moves = model.historyMoves();
        historyList.setListData((moves.isEmpty()
                ? new ClientModel.MoveInfo[]{ClientModel.MoveInfo.PLACEHOLDER}
                : moves.toArray(new ClientModel.MoveInfo[0])));
        myCapturesTray.setPieces(model.myCaptures());
        enemyCapturesTray.setPieces(model.enemyCaptures());

        boardPanel.setView(state, model.myColor());
        boardPanel.setSelection(model.selectedSquare(), model.highlightedTargets());
        boardPanel.setLastMove(state.lastMove());
        if (state.moveNumber() != lastEffectMove) {
            lastEffectMove = state.moveNumber();
            if (model.lastCaptureSquare() != null) {
                boardPanel.playCaptureEffect(model.lastCaptureSquare(),
                        state.turn().opponent());
            }
        }
        revalidate();
        repaint();
    }

    private void confirmResign() {
        int answer = JOptionPane.showConfirmDialog(this,
                "Resign this match?", "Resign", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            model.resign();
        }
    }

    private void showGameOverDialog(GameOver over) {
        if (model.gameOverAnnounced()) {
            return;
        }
        model.markGameOverAnnounced();
        refresh();
        String result;
        if (over.winnerColor() == null) {
            result = "Draw — the move limit was reached.";
        } else if (over.winnerColor() == model.myColor()) {
            result = "You win by " + over.way() + "!";
        } else {
            result = model.opponentName() + " wins by " + over.way() + ".";
        }
        String ratings = "New Elo — you: " + myEloAfter(over) + ", opponent: "
                + opponentEloAfter(over);
        Object[] options = {"OFFER REMATCH", "BACK TO LOBBY"};
        int choice = JOptionPane.showOptionDialog(this, result + "\n" + ratings,
                "Game over", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);
        if (choice == 0) {
            model.requestRematch();
        } else {
            model.leaveToLobby();
        }
    }

    private int myEloAfter(GameOver over) {
        return model.myColor() == onitama.core.PlayerColor.BLUE
                ? over.blueEloAfter() : over.redEloAfter();
    }

    private int opponentEloAfter(GameOver over) {
        return model.myColor() == onitama.core.PlayerColor.BLUE
                ? over.redEloAfter() : over.blueEloAfter();
    }
}

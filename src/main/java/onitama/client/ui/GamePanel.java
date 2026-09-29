package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;
import onitama.core.Board;
import onitama.core.GameState;
import onitama.core.Piece;
import onitama.net.GameOver;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

/**
 * The game screen, composed like the physical tabletop (§18): the opponent's
 * cards on top with their identity chip, the big board in the center, the
 * player panel on the left (turn state, stats, contextual help, controls and
 * the compact move list), the NEXT CARD panel on the right, and your hand
 * with your identity chip at the bottom. Click a card and a piece (either
 * order) to see legal destinations, then click a destination to move. ESC
 * clears the selection.
 */
public final class GamePanel extends JPanel {

    private final ClientModel model;

    private final BoardPanel boardPanel = new BoardPanel();
    private final UiKit.HeadingLabel turnLabel = new UiKit.HeadingLabel(15f);
    private final JLabel opponentLabel = UiKit.label("", 14f);
    private final JLabel nameLabel = UiKit.boldLabel("", 14f);
    private final JLabel statsLabel = UiKit.label("", 10f);
    private final JLabel helpLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel turnCountLabel = new JLabel("00", SwingConstants.CENTER);
    private final JLabel turnCaption = new JLabel("TURN", SwingConstants.CENTER);
    private final JLabel redPiecesLabel = new JLabel("5", SwingConstants.CENTER);
    private final JLabel bluePiecesLabel = new JLabel("5", SwingConstants.CENTER);
    private final JLabel transitGoesLabel = UiKit.label(" ", 11f);
    private final JPanel opponentCards = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
    private final JPanel myCards = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
    private final JPanel transitHolder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
    private final JList<ClientModel.MoveInfo> historyList = new JList<>();
    private final PieceTray myCapturesTray = new PieceTray("—");
    private final PieceTray enemyCapturesTray = new PieceTray("—");
    private final JComponent opponentDot = chipDot();
    private final JLabel opponentChipLabel = UiKit.inkLabel("OPPONENT", 11f);
    private final JComponent myDot = chipDot();
    private final JLabel myChipLabel = UiKit.inkLabel("YOU", 11f);
    private JPanel myColumn;
    private JPanel historyViews;
    private int lastEffectMove = -1;

    /** The avatar: a mini Master figurine in the player's color. */
    private final JComponent avatar = new JComponent() {
        {
            setPreferredSize(new Dimension(40, 44));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            var color = myColor();
            UiKit.drawFigurine(g, getWidth() / 2, getHeight() - 6,
                    getHeight() - 12,
                    color == null ? Theme.AMBER : Theme.playerColor(color), true);
            g.dispose();
        }
    };

    /** The online indicator dot. */
    private final JComponent onlineDot = new JComponent() {
        {
            setPreferredSize(new Dimension(10, 10));
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

    /** A small cream chip with a colored dot, used for player identity. */
    private static JComponent chipDot() {
        return new JComponent() {
            {
                setPreferredSize(new Dimension(14, 14));
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getForeground());
                g.fillOval(1, 1, 12, 12);
                g.setColor(Theme.INK);
                g.drawOval(1, 1, 12, 12);
                g.dispose();
            }
        };
    }

    /** Builds the game screen and subscribes it to the model. */
    public GamePanel(ClientModel model) {
        this.model = model;
        setLayout(new BorderLayout(10, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        setBackground(Theme.BG);

        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(Theme.BG);
        opponentLabel.setHorizontalAlignment(SwingConstants.CENTER);
        opponentLabel.setFont(Theme.bold(13f));
        opponentCards.setBackground(Theme.BG);
        north.add(opponentLabel, BorderLayout.NORTH);
        north.add(opponentCards, BorderLayout.CENTER);
        JPanel opponentChipRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        opponentChipRow.setBackground(Theme.BG);
        opponentChipRow.add(opponentDot);
        opponentChipRow.add(opponentChipLabel);
        north.add(opponentChipRow, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        // The board stretches to fill all remaining space (responsive).
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Theme.BG);
        center.add(boardPanel, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        add(buildPlayerPanel(), BorderLayout.WEST);
        add(buildNextCardPanel(), BorderLayout.EAST);
        add(buildBottom(), BorderLayout.SOUTH);

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

    // ------------------------------------------------------------------
    // Left: player panel
    // ------------------------------------------------------------------

    private JPanel buildPlayerPanel() {
        JPanel panel = UiKit.sticker(14);
        panel.setLayout(new BorderLayout(8, 8));
        panel.setPreferredSize(new Dimension(285, 100));

        JPanel bannerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        bannerRow.setOpaque(false);
        bannerRow.add(turnLabel);
        panel.add(bannerRow, BorderLayout.NORTH);

        // Middle stack: stats + help + moves.
        JPanel middle = new JPanel(new BorderLayout(6, 6));
        middle.setOpaque(false);
        middle.add(buildPlayerBadge(), BorderLayout.NORTH);

        // Dark stats block: turn counter and both piece counts.
        JPanel stats = new JPanel(new GridLayout(1, 2, 8, 4));
        stats.setBackground(Theme.INK);
        stats.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JPanel turnBox = new JPanel(new GridLayout(2, 1, 0, 0));
        turnBox.setBackground(Theme.INK);
        turnCountLabel.setForeground(Theme.AMBER);
        turnCountLabel.setFont(Theme.display(18f));
        turnCaption.setFont(Theme.normal(10f));
        turnCaption.setForeground(Theme.CREAM);
        turnBox.add(turnCountLabel);
        turnBox.add(turnCaption);
        JPanel piecesBox = new JPanel(new GridLayout(2, 1, 0, 0));
        piecesBox.setBackground(Theme.INK);
        redPiecesLabel.setForeground(Theme.P2);
        redPiecesLabel.setFont(Theme.display(15f));
        bluePiecesLabel.setForeground(Theme.P1);
        bluePiecesLabel.setFont(Theme.display(15f));
        JPanel redRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        redRow.setBackground(Theme.INK);
        redRow.add(redPiecesLabel);
        redRow.add(smallCaption("RED PIECES", Theme.P2));
        JPanel blueRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        blueRow.setBackground(Theme.INK);
        blueRow.add(bluePiecesLabel);
        blueRow.add(smallCaption("BLUE PIECES", Theme.P1));
        piecesBox.add(redRow);
        piecesBox.add(blueRow);
        stats.add(turnBox);
        stats.add(piecesBox);

        JPanel stack = new JPanel(new BorderLayout(6, 6));
        stack.setOpaque(false);
        stack.add(stats, BorderLayout.NORTH);
        helpLabel.setFont(Theme.normal(11.5f));
        helpLabel.setForeground(Theme.INK);
        helpLabel.setVerticalAlignment(SwingConstants.TOP);
        stack.add(helpLabel, BorderLayout.CENTER);

        // Compact move history with a themed empty state (§30).
        historyList.setBackground(Theme.SURFACE);
        historyList.setForeground(Theme.CREAM);
        historyList.setCellRenderer(new MoveRowRenderer());
        historyList.setFixedCellHeight(26);
        JScrollPane scroll = new JScrollPane(historyList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(240, 150));

        JPanel emptyState = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        emptyState.setBackground(Theme.SURFACE);
        JLabel emptyLabel = UiKit.label("Waiting for the first move…", 11f);
        emptyLabel.setForeground(Theme.MUTED);
        var emptyIcon = new java.awt.image.BufferedImage(24, 24,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        AnimalIcon.paint((Graphics2D) emptyIcon.getGraphics(), "frog", 0, 0, 24,
                Theme.MUTED, Theme.INK, Theme.CREAM);
        emptyLabel.setIcon(new javax.swing.ImageIcon(emptyIcon));
        emptyState.add(emptyLabel);

        historyViews = new JPanel(new java.awt.CardLayout());
        historyViews.setOpaque(false);
        historyViews.add(scroll, "moves");
        historyViews.add(emptyState, "empty");
        stack.add(historyViews, BorderLayout.SOUTH);
        middle.add(stack, BorderLayout.CENTER);
        panel.add(middle, BorderLayout.CENTER);

        // Controls.
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        controls.setOpaque(false);
        JButton menu = UiKit.pill("Menu", UiKit.Pill.CREAM_OUTLINE);
        menu.addActionListener(event -> model.leaveToLobby());
        controls.add(menu);
        JButton resign = UiKit.pill("Resign", UiKit.Pill.DANGER);
        resign.addActionListener(event -> confirmResign());
        controls.add(resign);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private onitama.core.PlayerColor myColor() {
        return model.myColor();
    }

    /** Swaps between the move list and the themed empty state. */
    private void showHistory(boolean hasMoves) {
        ((java.awt.CardLayout) historyViews.getLayout()).show(historyViews,
                hasMoves ? "moves" : "empty");
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

    /** A dark caption block: big value over a small label. */
    private JPanel statBox(JComponent value, JLabel caption, Color valueColor) {
        JPanel box = new JPanel(new GridLayout(2, 1, 0, 0));
        box.setBackground(Theme.INK);
        value.setForeground(valueColor);
        value.setFont(Theme.display(18f));
        caption.setFont(Theme.normal(10f));
        caption.setForeground(Theme.CREAM);
        box.add(value);
        box.add(caption);
        return box;
    }

    private JLabel smallCaption(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.normal(9f));
        label.setForeground(color);
        return label;
    }

    // ------------------------------------------------------------------
    // Right: next-card panel
    // ------------------------------------------------------------------

    private JPanel buildNextCardPanel() {
        JPanel panel = UiKit.sticker(14);
        panel.setLayout(new BorderLayout(8, 8));
        panel.setPreferredSize(new Dimension(240, 120));

        JPanel nextHeader = new JPanel(new GridLayout(2, 1, 0, 0));
        nextHeader.setBackground(Theme.CREAM);
        nextHeader.add(UiKit.inkLabel("NEXT CARD", 15f));
        transitGoesLabel.setForeground(Theme.INK);
        nextHeader.add(transitGoesLabel);
        panel.add(nextHeader, BorderLayout.NORTH);
        JPanel transitCenter = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        transitCenter.setOpaque(false);
        transitCenter.add(transitHolder);
        panel.add(transitCenter, BorderLayout.CENTER);
        return panel;
    }

    // ------------------------------------------------------------------
    // Bottom: my hand + identity chip
    // ------------------------------------------------------------------

    private JPanel buildBottom() {
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setBackground(Theme.BG);

        south.add(myCards, BorderLayout.CENTER);
        JPanel chipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 10));
        chipRow.setBackground(Theme.BG);
        chipRow.add(myDot);
        chipRow.add(myChipLabel);
        south.add(chipRow, BorderLayout.EAST);
        return south;
    }

    // ------------------------------------------------------------------
    // Refresh (every model change)
    // ------------------------------------------------------------------

    /** Redraws everything from the model (called on every match change). */
    private void refresh() {
        GameState state = model.state();
        if (state == null) {
            return;
        }
        var myColor = model.myColor();
        var opponent = myColor.opponent();

        opponentLabel.setText("VS  " + model.opponentName().toUpperCase());
        opponentLabel.setForeground(Theme.playerColor(opponent));
        opponentDot.setForeground(Theme.playerColor(opponent));
        opponentChipLabel.setText(opponent.name().toUpperCase() + "  ·  OPPONENT");
        myDot.setForeground(Theme.playerColor(myColor));
        myChipLabel.setText(myColor.name().toUpperCase() + "  ·  YOU");

        nameLabel.setText(model.me() == null ? "" : model.me().username().toUpperCase());
        nameLabel.setForeground(Theme.playerColor(myColor));
        if (model.me() != null) {
            statsLabel.setText("ELO " + model.me().elo() + "   ·   "
                    + model.me().wins() + "W · " + model.me().losses() + "L");
        }
        avatar.repaint();

        // Opponent cards: their color variant, dimmed, my perspective.
        opponentCards.removeAll();
        state.hand(opponent).forEach(card -> {
            CardPanel oppCard = new CardPanel(card, opponent, true, null);
            oppCard.setCardScale(0.50);
            opponentCards.add(oppCard);
        });

        // My cards: my color variant, hoverable, scaled for the bottom row.
        myCards.removeAll();
        state.hand(myColor).forEach(card -> {
            CardPanel panel = new CardPanel(card, myColor, false,
                    () -> model.cardClicked(card.id()));
            panel.setCardScale(0.50);
            panel.setSelected(card.id().equals(model.selectedCardId()));
            JPanel slot = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            slot.setBackground(Theme.BG);
            slot.add(panel);
            myCards.add(slot);
        });

        // NEXT CARD panel: the transit card, headed for the current player.
        transitHolder.removeAll();
        CardPanel transit = new CardPanel(state.transit(), state.turn(), true, null);
        transit.setCardScale(0.55);
        transitHolder.add(transit);
        transitGoesLabel.setText("GOES TO " + state.turn().name().toUpperCase());

        turnLabel.setText(bannerText(state, myColor));
        turnLabel.setAccent(Theme.playerColor(state.turn()));
        turnCountLabel.setText(String.format("%02d", state.moveNumber()));
        int redCount = 0;
        int blueCount = 0;
        for (int y = 0; y < Board.SIZE; y++) {
            for (int x = 0; x < Board.SIZE; x++) {
                Piece piece = state.board().pieceAt(x, y);
                if (piece != null) {
                    if (piece.color() == onitama.core.PlayerColor.BLUE) {
                        blueCount++;
                    } else {
                        redCount++;
                    }
                }
            }
        }
        redPiecesLabel.setText(String.valueOf(redCount));
        bluePiecesLabel.setText(String.valueOf(blueCount));
        helpLabel.setText(helpText(state, myColor));

        List<ClientModel.MoveInfo> moves = model.historyMoves();
        historyList.setListData(moves.toArray(new ClientModel.MoveInfo[0]));
        showHistory(!moves.isEmpty());

        boardPanel.setView(state, myColor);
        boardPanel.setSelection(model.selectedSquare(), model.highlightedTargets());
        boardPanel.setLastMove(state.lastMove());
        if (state.moveNumber() != lastEffectMove) {
            lastEffectMove = state.moveNumber();
            if (model.lastCaptureSquare() != null) {
                boardPanel.playCaptureEffect(model.lastCaptureSquare(),
                        state.turn().opponent());
            }
            if (state.lastMove() != null) {
                Piece moved = state.board().pieceAt(state.lastMove().to());
                if (moved != null) {
                    boardPanel.animateMove(state.lastMove(), moved.color(), moved.master());
                }
            }
        }
        revalidate();
        repaint();
    }

    /** Short banner text: the game state in two words. */
    private String bannerText(GameState state, onitama.core.PlayerColor myColor) {
        if (!state.isOngoing()) {
            return "GAME OVER";
        }
        if (state.turn() == myColor) {
            return "YOUR TURN";
        }
        return "WAITING FOR " + model.opponentName().toUpperCase() + "…";
    }

    /** Contextual help line for the left panel (§13/§34 feedback). */
    private String helpText(GameState state, onitama.core.PlayerColor myColor) {
        if (!state.isOngoing()) {
            return "The game is over.";
        }
        if (state.turn() != myColor) {
            return "Waiting for " + model.opponentName() + "…";
        }
        if (model.selectedSquare() != null && model.selectedCardId() != null) {
            Piece piece = state.board().pieceAt(model.selectedSquare());
            String kind = piece != null && piece.master() ? "Master" : "Student";
            return kind + " selected. Dots mark open squares; a ring marks a capture.";
        }
        if (model.selectedCardId() != null) {
            String cardName = onitama.core.CardDeck
                    .cardById(model.selectedCardId()).name();
            return cardName + " selected — now pick a piece.";
        }
        return "Pick a card, then a piece. Dots mark where it can go.";
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
}

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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
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
 * The game screen, composed like the physical tabletop: your hand cards big
 * on the left, the turn banner + player badge + stats + NEXT CARD + controls
 * + move history stacked on the right, the opponent's cards on top with
 * their identity chip, and the big board in the center. Click a card and a
 * piece (either order) to see legal destinations, then click a destination
 * to move. ESC clears the selection.
 */
public final class GamePanel extends JPanel {

    private final ClientModel model;

    private final BoardPanel boardPanel = new BoardPanel();
    private final UiKit.HeadingLabel turnLabel = new UiKit.HeadingLabel(15f);
    private final JLabel opponentLabel = UiKit.label("", 14f);
    private final JLabel nameLabel = UiKit.boldLabel("", 14f);
    private final JLabel statsLabel = UiKit.label("", 10f);
    private final JLabel turnCountLabel = new JLabel("00", SwingConstants.CENTER);
    private final JLabel turnCaption = new JLabel("TURN", SwingConstants.CENTER);
    private final JLabel redPiecesLabel = new JLabel("5", SwingConstants.CENTER);
    private final JLabel bluePiecesLabel = new JLabel("5", SwingConstants.CENTER);
    private final JLabel transitGoesLabel = UiKit.inkLabel(" ", 11f);
    private final JPanel opponentCards = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
    private final JPanel myCards = new JPanel(new GridLayout(2, 1, 12, 12));
    private final JPanel transitHolder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
    private final JList<ClientModel.MoveInfo> historyList = new JList<>();
    private final JComponent opponentDot = chipDot();
    private final JLabel opponentChipLabel = UiKit.inkLabel("OPPONENT", 11f);
    private final JComponent myDot = chipDot();
    private final JLabel myChipLabel = UiKit.inkLabel("YOU", 11f);
    private JPanel historyViews;
    private int lastEffectMove = -1;
    private final List<CardPanel> myCardPanels = new java.util.ArrayList<>();
    /** The hand card ids the current {@link #myCardPanels} were built for. */
    private List<String> builtHandIds = List.of();
    /** Last scale applied to the hand cards (reused when they are rebuilt). */
    private double handScale = 0.9;
    private JPanel handColumn;
    /** A hand card's native height at scale 1 (CardPanel H + shadow room). */
    private static final double CARD_NATIVE_H = 244;

    private onitama.core.PlayerColor myColor() {
        return model.myColor();
    }

    /** Swaps between the move list and the themed empty state. */
    private void showHistory(boolean hasMoves) {
        ((java.awt.CardLayout) historyViews.getLayout()).show(historyViews,
                hasMoves ? "moves" : "empty");
    }

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

    /** The sumi-e painting behind the match (same artwork as the lobby). */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        java.awt.Image background = AssetStore.optional("menu-background.png");
        if (background == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        double scale = Math.max(getWidth() / (double) background.getWidth(null),
                getHeight() / (double) background.getHeight(null));
        int w = (int) (background.getWidth(null) * scale);
        int h = (int) (background.getHeight(null) * scale);
        g.drawImage(background, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
        g.dispose();
    }

    /** Builds the game screen and subscribes it to the model. */
    public GamePanel(ClientModel model) {
        this.model = model;
        setLayout(new BorderLayout(10, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        setBackground(Theme.BG);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        opponentLabel.setHorizontalAlignment(SwingConstants.CENTER);
        opponentLabel.setFont(Theme.bold(13f));
        opponentCards.setOpaque(false);
        north.add(opponentLabel, BorderLayout.NORTH);
        north.add(opponentCards, BorderLayout.CENTER);
        JPanel opponentChipRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        opponentChipRow.setBackground(Theme.CREAM);
        opponentChipRow.setBorder(BorderFactory.createLineBorder(Theme.INK, 2));
        opponentDot.setForeground(Theme.P2);
        opponentChipRow.add(opponentDot);
        opponentChipLabel.setForeground(Theme.INK);
        opponentChipRow.add(opponentChipLabel);
        JPanel opponentChipWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        opponentChipWrap.setOpaque(false);
        opponentChipWrap.add(opponentChipRow);
        north.add(opponentChipWrap, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        // The board stretches to fill all remaining space (responsive).
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(boardPanel, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        add(buildHandColumn(), BorderLayout.WEST);
        add(buildRightColumn(), BorderLayout.EAST);

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
    // Left: your hand cards, big
    // ------------------------------------------------------------------

    private JPanel buildHandColumn() {
        handColumn = new JPanel(new BorderLayout());
        handColumn.setOpaque(false);
        handColumn.setPreferredSize(new Dimension(230, 100));
        myCards.setOpaque(false);
        handColumn.add(myCards, BorderLayout.NORTH);
        // Cards rescale when the window height changes: two cards always
        // fit the column exactly, so nothing clips and no space is wasted.
        handColumn.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                fitHandCards();
            }
        });
        return handColumn;
    }

    /** Rebuilds the two hand-card panels from scratch (only on card swaps). */
    private void rebuildMyHand(java.util.List<onitama.core.Card> hand) {
        myCards.removeAll();
        myCardPanels.clear();
        builtHandIds = hand.stream().map(onitama.core.Card::id).toList();
        hand.forEach(card -> {
            CardPanel panel = new CardPanel(card, model.myColor(), false,
                    () -> model.cardClicked(card.id()));
            panel.setCardScale(handScale);
            myCardPanels.add(panel);
            JPanel slot = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            slot.setOpaque(false);
            slot.add(panel);
            myCards.add(slot);
        });
        fitHandCards();
    }

    /** Scales the two hand cards so the pair fills the column height. */
    private void fitHandCards() {
        int available = handColumn.getHeight();
        if (available <= 0) {
            return;
        }
        double perCard = (available - 12) / 2.0;
        handScale = Math.max(0.30, Math.min(0.85, perCard / CARD_NATIVE_H));
        for (CardPanel panel : myCardPanels) {
            panel.setCardScale(handScale);
        }
        handColumn.revalidate();
    }

    // ------------------------------------------------------------------
    // Right: banner + badge + stats + help + next card + controls + moves
    // ------------------------------------------------------------------

    private java.awt.Component buildRightColumn() {
        // Fixed header column (banner, badge, stats, next card, controls) +
        // a moves list that scrolls internally — nothing scrolls out of view.
        JPanel column = new JPanel(new BorderLayout(8, 8));
        column.setOpaque(false);
        column.setPreferredSize(new Dimension(255, 100));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        // Turn banner.
        JPanel bannerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bannerRow.setOpaque(false);
        bannerRow.add(turnLabel);
        top.add(bannerRow);

        // Player badge.
        JPanel badge = buildPlayerBadge();
        badge.setAlignmentX(Component.CENTER_ALIGNMENT);
        top.add(badge);

        // Stats block: turn counter and both piece counts.
        JPanel stats = new JPanel(new GridLayout(1, 2, 8, 4));
        stats.setBackground(Theme.INK);
        stats.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        stats.setAlignmentX(Component.CENTER_ALIGNMENT);
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
        top.add(stats);

        // NEXT CARD: the transit card floats centered in the cream panel.
        JPanel nextCard = UiKit.sticker(12);
        nextCard.setLayout(new BorderLayout(8, 8));
        JPanel nextHeader = new JPanel(new BorderLayout());
        nextHeader.setOpaque(false);
        nextHeader.add(UiKit.inkLabel("NEXT CARD", 14f), BorderLayout.WEST);
        nextHeader.add(transitGoesLabel, BorderLayout.EAST);
        nextCard.add(nextHeader, BorderLayout.NORTH);
        JPanel transitCenter = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        transitCenter.setOpaque(false);
        transitCenter.add(transitHolder);
        nextCard.add(transitCenter, BorderLayout.CENTER);
        top.add(nextCard);

        // Controls: equal-width pair directly under the card panel.
        JPanel controls = new JPanel(new GridLayout(1, 2, 10, 0));
        controls.setOpaque(false);
        JButton menu = UiKit.pill("Menu", UiKit.Pill.CREAM_OUTLINE);
        menu.addActionListener(event -> model.leaveToLobby());
        controls.add(menu);
        JButton resign = UiKit.pill("Resign", UiKit.Pill.DANGER);
        resign.addActionListener(event -> confirmResign());
        controls.add(resign);
        top.add(controls);

        column.add(top, BorderLayout.NORTH);

        // MOVES fills the remaining sidebar height; the list scrolls
        // internally so the header elements never move.
        JPanel movesCard = UiKit.surface(10);
        movesCard.setLayout(new BorderLayout(6, 6));
        movesCard.add(UiKit.boldLabel("MOVES", 13f), BorderLayout.NORTH);
        historyList.setBackground(Theme.SURFACE);
        historyList.setForeground(Theme.CREAM);
        historyList.setCellRenderer(new MoveRowRenderer());
        historyList.setFixedCellHeight(26);
        JScrollPane scroll = new JScrollPane(historyList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        historyViews = new JPanel(new java.awt.CardLayout());
        historyViews.setOpaque(false);
        historyViews.add(scroll, "moves");

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
        historyViews.add(emptyState, "empty");
        movesCard.add(historyViews, BorderLayout.CENTER);

        column.add(movesCard, BorderLayout.CENTER);
        return column;
    }



    // ------------------------------------------------------------------
    // Right-column parts
    // ------------------------------------------------------------------

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

    private JLabel smallCaption(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.normal(9f));
        label.setForeground(color);
        return label;
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
            opponentCards.add(new CardPanel(card, opponent, true, null));
        });

        // My cards: rebuilt only when the hand actually changed (cards swap
        // after a half-move); otherwise the existing panels are updated in
        // place, so rapid clicking never races a full re-layout that could
        // swallow clicks between press and release.
        List<String> handIds = state.hand(myColor).stream()
                .map(onitama.core.Card::id).toList();
        if (!handIds.equals(builtHandIds)) {
            rebuildMyHand(state.hand(myColor));
        }
        for (int i = 0; i < myCardPanels.size(); i++) {
            myCardPanels.get(i).setSelected(
                    state.hand(myColor).get(i).id().equals(model.selectedCardId()));
        }

        // NEXT CARD panel: the transit card, headed for the current player.
        transitHolder.removeAll();
        CardPanel transit = new CardPanel(state.transit(), state.turn(), true, null);
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
        return "WAITING…";
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
        if (model.isPracticeMode()) {
            // Practice has no ratings: offer an instant rematch instead.
            Object[] options = {"PLAY AGAIN", "BACK TO LOBBY"};
            int choice = JOptionPane.showOptionDialog(this,
                    result + "\nThe dojo bot thanks you for the practice.",
                    "Practice game over", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
            if (choice == 0) {
                model.startPracticeMatch(model.practiceDifficulty());
            } else {
                model.leaveToLobby();
            }
            return;
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

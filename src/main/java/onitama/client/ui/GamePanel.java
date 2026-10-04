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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
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
import javax.swing.SwingUtilities;

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
    private final JLabel turnCaption = UiKit.caption("TURN", Theme.CREAM);
    private final PieceCountRow redPips = new PieceCountRow(Theme.P2);
    private final PieceCountRow bluePips = new PieceCountRow(Theme.P1);
    private final JPanel opponentBadge = UiKit.badge("OPPONENT", () -> {
        var opponent = opponentColor();
        return opponent == null ? Theme.AMBER : Theme.playerColor(opponent);
    });
    private final JPanel myBadge = UiKit.badge("YOU", () -> {
        var mine = myColor();
        return mine == null ? Theme.AMBER : Theme.playerColor(mine);
    });
    private final JPanel goesBadge = UiKit.badge("GOES TO —", this::turnDotColor);
    private final JPanel opponentCards =
            new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
    private final JPanel myCards = new JPanel(new GridLayout(2, 1, 12, 12));
    private final JPanel transitHolder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
    private final JList<ClientModel.MoveInfo> historyList = new JList<>();
    private final JLabel lastMoveLabel = UiKit.label("Last move: —", 11f);
    private final CaptureTray myCaptureTray = new CaptureTray();
    private final CaptureTray enemyCaptureTray = new CaptureTray();
    private final JComponent waitPulseDot = new JComponent() {
        {
            setPreferredSize(new Dimension(12, 12));
            setOpaque(false);
            setVisible(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(252, 186, 40, 90 + (int) (150 * pulse)));
            g.fillOval(1, 1, 10, 10);
            g.setColor(Theme.INK);
            g.drawOval(1, 1, 10, 10);
            g.dispose();
        }
    };
    private float pulse;
    private final javax.swing.Timer pulseTimer = new javax.swing.Timer(60, event -> {
        pulse = (pulse + 0.09f) % 1f;
        waitPulseDot.repaint();
    });
    private JPanel historyViews;
    private int lastEffectMove = -1;
    private JButton resignButton;
    /** True while a ResignRequest is on the wire and GameOver not yet back. */
    private boolean resignPending;
    private final List<CardPanel> myCardPanels = new java.util.ArrayList<>();
    /** The hand card ids the current {@link #myCardPanels} were built for. */
    private List<String> builtHandIds = List.of();
    /** Last scale applied to the hand cards (reused when they are rebuilt). */
    private double handScale = 0.9;
    private JPanel handColumn;
    /** A hand card's native height at scale 1 (CardPanel H + shadow room). */
    private static final double CARD_NATIVE_H = 244;
    /** Fixed scale for the opponent's two cards (≈168px wide, never rescales). */
    private static final double OPPONENT_CARD_SCALE = 0.78;
    /** Fixed scale for the transit card inside the NEXT CARD panel. */
    private static final double TRANSIT_CARD_SCALE = 0.62;
    /** The sidebar column's fixed width — all its cards align to it. */
    private static final int SIDEBAR_WIDTH = 264;

    private onitama.core.PlayerColor myColor() {
        return model.myColor();
    }

    private onitama.core.PlayerColor opponentColor() {
        return myColor() == null ? null : myColor().opponent();
    }

    /** Dot color for the GOES TO badge: the side the transit card is heading to. */
    private Color turnDotColor() {
        var turn = model.state() == null ? null : model.state().turn();
        return turn == null ? Theme.AMBER : Theme.playerColor(turn);
    }

    /** Swaps between the move list and the themed empty state. */
    private void showHistory(boolean hasMoves) {
        ((java.awt.CardLayout) historyViews.getLayout()).show(historyViews,
                hasMoves ? "moves" : "empty");
    }

    /** The avatar: a mini Master figurine in the player's color. */
    private final JComponent avatar = new JComponent() {
        {
            setPreferredSize(new Dimension(32, 36));
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

    /** The online indicator dot (top-right of the player badge). */
    private final JComponent onlineDot = UiKit.statusDot(9, () -> Theme.GREEN);

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
        setLayout(new BorderLayout(14, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        setBackground(Theme.BG);

        // Top band: the VS headline, the opponent's two cards side by side,
        // and their identity badge — all centered against the board.
        JPanel north = new JPanel(new BorderLayout(0, Theme.GRID));
        north.setOpaque(false);
        north.setBorder(BorderFactory.createEmptyBorder(Theme.GRID, 0, 0, 0));
        opponentLabel.setHorizontalAlignment(SwingConstants.CENTER);
        opponentLabel.setFont(Theme.display(17f));
        opponentLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        opponentCards.setOpaque(false);
        north.add(opponentLabel, BorderLayout.NORTH);
        north.add(opponentCards, BorderLayout.CENTER);
        JPanel opponentBadgeWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
        opponentBadgeWrap.setOpaque(false);
        opponentBadgeWrap.add(opponentBadge);
        north.add(opponentBadgeWrap, BorderLayout.SOUTH);

        // The board column: opponent band on top of the board only, so the
        // side columns (hand + sidebar) keep the full window height.
        JPanel center = new JPanel(new BorderLayout(0, Theme.GRID));
        center.setOpaque(false);
        center.add(north, BorderLayout.NORTH);
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
                } else {
                    // Off the game screen: drop selection highlights and the
                    // animations that only make sense over a live board.
                    boardPanel.clearHighlights();
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
        // A card that just left the hand (played into the transit slot) can
        // no longer be selected: drop the stale id so targets never compute
        // against a card the rules engine would reject.
        String selectedId = model.selectedCardId();
        if (selectedId != null && !builtHandIds.contains(selectedId)) {
            model.clearSelection();
        }
        hand.forEach(card -> {
            CardPanel panel = new CardPanel(card, model.myColor(), false,
                    () -> model.cardClicked(card.id()));
            panel.setCardScale(handScale);
            myCardPanels.add(panel);
            JPanel slot = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            slot.setOpaque(false);
            slot.add(panel);
            myCards.add(slot);
            panel.playDealPop();
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
        // The header stack is budgeted to ~470px so MOVES always stays
        // visible at the 720px minimum window height.
        JPanel column = new JPanel(new BorderLayout(0, Theme.GRID));
        column.setOpaque(false);
        column.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 100));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        // Turn banner with a pulsing dot while waiting for the opponent.
        JPanel bannerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bannerRow.setOpaque(false);
        bannerRow.add(turnLabel);
        bannerRow.add(waitPulseDot);
        top.add(bannerRow);
        top.add(Box.createVerticalStrut(10));

        // Player badge.
        JPanel badge = buildPlayerBadge();
        badge.setAlignmentX(Component.CENTER_ALIGNMENT);
        top.add(badge);
        top.add(Box.createVerticalStrut(10));

        // Stats block: turn counter and both piece counts, monospaced
        // numbers on one shared top baseline.
        JPanel stats = new JPanel(new GridLayout(1, 2, 10, 0));
        stats.setBackground(Theme.INK);
        stats.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        stats.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel turnBox = new JPanel(new GridLayout(2, 1, 0, 0));
        turnBox.setBackground(Theme.INK);
        turnCountLabel.setForeground(Theme.AMBER);
        turnCountLabel.setFont(Theme.mono(17f));
        turnBox.add(turnCountLabel);
        turnBox.add(turnCaption);
        JPanel piecesBox = new JPanel(new GridLayout(2, 1, 0, 0));
        piecesBox.setBackground(Theme.INK);
        // Piece counts as five pips per side: filled = alive, hollow = captured.
        JPanel redRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        redRow.setBackground(Theme.INK);
        redRow.add(redPips);
        redRow.add(UiKit.caption("RED PIECES", Theme.CREAM));
        JPanel blueRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        blueRow.setBackground(Theme.INK);
        blueRow.add(bluePips);
        blueRow.add(UiKit.caption("BLUE PIECES", Theme.CREAM));
        piecesBox.add(redRow);
        piecesBox.add(blueRow);
        stats.add(turnBox);
        stats.add(piecesBox);
        top.add(stats);
        top.add(Box.createVerticalStrut(10));

        // NEXT CARD: header row + the transit card, scaled to fit the panel.
        JPanel nextCard = UiKit.sticker(10);
        nextCard.setLayout(new BorderLayout(6, 6));
        JPanel nextHeader = new JPanel(new BorderLayout());
        nextHeader.setOpaque(false);
        nextHeader.add(UiKit.inkLabel("NEXT CARD", 13f), BorderLayout.WEST);
        nextHeader.add(goesBadge, BorderLayout.EAST);
        nextCard.add(nextHeader, BorderLayout.NORTH);
        JPanel transitCenter = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        transitCenter.setOpaque(false);
        transitCenter.add(transitHolder);
        nextCard.add(transitCenter, BorderLayout.CENTER);
        nextCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        top.add(nextCard);
        top.add(Box.createVerticalStrut(10));

        // Controls: equal-width pair directly under the card panel.
        JPanel controls = new JPanel(new GridLayout(1, 2, 10, 0));
        controls.setOpaque(false);
        controls.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton menu = UiKit.pill("Menu", UiKit.Pill.CREAM_OUTLINE);
        menu.addActionListener(event -> model.leaveToLobby());
        controls.add(menu);
        resignButton = UiKit.pill("Resign", UiKit.Pill.DANGER);
        resignButton.addActionListener(event -> confirmResign());
        controls.add(resignButton);
        top.add(controls);

        column.add(top, BorderLayout.NORTH);

        // MATCH INFO: captures, the last half-move and a compact move
        // history. A Y stack with a glue keeps every info row at its natural
        // height and pins a capped 80px history strip to the card bottom —
        // nothing scrolls the info rows out of view.
        JPanel infoCard = UiKit.surface(10);
        infoCard.setLayout(new BorderLayout(6, 8));
        infoCard.setMinimumSize(new Dimension(0, 150));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(cappedRow(leftRow(UiKit.boldLabel("MATCH INFO", 12f))));
        info.add(Box.createVerticalStrut(8));
        info.add(cappedRow(infoRow("CAPTURED BY YOU", myCaptureTray)));
        info.add(Box.createVerticalStrut(4));
        info.add(cappedRow(infoRow("CAPTURED BY OPPONENT", enemyCaptureTray)));
        info.add(Box.createVerticalStrut(6));
        lastMoveLabel.setHorizontalAlignment(SwingConstants.LEFT);
        info.add(cappedRow(lastMoveLabel));
        info.add(Box.createVerticalGlue());
        info.add(cappedRow(leftRow(UiKit.boldLabel("MOVES", 11f))));
        info.add(Box.createVerticalStrut(4));

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

        JPanel historyHolder = new JPanel(new BorderLayout());
        historyHolder.setOpaque(false);
        historyHolder.add(historyViews, BorderLayout.CENTER);
        historyHolder.setPreferredSize(new Dimension(0, 80));
        historyHolder.setMinimumSize(new Dimension(0, 60));
        historyHolder.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        info.add(historyHolder);

        infoCard.add(info, BorderLayout.CENTER);
        column.add(infoCard, BorderLayout.CENTER);
        return column;
    }

    /** A left-aligned row inside the MATCH INFO stack. */
    private JPanel leftRow(JComponent inner) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.add(inner);
        return row;
    }

    /** Keeps a Y-stack child at its natural height (no BoxLayout stretch). */
    private JComponent cappedRow(JComponent inner) {
        inner.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                inner.getPreferredSize().height));
        return inner;
    }

    /** A left-aligned sidebar row: a small caption followed by its content. */
    private JPanel infoRow(String caption, JComponent content) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        row.add(UiKit.caption(caption, Theme.CREAM));
        row.add(content);
        return row;
    }

    /** A row of tiny captured-piece figurines; a muted dash when empty. */
    private static final class CaptureTray extends JComponent {
        private static final int SLOT = 16;
        private List<Piece> captured = List.of();

        CaptureTray() {
            setOpaque(false);
            setPreferredSize(new Dimension(5 * SLOT + SLOT / 2, 22));
        }

        /** Replaces the displayed pieces (EDT only). */
        void show(List<Piece> captured) {
            this.captured = captured;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = UiKit.nice(graphics);
            if (captured.isEmpty()) {
                g.setColor(Theme.MUTED);
                g.setFont(Theme.normal(12f));
                g.drawString("—", 2, getHeight() - 7);
            } else {
                for (int i = 0; i < captured.size() && i < 5; i++) {
                    Piece piece = captured.get(i);
                    UiKit.drawFigurine(g, SLOT / 2 + i * SLOT, getHeight() - 3,
                            15, Theme.playerColor(piece.color()), piece.master());
                }
            }
            g.dispose();
        }
    }

    /** Five 8px pips: filled = the piece is alive, hollow grey = captured. */
    private static final class PieceCountRow extends JComponent {
        private static final int PIP = 8;
        private static final int GAP = 4;
        private final Color color;
        private int alive = 5;

        PieceCountRow(Color color) {
            this.color = color;
            setOpaque(false);
            setPreferredSize(new Dimension(5 * (PIP + GAP) + GAP, PIP + 2));
        }

        void setAlive(int alive) {
            this.alive = Math.max(0, Math.min(5, alive));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = UiKit.nice(graphics);
            for (int i = 0; i < 5; i++) {
                int x = GAP + i * (PIP + GAP);
                if (i < alive) {
                    g.setColor(color);
                    g.fillOval(x, 1, PIP, PIP);
                    g.setColor(Theme.INK);
                    g.drawOval(x, 1, PIP, PIP);
                } else {
                    g.setColor(Theme.DISABLED);
                    g.setStroke(new java.awt.BasicStroke(1.5f));
                    g.drawOval(x, 1, PIP, PIP);
                }
            }
            g.dispose();
        }
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
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        badge.add(avatar, BorderLayout.WEST);
        JPanel text = new JPanel(new GridLayout(2, 1, 0, 0));
        text.setOpaque(false);
        text.add(nameLabel);
        text.add(statsLabel);
        badge.add(text, BorderLayout.CENTER);
        JPanel dotWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        dotWrap.setOpaque(false);
        dotWrap.add(onlineDot);
        badge.add(dotWrap, BorderLayout.EAST);
        return badge;
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
        // Dark player shade for text on the light parchment (contrast §16).
        opponentLabel.setForeground(Theme.playerTextColor(opponent));
        UiKit.badgeText(opponentBadge, opponent.name() + " · OPPONENT");
        UiKit.badgeText(myBadge, myColor.name() + " · YOU");

        nameLabel.setText(model.me() == null ? "" : model.me().username().toUpperCase());
        nameLabel.setForeground(Theme.playerTextColor(myColor));
        if (model.me() != null) {
            statsLabel.setText("ELO " + model.me().elo() + "   ·   "
                    + model.me().wins() + "W · " + model.me().losses() + "L");
        }
        avatar.repaint();

        // Opponent cards: their color variant, dimmed, my perspective,
        // fixed side-by-side scale so the top band keeps a stable height.
        opponentCards.removeAll();
        state.hand(opponent).forEach(card -> {
            CardPanel panel = new CardPanel(card, opponent, true, null);
            panel.setCardScale(OPPONENT_CARD_SCALE);
            opponentCards.add(panel);
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
        transit.setCardScale(TRANSIT_CARD_SCALE);
        transitHolder.add(transit);
        UiKit.badgeText(goesBadge, "GOES TO " + state.turn().name());

        turnLabel.setText(bannerText(state, myColor));
        turnLabel.setAccent(Theme.playerColor(state.turn()));
        boolean waiting = state.isOngoing() && !model.isMatchOver()
                && state.turn() != myColor;
        waitPulseDot.setVisible(waiting);
        if (waiting && !pulseTimer.isRunning()) {
            pulseTimer.start();
        } else if (!waiting && pulseTimer.isRunning()) {
            pulseTimer.stop();
        }
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
        redPips.setAlive(redCount);
        bluePips.setAlive(blueCount);

        List<ClientModel.MoveInfo> moves = model.historyMoves();
        historyList.setListData(moves.toArray(new ClientModel.MoveInfo[0]));
        showHistory(!moves.isEmpty());
        myCaptureTray.show(model.myCaptures());
        enemyCaptureTray.show(model.enemyCaptures());
        if (moves.isEmpty()) {
            lastMoveLabel.setText("Last move: —");
        } else {
            ClientModel.MoveInfo last = moves.get(moves.size() - 1);
            lastMoveLabel.setText(last.pass()
                    ? "Last: PASS"
                    : "Last: " + last.cardName().toUpperCase() + " "
                            + last.fromSquare() + " → " + last.toSquare()
                            + (last.capture() ? " ✕" : ""));
        }

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

        // Resign button: live while the game runs. A pending request re-arms
        // when GameOver lands (match over / not ongoing) or a fresh match
        // deals an empty history (rematch / new game).
        if (!state.isOngoing() || model.isMatchOver()
                || model.historyMoves().isEmpty()) {
            resignPending = false;
        }
        resignButton.setEnabled(state.isOngoing() && !model.isMatchOver()
                && !resignPending);

        revalidate();
        repaint();
    }

    /** Short banner text: the game state in two words. */
    private String bannerText(GameState state, onitama.core.PlayerColor myColor) {
        // model.isMatchOver(): a forfeit ends the match via GameOver only —
        // the last state on the wire still claims to be ongoing.
        if (!state.isOngoing() || model.isMatchOver()) {
            return "GAME OVER";
        }
        if (state.turn() == myColor) {
            return "YOUR TURN";
        }
        return "WAITING…";
    }

    /**
     * Asks for confirmation on the themed dialog, then resigns. While the
     * confirmation is given and {@link GameOver} has not arrived yet the
     * button stays disabled, so a double click cannot send the request twice.
     */
    private void confirmResign() {
        if (resignPending) {
            return;
        }
        boolean confirmed = UiKit.confirmDialog(this, "Resign",
                "Resign this match?", "Yes", UiKit.Pill.DANGER);
        if (confirmed) {
            if (!model.isPracticeMode()) {
                // Practice resigns by leaving immediately; online waits for
                // the server's GameOver before the button comes back.
                resignPending = true;
                resignButton.setEnabled(false);
            }
            model.resign();
        }
    }

    private void showGameOverDialog(GameOver over) {
        if (model.gameOverAnnounced()) {
            return;
        }
        model.markGameOverAnnounced();
        refresh();
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        if (owner == null) {
            return;
        }
        boolean draw = over.winnerColor() == null;
        boolean won = !draw && over.winnerColor() == model.myColor();
        String title = draw ? "DRAW" : won ? "VICTORY" : "DEFEAT";
        Color titleColor = draw ? Theme.INK
                : Theme.playerTextColor(won ? myColor() : opponentColor());
        String wayText = switch (over.way()) {
            case STONE -> "Way of the Stone";
            case STREAM -> "Way of the Stream";
            case DRAW -> "Way of the Draw — the move limit was reached";
            default -> "By forfeit";
        };
        int moveNumber = model.state() == null ? 0 : model.state().moveNumber();

        UiKit.DialogSurface surface = new UiKit.DialogSurface(Theme.CREAM, 18);
        surface.setLayout(new GridBagLayout());
        surface.setBorder(BorderFactory.createEmptyBorder(24, 28, 24 + Theme.SHADOW_OFFSET, 28));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(Theme.display(28f));
        titleLabel.setForeground(titleColor);
        surface.add(titleLabel, constraints);

        constraints.gridy = 1;
        constraints.insets = new Insets(8, 0, 0, 0);
        JLabel subtitleLabel = new JLabel(wayText + " — move " + moveNumber,
                SwingConstants.CENTER);
        subtitleLabel.setFont(Theme.normal(12f));
        subtitleLabel.setForeground(Theme.INK);
        surface.add(subtitleLabel, constraints);

        constraints.gridy = 2;
        constraints.insets = new Insets(6, 0, 0, 0);
        JLabel detailLabel = new JLabel(detailText(over), SwingConstants.CENTER);
        detailLabel.setFont(Theme.normal(11f));
        detailLabel.setForeground(Theme.INK);
        surface.add(detailLabel, constraints);

        constraints.gridy = 3;
        constraints.insets = new Insets(18, 0, 0, 0);
        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.setOpaque(false);
        javax.swing.JDialog dialog = UiKit.undecoratedDialog(owner, surface, 420, 260);
        JButton primary = UiKit.pill(model.isPracticeMode() ? "Play again" : "Offer rematch",
                UiKit.Pill.GOLD);
        primary.addActionListener(event -> {
            dialog.dispose();
            if (model.isPracticeMode()) {
                model.startPracticeMatch(model.practiceDifficulty());
            } else {
                model.requestRematch();
            }
        });
        buttons.add(primary);
        JButton back = UiKit.pill("Back to lobby", UiKit.Pill.GOLD_OUTLINE);
        back.addActionListener(event -> {
            dialog.dispose();
            model.leaveToLobby();
        });
        buttons.add(back);
        surface.add(buttons, constraints);

        UiKit.playAppearTween(surface);
        dialog.setVisible(true); // modal; Swing timers keep animating on the EDT
    }

    /** The Elo-delta line (online) or the practice farewell. */
    private String detailText(GameOver over) {
        if (model.isPracticeMode()) {
            return "The dojo bot thanks you for the practice.";
        }
        if (model.me() == null) {
            return "New ELO — you: " + myEloAfter(over) + ", opponent: "
                    + opponentEloAfter(over);
        }
        // Elo is zero-sum: the opponent's delta is exactly the negative.
        int delta = myEloAfter(over) - model.me().elo();
        String green = String.format("#%06X", Theme.GREEN.getRGB() & 0xFFFFFF);
        String red = String.format("#%06X", Theme.CORAL.getRGB() & 0xFFFFFF);
        String mine = (delta >= 0 ? "+" : "") + delta;
        String theirs = (-delta >= 0 ? "+" : "") + -delta;
        return "<html><div style='text-align:center'>New ELO — you: <b>" + myEloAfter(over)
                + "</b> <font color=" + green + ">" + mine + "</font> · opponent: <b>"
                + opponentEloAfter(over) + "</b> <font color=" + red + ">" + theirs
                + "</font></div></html>";
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

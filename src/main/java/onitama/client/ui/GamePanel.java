package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;
import onitama.core.Card;
import onitama.core.CardDeck;
import onitama.core.PlayerColor;
import onitama.core.Square;
import onitama.core.duel.CardInstance;
import onitama.core.duel.Direction;
import onitama.core.duel.PlacedPiece;
import onitama.core.duel.PlayerView;
import onitama.core.duel.PublicEvent;
import onitama.core.duel.SelectionDraft;
import onitama.core.duel.SelectionOptions;
import onitama.core.duel.SkillCategory;
import onitama.core.duel.SkillDefinition;
import onitama.core.duel.TurnPhase;
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
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * 7x7 Grid Duel game screen.
 * Displays public movement hands, private skill cards, live definitions,
 * mana counter, deck counters, and action buttons.
 */
public final class GamePanel extends JPanel {

    private final ClientModel model;

    private final BoardPanel boardPanel = new BoardPanel();
    private final UiKit.HeadingLabel turnLabel = new UiKit.HeadingLabel(15f);
    private final JLabel opponentLabel = UiKit.label("", 14f);
    private final JLabel turnCountLabel = UiKit.caption("TURN 01", Theme.INK);
    private final PlayerBadgePanel ownBadge = new PlayerBadgePanel(true);
    private final PlayerBadgePanel opponentBadge = new PlayerBadgePanel(false);
    private final DeckStackPanel movementDeck = new DeckStackPanel(true);
    private final DeckStackPanel skillDeck = new DeckStackPanel(false);
    private final DeckDrawOverlay drawOverlay;
    private long drawGeneration;
    private final List<CardPanel> opponentMovementPanels = new ArrayList<>();
    private final List<CardBackPanel> opponentSkillPanels = new ArrayList<>();
    private List<String> builtOpponentMoves = List.of();
    private PlayerColor builtOpponentColor;

    private final JPanel opponentCards = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    private final CardHandPanel hand = new CardHandPanel();
    private final List<SkillCardPanel> mySkillPanels = new ArrayList<>();
    private List<CardInstance> builtSkills = List.of();
    private onitama.core.duel.RulesConfig handRules;
    private PlayerColor handColor;

    private final JButton skipMovementButton = UiKit.pill("Skip Movement", UiKit.Pill.CREAM_OUTLINE);
    private final JButton endTurnButton = UiKit.pill("End Turn", UiKit.Pill.GOLD);
    private final JButton castSkillButton = UiKit.pill("Cast Skill", UiKit.Pill.GOLD);
    private final JPanel directionPanel = new JPanel(new GridLayout(2, 2, 4, 4));
    private final UiKit.PillButton dirForward = UiKit.pill("Forward", UiKit.Pill.CREAM_OUTLINE);
    private final UiKit.PillButton dirBackward = UiKit.pill("Back", UiKit.Pill.CREAM_OUTLINE);
    private final UiKit.PillButton dirLeft = UiKit.pill("Left", UiKit.Pill.CREAM_OUTLINE);
    private final UiKit.PillButton dirRight = UiKit.pill("Right", UiKit.Pill.CREAM_OUTLINE);
    private final CardInspectorPanel inspector = new CardInspectorPanel(this::refreshInspector);
    private JComponent hoveredCard;
    private String inspectorSelection;
    private String currentInstruction = "";
    private final JLabel instructionLabel = UiKit.caption("", Theme.INK);

    private enum RelativeDirection { FORWARD, BACKWARD, LEFT, RIGHT }

    private static Direction relativeToCanonical(RelativeDirection rel, PlayerColor viewer) {
        if (viewer == PlayerColor.RED) {
            return switch (rel) {
                case FORWARD -> Direction.NORTH;
                case BACKWARD -> Direction.SOUTH;
                case LEFT -> Direction.EAST;
                case RIGHT -> Direction.WEST;
            };
        } else { // BLUE
            return switch (rel) {
                case FORWARD -> Direction.SOUTH;
                case BACKWARD -> Direction.NORTH;
                case LEFT -> Direction.WEST;
                case RIGHT -> Direction.EAST;
            };
        }
    }

    private final JComponent waitPulseDot = new JComponent() {
        {
            setPreferredSize(new Dimension(12, 12));
            setOpaque(false);
            setVisible(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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
    private JButton resignButton;
    private final List<CardPanel> myCardPanels = new ArrayList<>();
    private List<String> builtMovementCardIds = List.of();
    private static final double OPPONENT_CARD_SCALE = 0.36;
    private static final int SIDEBAR_WIDTH = 270;

    private ForesightDialog foresightDialog;
    private String foresightMatchId;
    private long foresightChoiceId;

    private PlayerColor myColor() {
        return model.myColor();
    }

    private PlayerColor opponentColor() {
        return myColor() == null ? null : myColor().opponent();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        java.awt.Image background = AssetStore.optional("cinematic-valley.png");
        if (background == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        double scale = Math.max(getWidth() / (double) background.getWidth(null),
                getHeight() / (double) background.getHeight(null));
        int w = (int) (background.getWidth(null) * scale);
        int h = (int) (background.getHeight(null) * scale);
        g.drawImage(background, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
        g.setColor(new Color(255, 241, 208, 24));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
    }

    /** Builds the board, public opponent hand, and unified private hand. */
    public GamePanel(ClientModel model) {
        this(model, new DeckDrawOverlay());
    }

    // Tests inject a visual clock without changing the model or authoritative rules.
    GamePanel(ClientModel model, DeckDrawOverlay drawOverlay) {
        this.model = model;
        this.drawOverlay = drawOverlay;
        setLayout(new BorderLayout(12, 6));
        setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        setBackground(Theme.BG);

        // Top opponent band
        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.setOpaque(false);
        opponentLabel.setHorizontalAlignment(SwingConstants.CENTER);
        opponentLabel.setFont(Theme.display(16f));
        backCaption(opponentLabel);
        opponentCards.setOpaque(false);
        north.add(opponentLabel, BorderLayout.NORTH);
        north.add(opponentCards, BorderLayout.CENTER);

        // The complete hand has its own space so hovered cards cannot hide board squares.
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setOpaque(false);
        center.add(north, BorderLayout.NORTH);
        center.add(boardPanel, BorderLayout.CENTER);
        center.add(buildBottomControls(), BorderLayout.SOUTH);
        JPanel scene = new JPanel(new BorderLayout(12, 6));
        scene.setOpaque(false);
        scene.add(buildTurnControls(), BorderLayout.WEST);
        scene.add(center, BorderLayout.CENTER);
        scene.add(buildRightColumn(), BorderLayout.EAST);
        add(DeckDrawOverlay.wrap(scene, drawOverlay), BorderLayout.CENTER);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) { cancelDraws(); }
        });

        boardPanel.onClick(sq -> {
            hoveredCard = null;
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.boardClicked(sq);
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSelection");
        getActionMap().put("clearSelection", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                cancelDraws();
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
                    cancelPresentation();
                    closeForesightDialog();
                    boardPanel.clearHighlights();
                }
            }

            @Override
            public void onMatchChanged() {
                refresh();
            }

            @Override
            public void onPresentationReset() {
                hoveredCard = null;
                inspector.clear();
                cancelPresentation();
                ownBadge.reset();
                opponentBadge.reset();
            }

            @Override
            public void onAcceptedPresentation(PlayerView priorView, PlayerView currentView, List<PublicEvent> events) {
                handleAcceptedPresentation(priorView, currentView, events);
            }

            @Override
            public void onGameOver(GameOver over) {
                cancelPresentation();
                closeForesightDialog();
                showGameOverDialog(over);
            }

            @Override
            public void onConnectionLost() {
                cancelPresentation();
                closeForesightDialog();
            }

            @Override
            public void onOpponentLeft(int graceSeconds) {
                cancelPresentation();
                turnLabel.setText("OPPONENT DISCONNECTED (" + graceSeconds + "s)");
            }

            @Override
            public void onRematchOffered() {
                turnLabel.setText("OPPONENT WANTS A REMATCH!");
            }
        });
    }

    private void handleAcceptedPresentation(PlayerView priorView, PlayerView currentView, List<PublicEvent> events) {
        if (priorView == null || currentView == null || events == null) {
            return;
        }

        cancelActionAnimations(true);
        if (model.reducedMotion() || model.screen() != Screen.GAME || currentView.outcome() != null
                || model.currentSync().paused()) return;
        scheduleDraws(priorView, currentView, events);

        // 1. Multi-piece moves
        List<BoardPanel.MovingPiece> moves = new ArrayList<>();
        for (PublicEvent ev : events) {
            if (ev instanceof PublicEvent.PieceMoved pm) {
                PlayerColor pColor = null;
                for (PlacedPiece pp : priorView.pieces()) {
                    if (pp.piece().id() == pm.pieceId()) {
                        pColor = pp.piece().color();
                        break;
                    }
                }
                if (pColor == null) {
                    for (PlacedPiece pp : currentView.pieces()) {
                        if (pp.piece().id() == pm.pieceId()) {
                            pColor = pp.piece().color();
                            break;
                        }
                    }
                }
                if (pColor == null) {
                    pColor = currentView.turn();
                }
                moves.add(new BoardPanel.MovingPiece(pm.pieceId(), pm.from(), pm.to(), pColor));
            }
        }

        // 2. Eliminations
        List<Square> elimSquares = new ArrayList<>();
        PlayerColor elimActor = currentView.turn();
        for (PublicEvent ev : events) {
            if (ev instanceof PublicEvent.PieceEliminated pe) {
                elimSquares.add(pe.at());
            }
        }

        // 3. Skill committed & cue
        PublicEvent.SkillCommitted committedSkill = null;
        for (PublicEvent ev : events) {
            if (ev instanceof PublicEvent.SkillCommitted sc) {
                committedSkill = sc;
                break;
            }
        }
        if (committedSkill != null) {
            SkillDefinition def = currentView.rules().skill(committedSkill.skill().definitionId());
            String sName = def != null ? def.name() : committedSkill.skill().definitionId();
            SkillCategory cat = def != null ? def.category() : SkillCategory.CONTROL;
            boardPanel.playSkillCue(sName, cat, committedSkill.actor());

            if (committedSkill.actor() == currentView.viewer()
                    && !currentView.ownSkillHand().contains(committedSkill.skill())) {
                CardInstance spent = committedSkill.skill();
                int index = priorView.ownSkillHand().indexOf(spent);
                if (index >= 0) {
                    SkillCardPanel face = new SkillCardPanel(spent, priorView.rules(), priorView.viewer(),
                            priorView.viewer() == PlayerColor.BLUE ? priorView.blueMana() : priorView.redMana(),
                            priorView.phase(), true, false, null);
                    hand.animateDeparture(face, 2 + index, 2 + priorView.ownSkillHand().size());
                }
            }
        }

        for (PublicEvent event : events) {
            if (event instanceof PublicEvent.MovementPlayed played && played.actor() == currentView.viewer()) {
                List<String> prior = currentView.viewer() == PlayerColor.BLUE
                        ? priorView.blueMovementHand() : priorView.redMovementHand();
                int index = prior.indexOf(played.cardId());
                if (index >= 0) {
                    CardPanel face = new CardPanel(CardDeck.cardById(played.cardId()), currentView.viewer(), false, null);
                    hand.animateDeparture(face, index, 2 + priorView.ownSkillHand().size());
                }
            }
        }

        // 5. Board animation trigger
        if (!moves.isEmpty()) {
            boardPanel.animateMoves(moves, () -> {
                if (!elimSquares.isEmpty()) {
                    boardPanel.playEliminations(elimSquares, elimActor);
                }
            });
        } else if (!elimSquares.isEmpty()) {
            boardPanel.playEliminations(elimSquares, elimActor);
        }
    }

    private void scheduleDraws(PlayerView before, PlayerView after, List<PublicEvent> events) {
        List<CardDraws.Draw> draws = CardDraws.between(before, after, events);
        if (draws.isEmpty()) return;
        long generation = drawGeneration;
        String match = model.currentSync().matchId();
        // Reconciliation has already adopted the new hand. Let Swing validate new slots first.
        SwingUtilities.invokeLater(() -> {
            if (generation != drawGeneration || model.screen() != Screen.GAME || model.reducedMotion()
                    || model.currentSync() == null || model.currentSync().paused()
                    || !match.equals(model.currentSync().matchId()) || model.view().revision() != after.revision()
                    || model.isMatchOver()) return;
            layoutTree(this);
            var flights = new ArrayList<DeckDrawOverlay.Flight>();
            for (CardDraws.Draw draw : draws) {
                boolean own = draw.actor() == after.viewer();
                List<? extends JComponent> cards = draw.movement()
                        ? (own ? myCardPanels : opponentMovementPanels)
                        : (own ? mySkillPanels : opponentSkillPanels);
                if (draw.slot() < 0 || draw.slot() >= cards.size()) continue;
                JComponent target = cards.get(draw.slot());
                if (target.getWidth() <= 0 || target.getHeight() <= 0) continue;
                DeckStackPanel deck = draw.movement() ? movementDeck : skillDeck;
                String key = draw.movement() ? "move:" + draw.actor() + ":" + ((CardPanel) target).card().id()
                        : own ? "own-skill:" + ((SkillCardPanel) target).instance().instanceId()
                        : "opponent-slot:" + draw.slot();
                java.util.function.Supplier<java.awt.Rectangle> source = () ->
                        SwingUtilities.convertRectangle(deck, deck.topBounds(), drawOverlay);
                var destination = DeckDrawOverlay.boundsOf(target, drawOverlay);
                int delay = flights.size() * 80;
                // Concealed destinations never construct a SkillCardPanel or receive a skill identity.
                flights.add(!draw.movement() && !own
                        ? DeckDrawOverlay.Flight.concealed(key, deck.backImage(), source, destination, delay)
                        : DeckDrawOverlay.Flight.reveal(key, deck.backImage(), DeckDrawOverlay.snapshot(target),
                                source, destination, delay));
                drawOverlay.bind(target, key);
            }
            drawOverlay.play(flights);
        });
    }

    private static void layoutTree(java.awt.Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents())
            if (child instanceof java.awt.Container container) layoutTree(container);
    }

    private void cancelDraws() {
        drawGeneration++;
        drawOverlay.cancel();
    }

    /** Gives smaller windows more board space while keeping the hand readable. */
    @Override
    public void doLayout() {
        int handHeight = Math.max(196, Math.min(226, getHeight() - 464));
        if (hand.getPreferredSize().height != handHeight) {
            hand.setPreferredSize(new Dimension(720, handHeight));
            hand.setMinimumSize(new Dimension(280, handHeight));
        }
        super.doLayout();
    }

    private void rebuildHand(PlayerView view, List<String> cardIds) {
        boolean reuse = view.rules().equals(handRules) && view.viewer() == handColor;
        List<CardPanel> oldCardPanels = reuse ? new ArrayList<>(myCardPanels) : List.of();
        List<SkillCardPanel> oldSkillPanels = reuse ? new ArrayList<>(mySkillPanels) : List.of();
        myCardPanels.clear();
        mySkillPanels.clear();
        builtMovementCardIds = List.copyOf(cardIds);
        builtSkills = List.copyOf(view.ownSkillHand());
        handRules = view.rules();
        handColor = view.viewer();
        List<JComponent> cards = new ArrayList<>();

        for (String id : cardIds) {
            CardPanel existing = null;
            for (CardPanel cp : oldCardPanels) {
                if (cp.card().id().equals(id)) {
                    existing = cp;
                    break;
                }
            }
            if (existing != null) {
                existing.setReducedMotion(model.reducedMotion());
                myCardPanels.add(existing);
                cards.add(existing);
            } else {
                Card card = CardDeck.cardById(id);
                CardPanel panel = new CardPanel(card, view.viewer(), false, () -> {
                    cancelDraws();
                    boardPanel.finishMoveAnimation();
                    model.cardClicked(id);
                });
                panel.setReducedMotion(model.reducedMotion());
                panel.setCardScale(.76);
                inspectOnHover(panel);
                myCardPanels.add(panel);
                cards.add(panel);
            }
        }

        int mana = view.viewer() == PlayerColor.BLUE ? view.blueMana() : view.redMana();
        for (CardInstance instance : view.ownSkillHand()) {
            SkillCardPanel existing = null;
            for (SkillCardPanel sp : oldSkillPanels) {
                if (sp.instance().instanceId().equals(instance.instanceId())) {
                    existing = sp;
                    break;
                }
            }
            if (existing != null) {
                existing.setReducedMotion(model.reducedMotion());
                existing.updateState(mana, view.phase(), model.myTurn(),
                        instance.instanceId().equals(model.selectedSkillInstanceId()));
                mySkillPanels.add(existing);
                cards.add(existing);
            } else {
                SkillCardPanel panel = new SkillCardPanel(instance, view.rules(), view.viewer(),
                        mana, view.phase(), model.myTurn(),
                        instance.instanceId().equals(model.selectedSkillInstanceId()),
                        () -> {
                            cancelDraws();
                            boardPanel.finishMoveAnimation();
                            model.skillClicked(instance.instanceId());
                        });
                panel.setReducedMotion(model.reducedMotion());
                inspectOnHover(panel);
                mySkillPanels.add(panel);
                cards.add(panel);
            }
        }

        hand.setReducedMotion(model.reducedMotion());
        hand.setCards(cards);
    }

    /** Local text plates preserve contrast without bleaching the entire landscape. */
    private static void backCaption(JLabel label) {
        label.setOpaque(true);
        label.setBackground(Theme.CREAM);
        label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
    }

    private JPanel buildBottomControls() {
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setOpaque(false);

        JLabel handLabel = UiKit.caption("YOUR HAND  ·  MOVEMENT PUBLIC / SKILLS PRIVATE", Theme.INK);
        handLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        backCaption(handLabel);
        bottom.add(handLabel);
        bottom.add(hand);

        instructionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        instructionLabel.setFont(Theme.normal(12f));
        instructionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        instructionLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        instructionLabel.setForeground(Theme.INK);
        backCaption(instructionLabel);
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(instructionLabel);
        bottom.add(Box.createVerticalStrut(2));

        return bottom;
    }

    /** Keeps turn actions in the unused left margin, freeing height for the board. */
    private JPanel buildTurnControls() {
        JPanel rail = new JPanel(new BorderLayout(0, 12));
        rail.setOpaque(false);
        int width = Math.max(skipMovementButton.getPreferredSize().width, 236);
        rail.setPreferredSize(new Dimension(width, 100));
        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.setOpaque(false);
        for (JButton button : List.of(skipMovementButton, castSkillButton, endTurnButton)) {
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
        }
        directionPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (JButton button : List.of(dirForward, dirBackward, dirLeft, dirRight)) {
            button.setFont(Theme.bold(10f));
        }

        skipMovementButton.addActionListener(e -> {
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.skipMovement();
        });
        actions.add(skipMovementButton);
        actions.add(Box.createVerticalStrut(10));

        castSkillButton.addActionListener(e -> {
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.castSkill();
        });
        castSkillButton.setVisible(false);
        actions.add(castSkillButton);

        // Direction buttons for skills like Shove/Pull/Gust
        directionPanel.setOpaque(false);
        directionPanel.setVisible(false);
        dirForward.addActionListener(e -> {
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.selectDirection(relativeToCanonical(RelativeDirection.FORWARD, myColor()));
        });
        dirBackward.addActionListener(e -> {
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.selectDirection(relativeToCanonical(RelativeDirection.BACKWARD, myColor()));
        });
        dirLeft.addActionListener(e -> {
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.selectDirection(relativeToCanonical(RelativeDirection.LEFT, myColor()));
        });
        dirRight.addActionListener(e -> {
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.selectDirection(relativeToCanonical(RelativeDirection.RIGHT, myColor()));
        });
        directionPanel.add(dirForward);
        directionPanel.add(dirBackward);
        directionPanel.add(dirLeft);
        directionPanel.add(dirRight);
        actions.add(directionPanel);
        actions.add(Box.createVerticalStrut(10));

        endTurnButton.addActionListener(e -> {
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.endTurn();
        });
        actions.add(endTurnButton);

        JPanel reference = new JPanel(new BorderLayout(0, 8));
        reference.setOpaque(false);
        JPanel pages = new JPanel(new java.awt.CardLayout());
        pages.add(new PlayedCardsPanel(model), "plays");
        pages.add(inspector, "inspect");
        JPanel tabs = new JPanel(new GridLayout(1, 2, 6, 0));
        tabs.setOpaque(false);
        JButton playsTab = UiKit.miniPill("Played cards", UiKit.Pill.GOLD);
        JButton inspectTab = UiKit.miniPill("Card details", UiKit.Pill.CREAM_OUTLINE);
        playsTab.addActionListener(e -> ((java.awt.CardLayout) pages.getLayout()).show(pages, "plays"));
        inspectTab.addActionListener(e -> ((java.awt.CardLayout) pages.getLayout()).show(pages, "inspect"));
        tabs.add(playsTab);
        tabs.add(inspectTab);
        reference.add(tabs, BorderLayout.NORTH);
        reference.add(pages, BorderLayout.CENTER);
        rail.add(reference, BorderLayout.CENTER);
        rail.add(actions, BorderLayout.SOUTH);
        return rail;
    }

    private Component buildRightColumn() {
        JPanel column = new JPanel(new BorderLayout(0, 12));
        column.setOpaque(false);
        column.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 100));
        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        JPanel banner = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        banner.setOpaque(false);
        banner.add(turnLabel);
        banner.add(waitPulseDot);
        top.add(banner, BorderLayout.NORTH);
        top.add(opponentBadge, BorderLayout.CENTER);
        column.add(top, BorderLayout.NORTH);

        JPanel decks = new JPanel(new GridLayout(1, 2, 8, 0));
        decks.setOpaque(false);
        // Compact deck art leaves space for conversation at the minimum window height.
        decks.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 100));
        decks.add(movementDeck);
        decks.add(skillDeck);
        JPanel middle = new JPanel(new GridBagLayout());
        middle.setOpaque(false);
        var constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        middle.add(decks, constraints);
        constraints.gridy++;
        constraints.insets = new Insets(8, 0, 0, 0);
        turnCountLabel.setHorizontalAlignment(SwingConstants.CENTER);
        backCaption(turnCountLabel);
        middle.add(turnCountLabel, constraints);
        JPanel conversation = new JPanel(new BorderLayout(0, 4));
        conversation.setOpaque(false);
        conversation.add(middle, BorderLayout.NORTH);
        conversation.add(new ChatPanel(model), BorderLayout.CENTER);
        column.add(conversation, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 12));
        bottom.setOpaque(false);
        bottom.add(ownBadge, BorderLayout.CENTER);
        JPanel controls = new JPanel(new GridLayout(1, 2, 10, 0));
        controls.setOpaque(false);
        JButton menu = UiKit.pill("Menu", UiKit.Pill.CREAM_OUTLINE);
        menu.addActionListener(event -> model.leaveToLobby());
        controls.add(menu);
        resignButton = UiKit.pill("Resign", UiKit.Pill.DANGER);
        resignButton.addActionListener(event -> confirmResign());
        controls.add(resignButton);
        bottom.add(controls, BorderLayout.SOUTH);
        column.add(bottom, BorderLayout.SOUTH);
        return column;
    }

    private void refreshOpponentHand(PlayerView view, PlayerColor opponent) {
        List<String> ids = CardDraws.moves(view, opponent);
        if (!ids.equals(builtOpponentMoves) || opponentSkillPanels.size() != view.opponentSkillCount()
                || builtOpponentColor != opponent) {
            opponentCards.removeAll();
            opponentMovementPanels.clear();
            opponentSkillPanels.clear();
            builtOpponentMoves = List.copyOf(ids);
            builtOpponentColor = opponent;
            for (String id : ids) {
                CardPanel card = new CardPanel(CardDeck.cardById(id), opponent, false, null);
                card.setCardScale(OPPONENT_CARD_SCALE);
                inspectOnHover(card);
                opponentMovementPanels.add(card);
                opponentCards.add(card);
            }
            opponentCards.add(Box.createHorizontalStrut(16));
            for (int i = 0; i < view.opponentSkillCount(); i++) {
                CardBackPanel back = new CardBackPanel();
                opponentSkillPanels.add(back);
                opponentCards.add(back);
            }
        }
        for (CardPanel card : opponentMovementPanels) card.setReducedMotion(model.reducedMotion());
    }

    private void refreshBadges(PlayerView view) {
        PlayerColor own = view.viewer();
        boolean paused = model.currentSync().paused();
        int mine = own == PlayerColor.BLUE ? view.blueMana() : view.redMana();
        int theirs = own == PlayerColor.BLUE ? view.redMana() : view.blueMana();
        int ownPieces = (int) view.pieces().stream().filter(p -> p.piece().color() == own).count();
        String name = model.me() == null ? "YOU" : model.me().username();
        ownBadge.update(name, own, mine, view.rules().manaCap(), view.reservedMana(), ownPieces,
                view.outcome() == null && view.turn() == own, paused, model.reducedMotion());
        opponentBadge.update(model.opponentName(), own.opponent(), theirs, view.rules().manaCap(), 0,
                view.pieces().size() - ownPieces, view.outcome() == null && view.turn() != own,
                paused, model.reducedMotion());
        movementDeck.setCount(view.movementDeckCount());
        skillDeck.setCount(view.skillDeckCount());
    }

    private void refresh() {
        if (model.screen() != Screen.GAME) {
            cancelPresentation();
            return;
        }
        PlayerView view = model.view();
        if (view == null) {
            closeForesightDialog();
            return;
        }
        PlayerColor myColor = model.myColor();
        PlayerColor opponent = myColor.opponent();

        opponentLabel.setText("VS  " + model.opponentName().toUpperCase());
        opponentLabel.setForeground(Theme.playerTextColor(opponent));

        refreshBadges(view);
        refreshOpponentHand(view, opponent);
        drawOverlay.setReducedMotion(model.reducedMotion());
        if (model.currentSync().paused()) cancelPresentation();

        boardPanel.setReducedMotion(model.reducedMotion());
        hand.setReducedMotion(model.reducedMotion());

        // My movement cards
        List<String> myCardIds = myColor == PlayerColor.BLUE
                ? view.blueMovementHand()
                : view.redMovementHand();
        if (!myCardIds.equals(builtMovementCardIds) || !view.ownSkillHand().equals(builtSkills)
                || !view.rules().equals(handRules) || view.viewer() != handColor) {
            rebuildHand(view, myCardIds);
        }
        for (CardPanel card : myCardPanels) card.setReducedMotion(model.reducedMotion());
        for (SkillCardPanel card : mySkillPanels) card.setReducedMotion(model.reducedMotion());
        if (model.reducedMotion()) cancelPresentation();
        for (int i = 0; i < myCardPanels.size(); i++) {
            myCardPanels.get(i).setSelected(
                    myCardIds.get(i).equals(model.selectedCardId()));
        }

        int myMana = myColor == PlayerColor.BLUE ? view.blueMana() : view.redMana();
        for (SkillCardPanel panel : mySkillPanels) {
            panel.updateState(myMana, view.phase(), model.myTurn(),
                    panel.instance().instanceId().equals(model.selectedSkillInstanceId()));
        }
        hand.updateLayers();

        // Action buttons
        skipMovementButton.setEnabled(model.canSkipMovement());
        endTurnButton.setEnabled(model.canEndTurn());
        castSkillButton.setVisible(model.selectedSkillInstanceId() != null);
        castSkillButton.setEnabled(model.canCastSkill() && !model.currentSync().paused());

        boolean showDirs = model.skillOptions() != null
                && !model.skillOptions().selectableDirections().isEmpty();
        directionPanel.setVisible(showDirs);
        if (showDirs) {
            updateDirectionButton(dirForward, RelativeDirection.FORWARD);
            updateDirectionButton(dirBackward, RelativeDirection.BACKWARD);
            updateDirectionButton(dirLeft, RelativeDirection.LEFT);
            updateDirectionButton(dirRight, RelativeDirection.RIGHT);
        }

        updateTargetingAndInstructions(view);
        refreshInspector();

        // Turn & Phase Banner
        String banner = bannerText(view, myColor);
        turnLabel.setText(banner);
        turnLabel.setAccent(Theme.playerColor(view.turn()));

        boolean waiting = !model.isMatchOver() && view.turn() != myColor
                && model.screen() == Screen.GAME && !model.reducedMotion() && !model.currentSync().paused();
        waitPulseDot.setVisible(waiting);
        if (waiting && !pulseTimer.isRunning()) {
            pulseTimer.start();
        } else if (!waiting && pulseTimer.isRunning()) {
            pulseTimer.stop();
        }

        turnCountLabel.setText("TURN " + view.startedTurns() + "  /  " + view.completedTurns() + " COMPLETED");

        // Board view & highlights
        boardPanel.setView(view);
        if (model.selectedSkillInstanceId() != null && model.skillOptions() != null) {
            SelectionOptions opt = model.skillOptions();
            SelectionDraft draft = model.skillDraft();
            java.util.Set<Integer> selectablePieces = new java.util.HashSet<>(opt.selectablePieceIds());
            List<Integer> selectedPieces = draft != null ? draft.pieceIds() : List.of();
            java.util.Set<Square> selectableSquares = new java.util.HashSet<>(opt.selectableSquares());
            List<Square> selectedDests = draft != null ? draft.destinations() : List.of();
            boardPanel.setTargetingFeedback(selectablePieces, selectedPieces, selectableSquares, selectedDests);
            boardPanel.setSelection(null, List.of());
        } else {
            boardPanel.clearTargetingFeedback();
            boardPanel.setSelection(model.selectedSquare(), model.highlightedTargets());
        }
        if (model.lastMoveFrom() != null && model.lastMoveTo() != null) {
            boardPanel.setLastMove(model.lastMoveFrom(), model.lastMoveTo());
        }

        // Resign button
        resignButton.setEnabled(!model.isMatchOver() && model.inFlightRequest() == null);

        // Screen observers may run before CardLayout has made this panel visible.
        SwingUtilities.invokeLater(() -> {
            PlayerView current = model.view();
            if (current == null) closeForesightDialog();
            else refreshForesightDialog(current);
        });

        revalidate();
        repaint();
    }

    private void inspectOnHover(JComponent card) {
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent event) {
                hoveredCard = card;
                refreshInspector();
            }
        });
    }

    private void refreshInspector() {
        PlayerView view = model.view();
        if (view == null || model.screen() != Screen.GAME) {
            hoveredCard = null;
            inspector.clear();
            return;
        }
        String selection = model.selectedSkillInstanceId() == null ? "move:" + model.selectedCardId()
                : "skill:" + model.selectedSkillInstanceId();
        if (!java.util.Objects.equals(inspectorSelection, selection)) {
            hoveredCard = null;
            inspectorSelection = selection;
        }
        // Keep the last reference on exit so the pointer can reach its scrollbar.
        boolean ownMove = myCardPanels.contains(hoveredCard);
        boolean enemyMove = opponentMovementPanels.contains(hoveredCard);
        if (inspector.browsingSpent()) {
            inspector.showSpent(view);
        } else if (hoveredCard instanceof CardPanel card && (ownMove || enemyMove)) {
            String id = card.card().id();
            PlayerColor owner = ownMove ? view.viewer() : view.viewer().opponent();
            if (CardDraws.moves(view, owner).contains(id)) inspector.showMovement(card.card(), owner);
            else { hoveredCard = null; showSelectedReference(view); }
        } else if (hoveredCard instanceof SkillCardPanel card && mySkillPanels.contains(card)
                && view.ownSkillHand().contains(card.instance())) {
            inspector.showSkill(card.instance(), view);
        } else {
            hoveredCard = null;
            showSelectedReference(view);
        }
        SelectionDraft draft = model.skillDraft();
        String progress = draft == null ? "" : "Selected: " + draft.pieceIds().size() + " piece(s), "
                + draft.destinations().size() + " destination(s)"
                + (draft.direction() == null ? "" : ", direction set")
                + (model.canCastSkill() && !model.currentSync().paused() ? " - ready to cast" : "");
        inspector.updateTurn(view, currentInstruction, progress, model.currentSync().paused(), model.isMatchOver());
    }

    private void showSelectedReference(PlayerView view) {
        var skill = view.ownSkillHand().stream()
                .filter(c -> c.instanceId().equals(model.selectedSkillInstanceId())).findFirst();
        if (skill.isPresent()) {
            inspector.showSkill(skill.get(), view);
        } else if (model.selectedCardId() != null
                && CardDraws.moves(view, view.viewer()).contains(model.selectedCardId())) {
            view.rules().movementCards().stream().filter(c -> c.id().equals(model.selectedCardId()))
                    .findFirst().ifPresent(c -> inspector.showMovement(c, view.viewer()));
        } else inspector.idle();
    }

    private void updateDirectionButton(UiKit.PillButton button, RelativeDirection rel) {
        Direction cDir = relativeToCanonical(rel, myColor());
        boolean selectable = model.skillOptions() != null
                && model.skillOptions().selectableDirections().contains(cDir);
        button.setEnabled(selectable);
        boolean isSelected = model.skillDraft() != null && model.skillDraft().direction() == cDir;
        button.setVariant(isSelected ? UiKit.Pill.GOLD : UiKit.Pill.CREAM_OUTLINE);
    }

    private void updateTargetingAndInstructions(PlayerView view) {
        if (model.isMatchOver()) {
            showInstruction("Match finished.");
        } else if (model.inFlightRequest() != null) {
            showInstruction("Waiting for the server to confirm your action...");
        } else if (model.currentSync().paused()) {
            showInstruction("Match paused - waiting for your opponent to reconnect.");
        } else if (!model.myTurn()) {
            showInstruction("Waiting for opponent...");
        } else if (model.selectedSkillInstanceId() != null) {
            SkillDefinition def = view.ownSkillHand().stream()
                    .filter(c -> c.instanceId().equals(model.selectedSkillInstanceId()))
                    .map(c -> view.rules().skill(c.definitionId())).findFirst().orElse(null);
            String name = def == null ? "Skill" : def.name();
            int mana = view.viewer() == PlayerColor.BLUE ? view.blueMana() : view.redMana();
            SelectionOptions options = model.skillOptions();
            SelectionDraft draft = model.skillDraft();
            if (def != null && mana < def.manaCost()) {
                showInstruction(name + ": needs " + def.manaCost() + " mana (you have " + mana + ").");
            } else if (model.canCastSkill()) {
                // Selectable IDs can remain after a complete choice to allow optional targets.
                boolean optional = options.selectablePieceIds().stream()
                        .anyMatch(id -> draft == null || !draft.pieceIds().contains(id));
                showInstruction("Ready to cast " + name + " - click Cast Skill."
                        + (optional ? " More targets are optional." : ""));
            } else if (options != null && !options.selectableSquares().isEmpty()) {
                showInstruction(name + ": choose a highlighted destination square.");
            } else if (options != null && !options.selectableDirections().isEmpty()
                    && (draft == null || draft.direction() == null)) {
                showInstruction(name + ": choose a direction.");
            } else if (options != null && !options.selectablePieceIds().isEmpty()) {
                boolean started = draft != null && !draft.pieceIds().isEmpty();
                showInstruction(name + (started ? ": choose another highlighted piece." : ": choose a highlighted piece."));
            } else if (view.phase() != TurnPhase.SKILL) {
                showInstruction("Finish or skip movement before casting " + name + ".");
            } else {
                showInstruction(name + ": no valid targets. Press Escape to choose again.");
            }
        } else if (view.phase() == TurnPhase.MOVEMENT) {
            showInstruction(model.selectedCardId() == null ? "Choose a movement card, then a piece."
                    : model.selectedPieceId() == null ? "Select a piece to move." : "Select a highlighted destination.");
        } else if (view.phase() == TurnPhase.SKILL) {
            showInstruction("Select a skill card, or End Turn.");
        } else if (view.phase() == TurnPhase.END) {
            showInstruction("Click End Turn to finish.");
        } else {
            showInstruction("");
        }
    }

    private void showInstruction(String text) {
        currentInstruction = text;
        instructionLabel.setText(text);
        instructionLabel.setToolTipText(text);
        castSkillButton.setToolTipText(model.selectedSkillInstanceId() == null ? null : text);
    }

    private void cancelActionAnimations(boolean preserveReflow) {
        cancelDraws();
        boardPanel.cancelAnimations();
        if (preserveReflow) hand.cancelDeparture();
        else hand.cancelAnimations();
        for (CardPanel card : myCardPanels) card.cancelAnimations();
        for (SkillCardPanel card : mySkillPanels) card.cancelAnimations();
    }

    private void cancelPresentation() {
        cancelActionAnimations(false);
        hoveredCard = null;
        inspector.clear();
        ownBadge.cancelAnimation();
        opponentBadge.cancelAnimation();
        pulseTimer.stop();
    }

    @Override public void removeNotify() {
        cancelPresentation();
        super.removeNotify();
    }

    private void refreshForesightDialog(PlayerView view) {
        if (model.screen() != Screen.GAME || model.isMatchOver()
                || view.phase() != TurnPhase.CHOICE || view.ownChoice() == null) {
            closeForesightDialog();
            return;
        }
        String matchId = model.currentSync().matchId();
        long choiceId = view.ownChoice().choiceId();
        if (foresightDialog != null && (!matchId.equals(foresightMatchId) || choiceId != foresightChoiceId)) {
            closeForesightDialog();
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (owner == null || !isShowing()) return;
        if (foresightDialog == null) {
            foresightMatchId = matchId;
            foresightChoiceId = choiceId;
            foresightDialog = new ForesightDialog(owner, view.ownChoice().preview(), view.rules(),
                    view.viewer(), model::resolveForesight, this::confirmResign);
        }
        boolean ready = model.inFlightRequest() == null;
        foresightDialog.setActionsEnabled(ready && !model.currentSync().paused(), ready);
        foresightDialog.setVisible(true);
    }

    private void closeForesightDialog() {
        if (foresightDialog != null) {
            foresightDialog.dispose();
            foresightDialog = null;
            foresightMatchId = null;
        }
    }

    private String bannerText(PlayerView view, PlayerColor myColor) {
        if (model.currentSync().paused()) return "MATCH PAUSED";
        if (model.isMatchOver()) {
            return "GAME OVER";
        }
        if (view.turn() == myColor) {
            return switch (view.phase()) {
                case MOVEMENT -> "YOUR TURN — MOVE";
                case SKILL -> "YOUR TURN — SKILL";
                case CHOICE -> "RESOLVING FORESIGHT";
                case END -> "YOUR TURN — END";
                case FINISHED -> "GAME OVER";
            };
        }
        return switch (view.phase()) {
            case CHOICE -> "OPPONENT FORESIGHT";
            default -> "OPPONENT'S TURN";
        };
    }

    private void confirmResign() {
        if (model.isMatchOver() || model.inFlightRequest() != null) {
            return;
        }
        boolean confirmed = UiKit.confirmDialog(this, "Resign",
                "Resign this match?", "Yes", UiKit.Pill.DANGER);
        // A server update may arrive while the confirmation dialog is open.
        if (confirmed && !model.isMatchOver() && model.inFlightRequest() == null) {
            model.resign();
        }
    }

    private void showGameOverDialog(GameOver over) {
        if (model.gameOverAnnounced()) {
            return;
        }
        model.markGameOverAnnounced();
        refresh();
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (owner == null) {
            return;
        }
        boolean draw = over.winnerColor() == null;
        boolean won = !draw && over.winnerColor() == model.myColor();
        String title = draw ? "DRAW" : won ? "VICTORY" : "DEFEAT";
        Color titleColor = draw ? Theme.INK
                : Theme.playerTextColor(won ? myColor() : opponentColor());
        String wayText = switch (over.way()) {
            case ELIMINATION -> "By elimination";
            case FORFEIT -> "By forfeit";
            case DRAW -> "Draw — 60-turn limit reached";
        };
        int turnNumber = model.view() == null ? 0 : model.view().startedTurns();

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
        JLabel subtitleLabel = new JLabel(wayText + " — turn " + turnNumber, SwingConstants.CENTER);
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
        dialog.setVisible(true);
    }

    private String detailText(GameOver over) {
        if (model.isPracticeMode()) {
            return "Practice match concluded.";
        }
        if (model.me() == null) {
            return "New ELO — you: " + myEloAfter(over) + ", opponent: " + opponentEloAfter(over);
        }
        int delta = myEloAfter(over) - model.me().elo();
        String green = String.format("#%06X", Theme.GREEN.darker().getRGB() & 0xFFFFFF);
        String red = String.format("#%06X", Theme.VERMILION.getRGB() & 0xFFFFFF);
        String mine = (delta >= 0 ? "+" : "") + delta;
        String theirs = (-delta >= 0 ? "+" : "") + -delta;
        return "<html><div style='text-align:center'>New ELO — you: <b>" + myEloAfter(over)
                + "</b> <font color=" + green + ">" + mine + "</font> · opponent: <b>"
                + opponentEloAfter(over) + "</b> <font color=" + red + ">" + theirs
                + "</font></div></html>";
    }

    private int myEloAfter(GameOver over) {
        return model.myColor() == PlayerColor.BLUE ? over.blueEloAfter() : over.redEloAfter();
    }

    private int opponentEloAfter(GameOver over) {
        return model.myColor() == PlayerColor.BLUE ? over.redEloAfter() : over.blueEloAfter();
    }

}

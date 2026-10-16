package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.ClientSettings;
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
import java.awt.Rectangle;
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
 * mana, a shared visual draw pile, and floating action buttons.
 */
public final class GamePanel extends ValleyMenuPanel {

    private final ClientModel model;
    private final ClientSettings settings;

    private final BoardPanel boardPanel = new BoardPanel();
    private final JLabel turnLabel = MatchStyle.label("", 13f);
    private final JLabel turnCountLabel = MatchStyle.label("TURN 01", 10f);
    private final PlayerBadgePanel ownBadge = new PlayerBadgePanel(true);
    private final PlayerBadgePanel opponentBadge = new PlayerBadgePanel(false);
    private final DeckStackPanel drawPile = new DeckStackPanel();
    private final DeckDrawOverlay drawOverlay;
    private final CardPlayOverlay playOverlay = new CardPlayOverlay(this::refresh);
    private final PlayedCardArea ownPlay = new PlayedCardArea("YOU PLAYED");
    private final PlayedCardArea enemyPlay = new PlayedCardArea("OPPONENT PLAYED");
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
    private final JButton dirForward = MatchStyle.action("Forward");
    private final JButton dirBackward = MatchStyle.action("Back");
    private final JButton dirLeft = MatchStyle.action("Left");
    private final JButton dirRight = MatchStyle.action("Right");
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
    private static final double OPPONENT_CARD_SCALE = 0.48;
    private static final int SIDEBAR_WIDTH = 228;

    private ForesightDialog foresightDialog;
    private String foresightMatchId;
    private long foresightChoiceId;

    private PlayerColor myColor() {
        return model.myColor();
    }

    private PlayerColor opponentColor() {
        return myColor() == null ? null : myColor().opponent();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setColor(new Color(12, 18, 20, 70));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
    }

    /** Builds the board, public opponent hand, and unified private hand. */
    public GamePanel(ClientModel model) {
        this(model, new ClientSettings(), new DeckDrawOverlay());
    }

    /** Uses the same scene preference as the login and lobby. */
    public GamePanel(ClientModel model, ClientSettings settings) {
        this(model, settings, new DeckDrawOverlay());
    }

    // Tests inject a visual clock without changing the model or authoritative rules.
    GamePanel(ClientModel model, DeckDrawOverlay drawOverlay) {
        this(model, new ClientSettings(), drawOverlay);
    }

    private GamePanel(ClientModel model, ClientSettings settings, DeckDrawOverlay drawOverlay) {
        super(model, Screen.GAME, settings);
        this.model = model;
        this.settings = settings;
        this.drawOverlay = drawOverlay;
        setLayout(new BorderLayout(12, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        setBackground(Theme.BG);

        // Top opponent band
        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.setOpaque(false);
        opponentCards.setOpaque(false);
        north.add(opponentCards, BorderLayout.CENTER);

        // The complete hand has its own space so hovered cards cannot hide board squares.
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setOpaque(false);
        center.add(north, BorderLayout.NORTH);
        center.add(buildBoardStage(), BorderLayout.CENTER);
        center.add(buildBottomControls(), BorderLayout.SOUTH);
        JPanel scene = new JPanel(new BorderLayout(12, 6));
        scene.setOpaque(false);
        scene.add(buildTurnControls(), BorderLayout.WEST);
        scene.add(center, BorderLayout.CENTER);
        scene.add(buildRightColumn(), BorderLayout.EAST);
        add(DeckDrawOverlay.wrap(DeckDrawOverlay.wrap(scene, drawOverlay), playOverlay), BorderLayout.CENTER);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                cancelDraws(); playOverlay.cancel(); refresh();
            }
        });

        boardPanel.onClick(sq -> {
            if (playOverlay.busy()) return;
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.boardClicked(sq);
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSelection");
        getActionMap().put("clearSelection", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (playOverlay.busy()) return;
                cancelDraws();
                playOverlay.returnSelection();
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
                    cancelPresentation();
                ownPlay.clear(); enemyPlay.clear();
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
        if (model.screen() != Screen.GAME || model.currentSync().paused()) return;
        presentPlayedCards(priorView, currentView, events);
        if (model.reducedMotion() || currentView.outcome() != null) return;
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

    private void presentPlayedCards(PlayerView priorView, PlayerView currentView, List<PublicEvent> events) {
        playOverlay.setReducedMotion(model.reducedMotion() || currentView.outcome() != null);
        layoutTree(this);
        for (PublicEvent event : events) {
            if (event instanceof PublicEvent.MovementPlayed played) {
                boolean own = played.actor() == currentView.viewer();
                var priorMoves = played.actor() == PlayerColor.BLUE ? priorView.blueMovementHand() : priorView.redMovementHand();
                var card = new CardPanel(CardDeck.cardById(played.cardId()), played.actor(), false, null);
                card.setSize(220, 280); card.setCardScale(1);
                playOverlay.play("move:" + played.cardId(), DeckDrawOverlay.snapshot(card), null,
                        playSource(own, true, priorMoves.indexOf(played.cardId())), own ? ownPlay : enemyPlay);
            } else if (event instanceof PublicEvent.SkillCommitted committed) {
                boolean own = committed.actor() == currentView.viewer();
                var card = new SkillCardPanel(committed.skill(), priorView.rules(), committed.actor(), 5,
                        TurnPhase.SKILL, true, false, null);
                card.setSize(220, 280);
                int slot = own ? priorView.ownSkillHand().indexOf(committed.skill()) : 0;
                playOverlay.play("skill:" + committed.skill().instanceId(), DeckDrawOverlay.snapshot(card),
                        own ? null : drawPile.backImage(), playSource(own, false, slot), own ? ownPlay : enemyPlay);
            }
        }

    }

    private Rectangle playSource(boolean own, boolean movement, int slot) {
        List<? extends JComponent> cards = movement ? (own ? myCardPanels : opponentMovementPanels)
                : (own ? mySkillPanels : opponentSkillPanels);
        if (!cards.isEmpty()) {
            JComponent card = cards.get(Math.max(0, Math.min(slot, cards.size() - 1)));
            if (card.getWidth() > 0) return SwingUtilities.convertRectangle(card.getParent(), card.getBounds(), playOverlay);
        }
        return SwingUtilities.convertRectangle(hand, new Rectangle(hand.getWidth() / 2, 60, 180, 220), playOverlay);
    }

    private JComponent buildBoardStage() {
        JPanel stage = new JPanel(null) {
            @Override public void doLayout() {
                int slotWidth = Math.max(64, Math.min(150, getWidth() / 9));
                boardPanel.setBounds(slotWidth + 12, 0, Math.max(1, getWidth() - 2 * slotWidth - 24), getHeight());
                boardPanel.computeGeometry();
                int rim = Math.max(3, Math.min(16, boardPanel.cell() / 4));
                int left = boardPanel.getX() + boardPanel.originX() - rim;
                int right = boardPanel.getX() + boardPanel.originX() + 7 * boardPanel.cell() + rim;
                int height = Math.min(getHeight(), (int) (slotWidth * 1.4) + 28);
                int top = Math.max(0, (getHeight() - height) / 2);
                ownPlay.setBounds(Math.max(0, left - slotWidth - 8), top, slotWidth, height);
                enemyPlay.setBounds(Math.min(getWidth() - slotWidth, right + 8), top, slotWidth, height);
            }
        };
        stage.setOpaque(false);
        stage.add(boardPanel); stage.add(ownPlay); stage.add(enemyPlay);
        return stage;
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
                DeckStackPanel deck = drawPile;
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
        int handHeight = Math.max(284, Math.min(330, (int) (getHeight() * .32)));
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
                CardPanel panel = new CardPanel(card, view.viewer(), false, null);
                playOverlay.bind(panel, "move:" + id, settings::clickToSelectCards,
                        () -> model.canSkipMovement() && model.inFlightRequest() == null && !model.currentSync().paused(),
                        () -> { cancelDraws(); model.cardClicked(id); });
                panel.setReducedMotion(model.reducedMotion());
                panel.setCardScale(.76);
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
                        instance.instanceId().equals(model.selectedSkillInstanceId()), null);
                playOverlay.bind(panel, "skill:" + instance.instanceId(), settings::clickToSelectCards,
                        () -> model.myTurn() && model.view() != null && model.view().phase() == TurnPhase.SKILL
                                && model.inFlightRequest() == null && !model.currentSync().paused(),
                        () -> { cancelDraws(); model.skillClicked(instance.instanceId()); });
                panel.setReducedMotion(model.reducedMotion());
                mySkillPanels.add(panel);
                cards.add(panel);
            }
        }

        hand.setReducedMotion(model.reducedMotion());
        hand.setCards(cards);
    }

    /** Local text plates preserve contrast without bleaching the entire landscape. */
    private JPanel buildBottomControls() {
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(hand, BorderLayout.CENTER);
        return bottom;
    }

    /** Keeps turn actions in the unused left margin, freeing height for the board. */
    private JPanel buildTurnControls() {
        JPanel rail = new JPanel(new BorderLayout(0, 12));
        rail.setOpaque(false);
        int width = 196;
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
            if (playOverlay.busy()) return;
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.skipMovement();
        });
        actions.add(skipMovementButton);
        actions.add(Box.createVerticalStrut(10));

        castSkillButton.addActionListener(e -> {
            if (playOverlay.busy()) return;
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
            if (playOverlay.busy()) return;
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.selectDirection(relativeToCanonical(RelativeDirection.FORWARD, myColor()));
        });
        dirBackward.addActionListener(e -> {
            if (playOverlay.busy()) return;
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.selectDirection(relativeToCanonical(RelativeDirection.BACKWARD, myColor()));
        });
        dirLeft.addActionListener(e -> {
            if (playOverlay.busy()) return;
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.selectDirection(relativeToCanonical(RelativeDirection.LEFT, myColor()));
        });
        dirRight.addActionListener(e -> {
            if (playOverlay.busy()) return;
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
            if (playOverlay.busy()) return;
            cancelDraws();
            boardPanel.finishMoveAnimation();
            model.endTurn();
        });
        actions.add(endTurnButton);

        rail.add(new PlayedCardsPanel(model), BorderLayout.CENTER);
        rail.add(actions, BorderLayout.SOUTH);
        return rail;
    }

    private Component buildRightColumn() {
        JPanel column = new JPanel(new BorderLayout(0, 12));
        column.setOpaque(false);
        column.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 84));
        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        JPanel banner = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        banner.setOpaque(false);
        banner.add(turnLabel);
        banner.add(waitPulseDot);
        top.add(banner, BorderLayout.NORTH);
        top.add(opponentBadge, BorderLayout.CENTER);
        column.add(top, BorderLayout.NORTH);

        JPanel decks = new JPanel(new GridLayout(1, 1, 8, 0));
        decks.setOpaque(false);
        // Compact deck art leaves space for conversation at the minimum window height.
        decks.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 84));
        decks.add(drawPile);
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
        JButton menu = MatchStyle.action("Menu");
        menu.addActionListener(event -> model.leaveToLobby());
        controls.add(menu);
        resignButton = MatchStyle.action("Resign");
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
        playOverlay.setReducedMotion(model.reducedMotion());
        var handCards = new ArrayList<JComponent>();
        handCards.addAll(myCardPanels); handCards.addAll(mySkillPanels);
        playOverlay.syncSelection(handCards, model.inFlightRequest() != null);

        // Action buttons
        skipMovementButton.setEnabled(model.canSkipMovement() && !playOverlay.busy());
        endTurnButton.setEnabled(model.canEndTurn() && !playOverlay.busy());
        castSkillButton.setVisible(model.selectedSkillInstanceId() != null);
        castSkillButton.setEnabled(model.canCastSkill() && !model.currentSync().paused() && !playOverlay.busy());

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

        // Turn & Phase Banner
        String banner = bannerText(view, myColor);
        turnLabel.setText(banner);

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

    private void updateDirectionButton(JButton button, RelativeDirection rel) {
        Direction cDir = relativeToCanonical(rel, myColor());
        boolean selectable = model.skillOptions() != null
                && model.skillOptions().selectableDirections().contains(cDir);
        button.setEnabled(selectable && !playOverlay.busy());
        boolean isSelected = model.skillDraft() != null && model.skillDraft().direction() == cDir;
        button.setSelected(isSelected);
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
        boardPanel.setToolTipText(text);
        castSkillButton.setToolTipText(model.selectedSkillInstanceId() == null ? null : text);
    }

    private void cancelActionAnimations(boolean preserveReflow) {
        cancelDraws();
        boardPanel.cancelAnimations();
        if (!preserveReflow) hand.cancelAnimations();
        for (CardPanel card : myCardPanels) card.cancelAnimations();
        for (SkillCardPanel card : mySkillPanels) card.cancelAnimations();
    }

    private void cancelPresentation() {
        cancelActionAnimations(false);
        playOverlay.cancel();
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

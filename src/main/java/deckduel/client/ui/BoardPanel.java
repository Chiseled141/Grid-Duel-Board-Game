package deckduel.client.ui;

import deckduel.core.PlayerColor;
import deckduel.core.Square;
import deckduel.core.duel.PlacedPiece;
import deckduel.core.duel.PlayerView;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Responsive 7x7 board canvas for Deck Duel.
 * Drawn from the viewer's side (own home row at the bottom).
 * Seven identical pieces per side; no temple arch or Master distinctions.
 */
public final class BoardPanel extends JComponent {

    public static final int BOARD_SIZE = deckduel.core.duel.Board.SIZE; // 7
    private static final int MIN_CELL = 26;
    private static final double GRID_SHARE = 0.96;

    private List<PlacedPiece> pieces = List.of();
    private deckduel.core.duel.ObjectiveLayout objectives;
    private PlayerColor viewColor = PlayerColor.BLUE;
    private Square selectedSquare;
    private List<Square> targets = List.of();
    private Consumer<Square> clickHandler;

    private int cell = 54;
    private int originX;
    private int originY;

    private Square lastMoveFrom;
    private Square lastMoveTo;
    private Square hoveredSquare;
    private boolean reducedMotion;

    // Targeting feedback
    private java.util.Set<Integer> selectablePieceIds = java.util.Set.of();
    private List<Integer> selectedPieceIds = List.of();
    private java.util.Set<Square> selectableSquares = java.util.Set.of();
    private List<Square> selectedDestinations = List.of();

    // Multi-piece and batch animations
    public record MovingPiece(int pieceId, Square from, Square to, PlayerColor color) {}
    public record EliminationEffect(Square square, Color color, long startMillis, long durationMillis) {}
    public record SkillCue(String name, deckduel.core.duel.SkillCategory category, PlayerColor actor, long startMillis, long durationMillis) {}

    private final List<MovingPiece> movingPieces = new ArrayList<>();
    private long moveAnimStart;
    private static final long MOVE_ANIM_MS = 480;
    private final java.util.function.LongSupplier animationClock;

    private final List<EliminationEffect> eliminationEffects = new ArrayList<>();
    private static final long ELIM_ANIM_MS = 380;

    private final List<SkillCue> skillCues = new ArrayList<>();
    private static final long SKILL_CUE_MS = 520;

    private record SkillImpact(String skillId, PlayerColor actor, deckduel.core.duel.SkillCategory category,
                               List<MovingPiece> moves, List<Square> targets, long started) { }
    private final List<SkillImpact> skillImpacts = new ArrayList<>();

    private Timer animationTimer;
    private float selectionPulse;
    private Timer selectionPulseTimer;

    /** Creates the board with smooth, transient movement feedback. */
    public BoardPanel() { this(System::currentTimeMillis); }

    BoardPanel(java.util.function.LongSupplier animationClock) {
        this.animationClock = Objects.requireNonNull(animationClock);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                Square square = squareAt(event.getX(), event.getY());
                if (square != null && clickHandler != null) {
                    clickHandler.accept(square);
                }
            }
        });
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
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

    public void computeGeometry() {
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        int canvasSize = Math.max(1, Math.min(width, height) - 12);
        int gridPx = (int) (canvasSize * GRID_SHARE);
        cell = Math.max(1, gridPx / BOARD_SIZE);
        int span = cell * BOARD_SIZE;
        originX = (width - span) / 2;
        originY = (height - span) / 2;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(2 * MIN_CELL * BOARD_SIZE + 60,
                2 * MIN_CELL * BOARD_SIZE + 60);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(2 * MIN_CELL * BOARD_SIZE, 2 * MIN_CELL * BOARD_SIZE);
    }

    /** Sets the state to render from the player's view. */
    public void setView(PlayerView view) {
        if (view == null) {
            this.pieces = List.of();
            this.objectives = null;
            this.viewColor = PlayerColor.BLUE;
        } else {
            this.pieces = view.pieces();
            this.objectives = view.objectives();
            this.viewColor = view.viewer();
        }
        repaint();
    }

    public void setPieces(List<PlacedPiece> pieces, PlayerColor viewColor) {
        this.pieces = pieces == null ? List.of() : List.copyOf(pieces);
        this.viewColor = viewColor == null ? PlayerColor.BLUE : viewColor;
        repaint();
    }

    public void setSelection(Square selectedSquare, List<Square> targets) {
        this.selectedSquare = selectedSquare;
        this.targets = targets == null ? List.of() : List.copyOf(targets);
        updateSelectionPulse();
        repaint();
    }

    public void clearHighlights() {
        setSelection(null, List.of());
    }

    private void updateSelectionPulse() {
        boolean active = selectedSquare != null && !reducedMotion;
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

    public void setLastMove(Square from, Square to) {
        this.lastMoveFrom = from;
        this.lastMoveTo = to;
        repaint();
    }

    public void onClick(Consumer<Square> clickHandler) {
        this.clickHandler = clickHandler;
    }

    private static volatile int pieceStyle = 1;

    public static void setDefaultPieceStyle(int style) {
        pieceStyle = Math.max(1, Math.min(3, style));
    }

    private static Image pieceSprite(PlayerColor color) {
        return MedievalPieces.image(color, pieceStyle);
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
        updateSelectionPulse();
        if (reducedMotion) {
            cancelAnimations();
        }
    }

    public boolean reducedMotion() {
        return reducedMotion;
    }

    public void setTargetingFeedback(java.util.Set<Integer> selectablePieceIds,
                                    List<Integer> selectedPieceIds,
                                    java.util.Set<Square> selectableSquares,
                                    List<Square> selectedDestinations) {
        this.selectablePieceIds = selectablePieceIds == null ? java.util.Set.of() : java.util.Set.copyOf(selectablePieceIds);
        this.selectedPieceIds = selectedPieceIds == null ? List.of() : List.copyOf(selectedPieceIds);
        this.selectableSquares = selectableSquares == null ? java.util.Set.of() : java.util.Set.copyOf(selectableSquares);
        this.selectedDestinations = selectedDestinations == null ? List.of() : List.copyOf(selectedDestinations);
        updateSelectionPulse();
        repaint();
    }

    public void clearTargetingFeedback() {
        this.selectablePieceIds = java.util.Set.of();
        this.selectedPieceIds = List.of();
        this.selectableSquares = java.util.Set.of();
        this.selectedDestinations = List.of();
        repaint();
    }

    private Runnable moveAnimCallback;

    public void animateMoves(List<MovingPiece> moves, Runnable onComplete) {
        if (reducedMotion || moves == null || moves.isEmpty()) {
            movingPieces.clear();
            repaint();
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        movingPieces.clear();
        movingPieces.addAll(moves);
        this.moveAnimCallback = onComplete;
        moveAnimStart = animationClock.getAsLong();
        ensureAnimationTimer();
        repaint();
    }

    public void animateMoves(List<MovingPiece> moves) {
        animateMoves(moves, null);
    }

    public void animateMove(Square from, Square to, PlayerColor mover) {
        if (from != null && to != null && mover != null) {
            animateMoves(List.of(new MovingPiece(0, from, to, mover)));
        }
    }

    public void playEliminations(List<Square> squares, PlayerColor byColor) {
        if (reducedMotion || squares == null || squares.isEmpty()) {
            return;
        }
        long now = animationClock.getAsLong();
        Color c = Theme.playerColor(byColor);
        for (Square sq : squares) {
            eliminationEffects.add(new EliminationEffect(sq, c, now, ELIM_ANIM_MS));
        }
        ensureAnimationTimer();
        repaint();
    }

    public void playCaptureEffect(Square square, PlayerColor byColor) {
        if (square != null) {
            playEliminations(List.of(square), byColor);
        }
    }

    public void playSkillCue(String name, deckduel.core.duel.SkillCategory category, PlayerColor actor) {
        if (reducedMotion || name == null) {
            return;
        }
        skillCues.add(new SkillCue(name, category, actor, animationClock.getAsLong(), SKILL_CUE_MS));
        ensureAnimationTimer();
        repaint();
    }

    /** Adds reusable board effects using only accepted public movement and elimination events. */
    public void playSkillImpact(String skillId, PlayerColor actor, deckduel.core.duel.SkillCategory category,
                                List<MovingPiece> moves, List<Square> eliminated) {
        if (reducedMotion || category == null || category == deckduel.core.duel.SkillCategory.RESOURCE) return;
        var targets = new java.util.LinkedHashSet<Square>(eliminated);
        for (MovingPiece move : moves) targets.add(move.to());
        if (targets.isEmpty()) return;
        skillImpacts.add(new SkillImpact(skillId, actor, category, List.copyOf(moves), List.copyOf(targets),
                animationClock.getAsLong()));
        ensureAnimationTimer();
        repaint();
    }

    public void finishMoveAnimation() {
        movingPieces.clear();
        Runnable cb = moveAnimCallback;
        moveAnimCallback = null;
        checkStopAnimationTimer();
        repaint();
        if (cb != null) {
            cb.run();
        }
    }

    /** Discards all effects and callbacks; cancellation must never spawn another effect. */
    public void cancelAnimations() {
        if (animationTimer != null) animationTimer.stop();
        if (selectionPulseTimer != null) { selectionPulseTimer.stop(); selectionPulseTimer = null; }
        selectionPulse = 0f;
        movingPieces.clear();
        eliminationEffects.clear();
        skillCues.clear();
        skillImpacts.clear();
        moveAnimCallback = null;
        repaint();
    }

    @Override public void removeNotify() {
        cancelAnimations();
        super.removeNotify();
    }

    private void ensureAnimationTimer() {
        if (animationTimer == null) {
            animationTimer = new Timer(16, event -> onAnimTick());
        }
        if (!animationTimer.isRunning()) {
            animationTimer.start();
        }
    }

    private void checkStopAnimationTimer() {
        if (movingPieces.isEmpty() && eliminationEffects.isEmpty() && skillCues.isEmpty() && skillImpacts.isEmpty()) {
            if (animationTimer != null) {
                animationTimer.stop();
            }
        }
    }

    private void onAnimTick() {
        long now = animationClock.getAsLong();
        if (!movingPieces.isEmpty() && now - moveAnimStart >= MOVE_ANIM_MS) {
            movingPieces.clear();
            Runnable cb = moveAnimCallback;
            moveAnimCallback = null;
            if (cb != null) {
                cb.run();
            }
        }
        eliminationEffects.removeIf(eff -> now - eff.startMillis() >= eff.durationMillis());
        skillCues.removeIf(cue -> now - cue.startMillis() >= cue.durationMillis());
        skillImpacts.removeIf(impact -> now - impact.started() >= SKILL_CUE_MS);
        checkStopAnimationTimer();
        repaint();
    }

    private boolean isPieceMoving(int pieceId, Square square, PlayerColor color) {
        if (movingPieces.isEmpty()) return false;
        long elapsed = animationClock.getAsLong() - moveAnimStart;
        if (elapsed >= MOVE_ANIM_MS) return false;
        for (MovingPiece mp : movingPieces) {
            if (mp.pieceId() == pieceId) return true;
            if (mp.pieceId() == 0 && mp.color() == color && mp.to().equals(square)) return true;
        }
        return false;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = UiKit.nice(graphics);
        computeGeometry();

        paintBoard(g);
        paintObjectives(g);

        if (lastMoveFrom != null) {
            overlay(g, lastMoveFrom);
        }
        if (lastMoveTo != null) {
            overlay(g, lastMoveTo);
        }

        for (PlacedPiece pp : pieces) {
            if (isPieceMoving(pp.piece().id(), pp.square(), pp.piece().color())) {
                continue;
            }
            drawPiece(g, pp.square().x(), pp.square().y(), pp.piece().color());
        }

        if (!movingPieces.isEmpty()) {
            drawMovingPieces(g);
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

        drawSkillTargetingFeedback(g);

        if (!eliminationEffects.isEmpty()) {
            drawEliminationEffects(g);
        }

        drawSkillImpacts(g);

        if (!skillCues.isEmpty()) {
            drawSkillCues(g);
        }

        g.dispose();
    }

    private void paintObjectives(Graphics2D g) {
        if (objectives == null) return;
        for (Square square : objectives.scoring()) paintObjective(g, square, true);
        for (Square square : objectives.mana()) paintObjective(g, square, false);
    }

    private void paintObjective(Graphics2D parent, Square square, boolean score) {
        Graphics2D g = (Graphics2D) parent.create();
        try {
            int px = displayX(square.x()) * cell + originX;
            int py = displayY(square.y()) * cell + originY;
            g.clipRect(px + 1, py + 1, cell - 2, cell - 2);
            Color accent = score ? new Color(216, 164, 59) : new Color(58, 145, 177);
            // Tint the stone itself; the original tile texture remains visible underneath.
            g.setPaint(new java.awt.GradientPaint(px, py,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 115),
                    px, py + cell, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60)));
            g.fillRect(px + 1, py + 1, cell - 2, cell - 2);
            int edge = Math.max(2, cell / 24);
            for (int inset = edge + 3; inset >= edge; inset--) {
                int alpha = inset == edge ? 220 : 35;
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha));
                g.setStroke(new BasicStroke(inset == edge ? 1.5f : 2f));
                g.drawRect(px + inset, py + inset, cell - 2 * inset - 1, cell - 2 * inset - 1);
            }
        } finally { g.dispose(); }
    }

    /** Objective explanations follow canonical squares even when the board is flipped. */
    @Override public String getToolTipText(MouseEvent event) {
        Square square = squareAt(event.getX(), event.getY());
        if (objectives != null && square != null) {
            if (objectives.scoring().contains(square))
                return "Score square: +1 point at your turn start while occupied. Each gold square scores separately.";
            if (objectives.mana().contains(square))
                return "Mana square: +1 bonus mana at your turn start while occupied, within the mana cap.";
        }
        return super.getToolTipText(event);
    }

    /** Oak frame, brass corner fittings and worn limestone/slate playing squares. */
    private void paintBoard(Graphics2D g) {
        int grid = BOARD_SIZE * cell;
        int rim = Math.max(3, Math.min(16, cell / 4));
        int shadow = Math.max(2, rim / 3);
        int left = originX - rim, top = originY - rim;
        int size = grid + 2 * rim;
        int arc = rim * 2;

        g.setColor(Theme.withAlpha(Theme.SHADOW, 180));
        g.fillRoundRect(left + shadow, top + shadow, size, size, arc, arc);
        g.setPaint(new java.awt.GradientPaint(left, top, new Color(115, 76, 43),
                left + size, top + size, new Color(58, 39, 27)));
        g.fillRoundRect(left, top, size, size, arc, arc);
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(left, top, size, size, arc, arc);
        g.setColor(Theme.BOARD_GOLD);
        g.setStroke(new BasicStroke(1.25f));
        int inlay = Math.max(2, rim / 3);
        g.drawRoundRect(left + inlay, top + inlay, size - 2 * inlay, size - 2 * inlay,
                Math.max(2, arc - inlay), Math.max(2, arc - inlay));

        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                int px = displayX(x) * cell + originX;
                int py = displayY(y) * cell + originY;
                Color tile = (x + y) % 2 == 0 ? new Color(222, 208, 176) : new Color(112, 103, 88);
                g.setColor(tile);
                g.fillRect(px, py, cell, cell);
                // Restrained chisel marks keep the stone texture clear of targeting feedback.
                g.setColor(Theme.withAlpha(Color.WHITE, 40));
                g.setStroke(new BasicStroke(1f));
                g.drawLine(px + 1, py + 1, px + cell - 1, py + 1);
                g.setColor(new Color(55, 42, 28, 22));
                int vein = Math.max(3, cell / 5);
                g.drawLine(px + vein, py + cell - vein, px + cell / 2, py + cell - vein - 2);
                g.drawLine(px + cell / 2, py + cell - vein - 2, px + cell - vein, py + cell - vein);
            }
        }
        g.setColor(Theme.withAlpha(Theme.INK, 100));
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= BOARD_SIZE; i++) {
            int offset = i * cell;
            g.drawLine(originX + offset, originY, originX + offset, originY + grid);
            g.drawLine(originX, originY + offset, originX + grid, originY + offset);
        }
        g.setColor(Theme.BOARD_GOLD);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(originX - 2, originY - 2, grid + 4, grid + 4);
        if (rim >= 8) paintCornerInlays(g, grid, rim);
    }

    /** Matching brass corner brackets stay entirely outside the playable squares. */
    private void paintCornerInlays(Graphics2D g, int grid, int rim) {
        int inset = Math.max(3, rim / 2);
        int length = Math.max(4, rim - 2);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int horizontal : new int[]{-1, 1}) {
            for (int vertical : new int[]{-1, 1}) {
                int x = horizontal < 0 ? originX - inset : originX + grid + inset;
                int y = vertical < 0 ? originY - inset : originY + grid + inset;
                var corner = new java.awt.geom.Path2D.Float();
                corner.moveTo(x, y - vertical * length);
                corner.lineTo(x, y);
                corner.lineTo(x - horizontal * length, y);
                g.draw(corner);
            }
        }
    }

    private void drawPiece(Graphics2D g, int x, int y, PlayerColor color) {
        Image sprite = pieceSprite(color);
        int px = displayX(x) * cell + originX;
        int py = displayY(y) * cell + originY;
        int inset = (int) (cell * 0.08);
        g.drawImage(sprite, px + inset, py + inset, cell - 2 * inset, cell - 2 * inset, null);
    }

    private void drawMovingPieces(Graphics2D g) {
        long elapsed = animationClock.getAsLong() - moveAnimStart;
        double progress = Math.min(1.0, elapsed / (double) MOVE_ANIM_MS);
        double eased = progress * progress * (3 - 2 * progress);

        for (MovingPiece mp : movingPieces) {
            Image sprite = pieceSprite(mp.color());

            int fromX = displayX(mp.from().x()) * cell + originX + cell / 2;
            int fromY = displayY(mp.from().y()) * cell + originY + cell / 2;
            int toX = displayX(mp.to().x()) * cell + originX + cell / 2;
            int toY = displayY(mp.to().y()) * cell + originY + cell / 2;

            int cx = (int) (fromX + (toX - fromX) * eased);
            int cy = (int) (fromY + (toY - fromY) * eased - Math.sin(progress * Math.PI) * cell * 0.16);

            int inset = (int) (cell * 0.08);
            int size = cell - 2 * inset;
            g.setColor(new Color(24, 15, 7, 48));
            g.fillOval(cx - size / 3, (int) (fromY + (toY - fromY) * eased) + size / 3,
                    size * 2 / 3, Math.max(2, size / 8));
            g.drawImage(sprite, cx - size / 2, cy - size / 2, size, size, null);
        }
    }

    private void drawTargetDot(Graphics2D g, Square target) {
        int cx = displayX(target.x()) * cell + originX + cell / 2;
        int cy = displayY(target.y()) * cell + originY + cell / 2;
        boolean isOccupiedEnemy = pieces.stream()
                .anyMatch(pp -> pp.square().equals(target) && pp.piece().color() != viewColor);
        boolean hovered = target.equals(hoveredSquare);

        Color accent = Theme.playerColor(viewColor);
        if (hovered) {
            accent = UiKit.lighten(accent);
        }
        g.setColor(new Color(accent.getRed(), accent.getGreen(),
                accent.getBlue(), hovered ? 255 : 190));

        if (!isOccupiedEnemy) {
            int dot = hovered ? Math.max(16, cell / 3) : Math.max(12, cell / 4);
            g.fillOval(cx - dot / 2, cy - dot / 2, dot, dot);
            g.setColor(Theme.INK);
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(cx - dot / 2, cy - dot / 2, dot, dot);
        } else {
            g.setStroke(new BasicStroke(hovered ? 4f : 3f));
            int radius = cell / 2 - (hovered ? 4 : 6);
            g.drawOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
            g.drawOval(cx - radius + 4, cy - radius + 4, 2 * radius - 8, 2 * radius - 8);
        }
    }

    private void drawSelectionRing(Graphics2D g, Square square) {
        int px = displayX(square.x()) * cell + originX;
        int py = displayY(square.y()) * cell + originY;
        g.setColor(Theme.withAlpha(Theme.playerColor(viewColor),
                215 + (int) (40 * selectionPulse)));
        g.setStroke(new BasicStroke(4f));
        g.drawOval(px + 3, py + 3, cell - 6, cell - 6);
    }

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
        g.setColor(new Color(Theme.LAST_MOVE.getRed(), Theme.LAST_MOVE.getGreen(),
                Theme.LAST_MOVE.getBlue(), 170));
        g.fillRect(px, py, cell, cell);
        g.setColor(Theme.AMBER);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(px + 1, py + 1, cell - 2, cell - 2);
    }

    private void drawSkillTargetingFeedback(Graphics2D g) {
        // Highlight selectable piece IDs
        if (!selectablePieceIds.isEmpty()) {
            for (PlacedPiece pp : pieces) {
                if (selectablePieceIds.contains(pp.piece().id())) {
                    int px = displayX(pp.square().x()) * cell + originX;
                    int py = displayY(pp.square().y()) * cell + originY;
                    g.setColor(new Color(64, 160, 190, 190 + (int) (65 * selectionPulse)));
                    g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            10f, reducedMotion ? null : new float[]{6f, 4f}, 0f));
                    g.drawRoundRect(px + 3, py + 3, cell - 6, cell - 6, 12, 12);
                }
            }
        }

        // Highlight selected piece IDs from draft
        if (!selectedPieceIds.isEmpty()) {
            for (int i = 0; i < selectedPieceIds.size(); i++) {
                int pid = selectedPieceIds.get(i);
                for (PlacedPiece pp : pieces) {
                    if (pp.piece().id() == pid) {
                        int px = displayX(pp.square().x()) * cell + originX;
                        int py = displayY(pp.square().y()) * cell + originY;
                        g.setColor(Theme.AMBER);
                        g.setStroke(new BasicStroke(3.5f));
                        g.drawRoundRect(px + 2, py + 2, cell - 4, cell - 4, 12, 12);
                        if (selectedPieceIds.size() > 1) {
                            drawOrderBadge(g, px + cell - 18, py + 3, i + 1);
                        }
                        break;
                    }
                }
            }
        }

        // Highlight selectable empty squares
        if (!selectableSquares.isEmpty()) {
            for (Square sq : selectableSquares) {
                int cx = displayX(sq.x()) * cell + originX + cell / 2;
                int cy = displayY(sq.y()) * cell + originY + cell / 2;
                boolean hovered = sq.equals(hoveredSquare);
                int r = hovered ? Math.max(14, cell / 3) : Math.max(10, cell / 4);
                g.setColor(new Color(50, 140, 180, hovered ? 230 : 160));
                g.fillOval(cx - r, cy - r, r * 2, r * 2);
                g.setColor(Theme.INK);
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval(cx - r, cy - r, r * 2, r * 2);
            }
        }

        // Highlight selected destination squares from draft
        if (!selectedDestinations.isEmpty()) {
            for (int i = 0; i < selectedDestinations.size(); i++) {
                Square sq = selectedDestinations.get(i);
                int cx = displayX(sq.x()) * cell + originX + cell / 2;
                int cy = displayY(sq.y()) * cell + originY + cell / 2;
                drawDestinationDiamond(g, cx, cy, i + 1, selectedDestinations.size() > 1);
            }
        }
    }

    private void drawOrderBadge(Graphics2D g, int x, int y, int num) {
        g.setColor(Theme.INK);
        g.fillOval(x, y, 16, 16);
        g.setColor(Theme.AMBER);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(x, y, 16, 16);
        g.setColor(Theme.CREAM);
        g.setFont(Theme.display(10f));
        String s = Integer.toString(num);
        int tw = g.getFontMetrics().stringWidth(s);
        g.drawString(s, x + 8 - tw / 2, y + 12);
    }

    private void drawDestinationDiamond(Graphics2D g, int cx, int cy, int num, boolean showNum) {
        int r = Math.max(14, cell / 3);
        int[] xs = {cx, cx + r, cx, cx - r};
        int[] ys = {cy - r, cy, cy + r, cy};
        g.setColor(new Color(Theme.AMBER.getRed(), Theme.AMBER.getGreen(), Theme.AMBER.getBlue(), 180));
        g.fillPolygon(xs, ys, 4);
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(2f));
        g.drawPolygon(xs, ys, 4);
        if (showNum) {
            drawOrderBadge(g, cx + r - 8, cy - r, num);
        }
    }

    private void drawEliminationEffects(Graphics2D g) {
        long now = animationClock.getAsLong();
        for (EliminationEffect eff : eliminationEffects) {
            long elapsed = now - eff.startMillis();
            if (elapsed > eff.durationMillis()) continue;
            double p = Math.min(1.0, elapsed / (double) eff.durationMillis());
            int cx = displayX(eff.square().x()) * cell + originX + cell / 2;
            int cy = displayY(eff.square().y()) * cell + originY + cell / 2;
            int radius = (int) (cell * 0.25 + cell * 0.40 * p);
            int alpha = (int) (220 * (1.0 - p));
            Color c = eff.color();
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            g.setStroke(new BasicStroke(3.5f * (float) (1.0 - p * 0.6)));
            g.drawOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
            int slash = (int) (radius * 0.55);
            g.drawLine(cx - slash, cy - slash, cx + slash, cy + slash);
            g.drawLine(cx - slash, cy + slash, cx + slash, cy - slash);
        }
    }

    private void drawSkillImpacts(Graphics2D parent) {
        for (SkillImpact impact : skillImpacts) {
            double p = Math.max(0, (animationClock.getAsLong() - impact.started()) / (double) SKILL_CUE_MS);
            if (p >= 1) continue;
            Graphics2D g = (Graphics2D) parent.create();
            try {
                g.clipRect(originX, originY, cell * BOARD_SIZE, cell * BOARD_SIZE);
                Color accent = switch (impact.category()) {
                    case MOVEMENT -> new Color(111, 224, 244);
                    case ATTACK -> new Color(255, 170, 86);
                    case CONTROL -> new Color(192, 151, 255);
                    case RESOURCE -> Theme.AMBER;
                };
                float opacity = (float) (Math.sin(Math.PI * p) * 0.9);
                g.setComposite(java.awt.AlphaComposite.SrcOver.derive(opacity));
                if (impact.category() == deckduel.core.duel.SkillCategory.MOVEMENT) {
                    for (MovingPiece move : impact.moves()) drawSkillTrail(g, move, p, accent);
                }
                for (Square square : impact.targets()) {
                    double x = originX + (displayX(square.x()) + 0.5) * cell;
                    double y = originY + (displayY(square.y()) + 0.5) * cell;
                    double radius = cell * (0.16 + 0.52 * p);
                    g.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(x, y),
                            (float) radius, new float[]{0f, 1f},
                            new Color[]{Theme.withAlpha(accent, 110), Theme.withAlpha(accent, 0)}));
                    g.fill(new java.awt.geom.Ellipse2D.Double(x-radius, y-radius, radius*2, radius*2));
                    g.setColor(accent);
                    g.setStroke(new BasicStroke(Math.max(1f, cell / 28f)));
                    g.draw(new java.awt.geom.Ellipse2D.Double(x-radius, y-radius, radius*2, radius*2));
                    if (impact.category() == deckduel.core.duel.SkillCategory.ATTACK) {
                        drawSkillWeapon(g, impact, x, y, p);
                    } else if (impact.category() == deckduel.core.duel.SkillCategory.CONTROL) {
                        g.rotate(p * Math.PI / 2, x, y);
                        for (int i = 0; i < 4; i++) {
                            g.draw(new java.awt.geom.Rectangle2D.Double(x-radius*.7, y-radius*.7, radius*1.4, radius*1.4));
                            g.rotate(Math.PI / 2, x, y);
                            g.fill(new java.awt.geom.Ellipse2D.Double(x+radius-2, y-2, 4, 4));
                        }
                        g.rotate(-p * Math.PI / 2, x, y);
                    }
                }
            } finally { g.dispose(); }
        }
    }

    private void drawSkillWeapon(Graphics2D parent, SkillImpact impact, double x, double y, double p) {
        Graphics2D g = (Graphics2D) parent.create();
        try {
            g.translate(x, y);
            double strike = Math.max(0, Math.min(1, (p - .15) / .5));
            strike = strike * strike * (3 - 2 * strike);
            boolean spear = "pierce".equals(impact.skillId()) || "crossfire".equals(impact.skillId());
            // Weapons face the actor's advance direction even when the viewer flips the board.
            double facing = impact.actor() == viewColor ? -Math.PI / 2 : Math.PI / 2;
            g.rotate(facing);
            if (spear) {
                drawSpear(g, strike);
                if ("crossfire".equals(impact.skillId())) {
                    g.rotate(Math.PI);
                    drawSpear(g, strike);
                }
            } else {
                g.setColor(new Color(255, 202, 119, 105));
                g.setStroke(new BasicStroke(Math.max(3f, cell / 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double size = cell * 1.5;
                g.draw(new java.awt.geom.Arc2D.Double(-size/2, -size/2, size, size,
                        35, -150 * strike, java.awt.geom.Arc2D.OPEN));
                g.rotate(-1.3 + 2.6 * strike);
                g.scale(cell, cell);
                // Blade, central bevel, brass crossguard and leather grip remain readable at small sizes.
                g.setPaint(new java.awt.GradientPaint(0f, -.10f, new Color(248, 252, 255),
                        0f, .10f, new Color(104, 142, 164)));
                var blade = new java.awt.geom.Path2D.Double();
                blade.moveTo(-.48, -.09); blade.lineTo(.46, -.065);
                blade.lineTo(.75, 0); blade.lineTo(.46, .065); blade.lineTo(-.48, .09); blade.closePath();
                g.fill(blade);
                g.setColor(new Color(52, 72, 84)); g.setStroke(new BasicStroke(.018f)); g.draw(blade);
                g.setColor(Color.WHITE); g.draw(new java.awt.geom.Line2D.Double(-.44, 0, .68, 0));
                g.setColor(new Color(91, 48, 28));
                g.fill(new java.awt.geom.RoundRectangle2D.Double(-.85, -.055, .34, .11, .04, .04));
                g.setColor(new Color(236, 191, 92));
                g.fill(new java.awt.geom.RoundRectangle2D.Double(-.54, -.22, .08, .44, .04, .04));
                g.fill(new java.awt.geom.Ellipse2D.Double(-.92, -.085, .15, .17));
            }
        } finally { g.dispose(); }
    }

    private void drawSpear(Graphics2D parent, double strike) {
        Graphics2D g = (Graphics2D) parent.create();
        try {
            g.scale(cell, cell);
            g.translate(-1.15 + 1.35 * strike, 0);
            g.setColor(new Color(255, 213, 129, 95));
            g.fill(new java.awt.geom.RoundRectangle2D.Double(-1.6, -.10, 1.8, .20, .15, .15));
            g.setColor(new Color(99, 57, 32));
            g.fill(new java.awt.geom.RoundRectangle2D.Double(-1.35, -.035, 1.25, .07, .03, .03));
            g.setColor(new Color(231, 184, 87));
            g.fill(new java.awt.geom.Rectangle2D.Double(-.23, -.065, .14, .13));
            var head = new java.awt.geom.Path2D.Double();
            head.moveTo(-.12, -.11); head.lineTo(.30, 0); head.lineTo(-.12, .11);
            head.lineTo(-.22, 0); head.closePath();
            g.setPaint(new java.awt.GradientPaint(0f, -.1f, Color.WHITE, 0f, .1f, new Color(103, 145, 166)));
            g.fill(head);
            g.setColor(new Color(52, 72, 84)); g.setStroke(new BasicStroke(.015f)); g.draw(head);
            g.setColor(Color.WHITE); g.draw(new java.awt.geom.Line2D.Double(-.17, 0, .26, 0));
        } finally { g.dispose(); }
    }

    private void drawSkillTrail(Graphics2D g, MovingPiece move, double p, Color accent) {
        double eased = Math.min(1, p * SKILL_CUE_MS / MOVE_ANIM_MS);
        eased = eased * eased * (3 - 2 * eased);
        double x = originX + (displayX(move.from().x()) + .5) * cell;
        double y = originY + (displayY(move.from().y()) + .5) * cell;
        double endX = originX + (displayX(move.to().x()) + .5) * cell;
        double endY = originY + (displayY(move.to().y()) + .5) * cell;
        double headX = x + (endX-x) * eased;
        double headY = y + (endY-y) * eased - Math.sin(Math.PI * Math.min(1, p * SKILL_CUE_MS / MOVE_ANIM_MS)) * cell * .16;
        g.setColor(Theme.withAlpha(accent, 70));
        g.setStroke(new BasicStroke(Math.max(3f, cell / 5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(x, y, headX, headY));
        g.setColor(accent);
        g.setStroke(new BasicStroke(Math.max(1f, cell / 24f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(x, y, headX, headY));
    }

    private void drawSkillCues(Graphics2D g) {
        long now = animationClock.getAsLong();
        for (SkillCue cue : skillCues) {
            long elapsed = now - cue.startMillis();
            if (elapsed > cue.durationMillis()) continue;
            double p = Math.min(1.0, elapsed / (double) cue.durationMillis());
            float alpha = (float) (p < 0.2 ? (p / 0.2) : (p > 0.7 ? (1.0 - p) / 0.3 : 1.0));
            alpha = Math.max(0f, Math.min(1f, alpha));

            int bw = Math.min(240, cell * 4);
            int bh = 34;
            int bx = originX + (BOARD_SIZE * cell - bw) / 2;
            int by = originY + (int) (cell * 2.8 - p * cell * 0.3);

            g.setColor(new Color(24, 20, 16, (int) (215 * alpha)));
            g.fillRoundRect(bx, by, bw, bh, 14, 14);

            Color catColor = switch (cue.category()) {
                case MOVEMENT -> new Color(41, 85, 106);
                case ATTACK -> new Color(178, 64, 40);
                case CONTROL -> new Color(194, 138, 42);
                case RESOURCE -> new Color(52, 110, 80);
            };
            g.setColor(new Color(catColor.getRed(), catColor.getGreen(), catColor.getBlue(), (int) (240 * alpha)));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(bx, by, bw, bh, 14, 14);

            g.setColor(new Color(245, 238, 220, (int) (255 * alpha)));
            g.setFont(Theme.display(13f));
            String text = cue.name().toUpperCase();
            int tw = g.getFontMetrics().stringWidth(text);
            g.drawString(text, bx + (bw - tw) / 2, by + 22);
        }
    }

    /**
     * Converts mouse coordinates to canonical square.
     * Blue viewer: home row y=0 at bottom.
     * Red viewer: home row y=6 at bottom, flipped horizontally and vertically.
     */
    public Square squareAt(int mouseX, int mouseY) {
        if (getWidth() < 100 || getHeight() < 100) {
            return null;
        }
        computeGeometry();
        int displayCol = (mouseX - originX) / cell;
        int displayRow = (mouseY - originY) / cell;
        if (mouseX < originX || mouseY < originY
                || displayCol < 0 || displayCol >= BOARD_SIZE
                || displayRow < 0 || displayRow >= BOARD_SIZE) {
            return null;
        }
        int x = viewColor == PlayerColor.BLUE ? displayCol : BOARD_SIZE - 1 - displayCol;
        int y = viewColor == PlayerColor.BLUE ? BOARD_SIZE - 1 - displayRow : displayRow;
        return new Square(x, y);
    }

    public int displayX(int x) {
        return viewColor == PlayerColor.BLUE ? x : BOARD_SIZE - 1 - x;
    }

    public int displayY(int y) {
        return viewColor == PlayerColor.BLUE ? BOARD_SIZE - 1 - y : y;
    }

    public int cell() {
        return cell;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }
}

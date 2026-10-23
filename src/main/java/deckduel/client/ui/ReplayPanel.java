package deckduel.client.ui;

import deckduel.client.state.ClientModel;
import deckduel.client.state.ClientModelListener;
import deckduel.client.state.ReplayPlayback;
import deckduel.client.state.Screen;
import deckduel.core.card.CardInstance;
import deckduel.core.card.PlayerView;
import deckduel.core.card.RoundResult;
import deckduel.core.card.Row;
import deckduel.core.card.RowCard;
import deckduel.core.card.ScoreBreakdown;
import deckduel.core.card.SideView;

import java.awt.*;
import java.util.List;
import javax.swing.*;

/**
 * Plays back one of the account's recorded matches over the match room: both Unit rows, the
 * owner's hand and the cards their Spy revealed, drawn with the painted card faces, a score
 * breakdown for both sides, and Previous, Play/Pause, Next and speed controls. It only reads the
 * model's {@link ReplayPlayback}; no control here can send a request or reach a live match.
 */
final class ReplayPanel extends JPanel {
    private static final int MARGIN = 24, RAIL = 300, HEADER = 74, CONTROLS = 70, GAP = 12;
    /** How long the brass glow around the latest card fades, unless motion is reduced. */
    private static final int GLOW_MILLIS = 700;

    private final ClientModel model;
    private final JButton back;
    private final JButton previous = UiKit.pill("Previous", UiKit.Pill.GOLD_OUTLINE);
    private final JButton play = UiKit.pill("Pause", UiKit.Pill.GOLD);
    private final JButton next = UiKit.pill("Next", UiKit.Pill.GOLD_OUTLINE);
    private final JButton speed = UiKit.pill("Speed 0.5×", UiKit.Pill.GOLD_OUTLINE);
    private final JLabel title = label(Theme.display(24f), Theme.CREAM);
    private final JLabel subtitle = label(Theme.normal(13f), Theme.FADED);
    private final JLabel position = label(Theme.bold(13f), Theme.BRASS_LIGHT);
    private final JLabel action = label(Theme.normal(13f), Theme.CREAM);
    private final Timer stepper = new Timer(ReplayPlayback.BASE_STEP_MILLIS, event -> advance());
    private final Timer glow = new Timer(30, event -> glowFrame());
    private ReplayPlayback playback;
    private long glowStartedAt;

    /** Builds the screen; Back returns to match history through the model. */
    ReplayPanel(ClientModel model) {
        this.model = model;
        setLayout(null);
        setBackground(Theme.BG);
        setFocusable(true);
        getAccessibleContext().setAccessibleName("Match replay");
        back = LobbyPanel.smallButton("Back", model::closeReplay);
        back.setName("replayBack");
        back.getAccessibleContext().setAccessibleDescription("Return to match history");
        previous.setName("replayPrevious");
        play.setName("replayPlay");
        next.setName("replayNext");
        speed.setName("replaySpeed");
        position.setName("replayPosition");
        action.setName("replayAction");
        title.setName("replayTitle");
        subtitle.setName("replaySubtitle");
        previous.addActionListener(event -> { if (playback != null) playback.previous(); });
        next.addActionListener(event -> { if (playback != null) playback.next(); });
        play.addActionListener(event -> togglePlay());
        speed.addActionListener(event -> cycleSpeed());
        for (JComponent child : new JComponent[] {back, previous, play, next, speed, title, subtitle, position, action}) add(child);
        stepper.setRepeats(true);
        bindKeys();
        model.addListener(new ClientModelListener() {
            @Override public void onScreenChanged(Screen screen) {
                if (screen == Screen.REPLAY) open(model.replay());
                else stop();
            }
            @Override public void onLobbyChanged() { if (model.reducedMotion()) glow.stop(); repaint(); }
        });
    }

    private void bindKeys() {
        bind("SPACE", "replay.play", this::togglePlay);
        bind("LEFT", "replay.previous", () -> previous.doClick(0));
        bind("RIGHT", "replay.next", () -> next.doClick(0));
        bind("ESCAPE", "replay.back", () -> back.doClick(0));
        bind("BACK_SPACE", "replay.back", () -> back.doClick(0));
    }

    private void bind(String key, String name, Runnable action) {
        // Scoped to this page, so other pages of the window keep their keys.
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                if (model.screen() == Screen.REPLAY) action.run();
            }
        });
    }

    private void open(ReplayPlayback next) {
        stop();
        playback = next;
        if (playback == null) return;
        playback.addListener(this::changed);
        var replay = playback.replay();
        title.setText("Replay · vs " + replay.opponent());
        subtitle.setText(date(replay.playedAt()) + (replay.practice() ? "  ·  Practice" : "")
                + (replay.complete() ? "" : "  ·  Incomplete recording: some actions are missing"));
        subtitle.setForeground(replay.complete() ? Theme.FADED : Theme.CORAL);
        changed();
        SwingUtilities.invokeLater(() -> { if (model.screen() == Screen.REPLAY) requestFocusInWindow(); });
    }

    private void stop() {
        stepper.stop();
        glow.stop();
        if (playback != null) playback.pause();
    }

    private void togglePlay() {
        if (playback == null) return;
        if (playback.playing()) playback.pause();
        else playback.play();
    }

    private void cycleSpeed() {
        if (playback == null) return;
        List<Double> speeds = ReplayPlayback.SPEEDS;
        playback.setSpeed(speeds.get((speeds.indexOf(playback.speed()) + 1) % speeds.size()));
    }

    private void advance() {
        if (playback == null || !playback.playing()) { stepper.stop(); return; }
        playback.next();
    }

    private void changed() {
        // Pills letter their captions in capitals; relabelled ones must match.
        play.setText(playback.playing() ? "PAUSE" : "PLAY");
        speed.setText("SPEED " + speedText(playback.speed()));
        previous.setEnabled(playback.index() > 0);
        next.setEnabled(!playback.atEnd());
        position.setText(playback.position());
        action.setText(playback.action());
        action.setToolTipText(playback.action());
        if (playback.playing()) {
            stepper.setInitialDelay(playback.stepMillis());
            stepper.setDelay(playback.stepMillis());
            if (!stepper.isRunning()) stepper.start();
        } else stepper.stop();
        glowStartedAt = System.nanoTime();
        if (model.reducedMotion()) glow.stop();
        else glow.restart();
        repaint();
    }

    private void glowFrame() {
        if (glowAlpha() <= 0 || !isShowing()) glow.stop();
        repaint();
    }

    /** The latest card's glow: fading after each step, steady with reduced motion. */
    private float glowAlpha() {
        if (model.reducedMotion()) return .8f;
        double elapsed = (System.nanoTime() - glowStartedAt) / 1e6;
        return (float) Math.max(0, 1 - elapsed / GLOW_MILLIS);
    }

    private static String speedText(double value) {
        return (value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value)) + "×";
    }

    private static String date(String playedAt) {
        return playedAt != null && playedAt.length() >= 16 ? playedAt.substring(0, 16).replace('T', ' ') : String.valueOf(playedAt);
    }

    private static JLabel label(Font font, Color colour) {
        JLabel label = new JLabel(" ");
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setFont(font);
        label.setForeground(colour);
        return label;
    }

    @Override public void doLayout() {
        int w = getWidth(), h = getHeight();
        Dimension backSize = back.getPreferredSize();
        back.setBounds(MARGIN, MARGIN, backSize.width, backSize.height);
        int textX = MARGIN + backSize.width + 18;
        title.setBounds(textX, MARGIN - 4, Math.max(0, w - textX - MARGIN - 60), 32);
        subtitle.setBounds(textX, MARGIN + 28, Math.max(0, w - textX - MARGIN - 60), 20);
        int y = h - MARGIN - CONTROLS + 10;
        int x = MARGIN;
        for (JButton button : new JButton[] {previous, play, next, speed}) {
            Dimension size = button.getPreferredSize();
            button.setBounds(x, y, size.width, size.height);
            x += size.width + 10;
        }
        int textWidth = Math.max(0, w - x - MARGIN - 8);
        position.setBounds(x + 8, y - 2, textWidth, 20);
        action.setBounds(x + 8, y + 20, textWidth, 20);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = UiKit.nice(graphics);
        try {
            BattlefieldMenuPanel.paintCover(g, AssetStore.optional("match-room.png"), getWidth(), getHeight(), this);
            // A dark veil keeps cards and numbers readable over the painting.
            g.setColor(new Color(12, 10, 9, 150));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (playback == null) return;
            PlayerView view = playback.frame();
            Rectangle board = new Rectangle(MARGIN, MARGIN + HEADER, getWidth() - 2 * MARGIN - RAIL - GAP,
                    getHeight() - 2 * MARGIN - HEADER - CONTROLS);
            paintBoard(g, view, board);
            paintRail(g, view, new Rectangle(getWidth() - MARGIN - RAIL, board.y, RAIL, board.height));
        } finally { g.dispose(); }
    }

    private void paintBoard(Graphics2D g, PlayerView view, Rectangle board) {
        int cardWidth = (int) Math.max(40, Math.min((board.width - 6 * GAP) / 7.0, board.height * .29 / 1.4));
        int cardHeight = (int) Math.round(cardWidth * 1.4);
        int handWidth = (int) Math.round(cardWidth * .72), handHeight = (int) Math.round(handWidth * 1.4);
        Long latest = view.lastPlay() == null ? null : view.lastPlay().card().id();
        int y = board.y;
        y = caption(g, board.x, y, "OPPONENT  ·  hand " + view.opponent().handCount() + "  ·  deck " + view.opponent().deckCount()
                + "  ·  plays left " + view.opponent().playsRemaining());
        if (!view.revealed().isEmpty()) {
            paintCards(g, view.revealed(), board.x, y, handWidth / 2, handHeight / 2, null, board.width);
            y += handHeight / 2 + 8;
        }
        paintRow(g, view.opponent().row(), board, y, cardWidth, cardHeight, latest);
        y = caption(g, board.x, y + cardHeight + 4, "YOUR ROW") + 2;
        paintRow(g, view.you().row(), board, y, cardWidth, cardHeight, latest);
        y += cardHeight + 18;
        y = caption(g, board.x, y, "YOUR HAND  ·  deck " + view.you().deckCount() + "  ·  plays left "
                + view.you().playsRemaining() + "  ·  draws left " + view.you().drawsRemaining());
        int handEnd = paintCards(g, view.hand(), board.x, y, handWidth, handHeight, latest, board.width);
        if (!view.looking().isEmpty()) {
            g.setFont(Theme.bold(11f));
            g.setColor(Theme.BRASS_LIGHT);
            g.drawString("RECONNAISSANCE", handEnd + 16, y + 12);
            paintCards(g, view.looking(), handEnd + 16, y + 18, handWidth * 3 / 4, handHeight * 3 / 4, null, board.width);
        }
    }

    private int caption(Graphics2D g, int x, int y, String text) {
        g.setFont(Theme.bold(11f));
        g.setColor(Theme.BRASS_LIGHT);
        g.drawString(text, x, y + 12);
        return y + 20;
    }

    private void paintRow(Graphics2D g, Row row, Rectangle board, int y, int width, int height, Long latest) {
        int left = board.x + (board.width - (7 * width + 6 * GAP)) / 2;
        for (int slot = 0; slot < Row.SIZE; slot++) {
            Rectangle at = new Rectangle(left + slot * (width + GAP), y, width, height);
            RowCard card = row.at(slot);
            if (card == null) {
                Graphics2D slotG = (Graphics2D) g.create();
                slotG.translate(at.x, at.y);
                UiKit.paintSlot(slotG, at.width, at.height, false);
                slotG.dispose();
                continue;
            }
            boolean newest = latest != null && card.card().id() == latest;
            CardFace.paint(g, card.def(), at, newest, card.suppressed());
            if (newest) paintGlow(g, at);
        }
    }

    /** Lays cards out left to right, overlapping when they would not fit; returns the right edge. */
    private int paintCards(Graphics2D g, List<CardInstance> cards, int x, int y, int width, int height, Long latest, int room) {
        if (cards.isEmpty()) return x;
        int step = Math.min(width + 8, Math.max(12, (room - width) / Math.max(1, cards.size() - 1)));
        for (int i = 0; i < cards.size(); i++) {
            Rectangle at = new Rectangle(x + i * step, y, width, height);
            CardFace.paint(g, cards.get(i).def(), at, false, List.of());
            if (latest != null && cards.get(i).id() == latest) paintGlow(g, at);
        }
        return x + (cards.size() - 1) * step + width;
    }

    private void paintGlow(Graphics2D g, Rectangle at) {
        float alpha = glowAlpha();
        if (alpha <= 0) return;
        Graphics2D glowG = (Graphics2D) g.create();
        glowG.setComposite(AlphaComposite.SrcOver.derive(alpha));
        glowG.setColor(Theme.BRASS_LIGHT);
        glowG.setStroke(new BasicStroke(3f));
        glowG.drawRoundRect(at.x - 3, at.y - 3, at.width + 6, at.height + 6, 12, 12);
        glowG.dispose();
    }

    /** The score breakdown for both sides, the banked rounds and, at the end, the result. */
    private void paintRail(Graphics2D g, PlayerView view, Rectangle rail) {
        Graphics2D plate = (Graphics2D) g.create();
        plate.translate(rail.x, rail.y);
        UiKit.paintPlate(plate, rail.width - 4, rail.height - 4);
        plate.dispose();
        int x = rail.x + 24, y = rail.y + 34, width = rail.width - 52;
        g.setFont(Theme.display(16f));
        g.setColor(Theme.BRASS_LIGHT);
        g.drawString("ROUND " + view.round() + " OF 3", x, y);
        y = side(g, "YOU", view.you(), x, y + 26, width);
        y = side(g, model.replay() == null ? "OPPONENT" : model.replay().replay().opponent().toUpperCase(java.util.Locale.ROOT),
                view.opponent(), x, y + 14, width);
        g.setFont(Theme.bold(11f));
        g.setColor(Theme.BRASS_LIGHT);
        y += 14;
        g.drawString("BANKED ROUNDS", x, y);
        g.setFont(Theme.normal(12f));
        g.setColor(Theme.CREAM);
        for (RoundResult round : view.rounds()) {
            y += 18;
            g.drawString("Round " + round.round() + "   " + round.scoreOf(view.seat()) + " – " + round.scoreOf(view.seat().other()), x, y);
        }
        if (view.rounds().isEmpty()) g.drawString("None yet", x, y += 18);
        if (view.outcome() != null) {
            g.setFont(Theme.display(18f));
            boolean won = view.seat() == view.outcome().winner();
            g.setColor(view.outcome().winner() == null ? Theme.CREAM : won ? Theme.BRASS_LIGHT : Theme.CORAL);
            String verdict = view.outcome().winner() == null ? "DRAW" : won ? "YOU WON" : "YOU LOST";
            g.drawString(verdict + (view.outcome().forfeit() ? " (FORFEIT)" : ""), x, y + 32);
        }
    }

    private int side(Graphics2D g, String name, SideView side, int x, int y, int width) {
        ScoreBreakdown score = side.score();
        g.setFont(Theme.bold(12f));
        g.setColor(Theme.BRASS_LIGHT);
        g.drawString(name, x, y);
        g.setFont(Theme.display(22f));
        g.setColor(Theme.CREAM);
        String total = String.valueOf(score.total());
        g.drawString(total, x + width - g.getFontMetrics().stringWidth(total), y + 4);
        g.setFont(Theme.normal(12f));
        g.setColor(Theme.FADED);
        y = line(g, "Units " + score.units() + "   ·   Combos " + score.combos(), x, y + 22);
        y = line(g, score.formation() == null ? "No formation"
                : score.formation().title() + "  +" + score.formationBonus() + "  ×" + score.multiplier(), x, y);
        if (score.bonus() != 0) y = line(g, "Bonus +" + score.bonus() + " (added after ×)", x, y);
        return line(g, "Match total " + side.matchTotal(), x, y);
    }

    private static int line(Graphics2D g, String text, int x, int y) {
        g.drawString(text, x, y);
        return y + 18;
    }
}

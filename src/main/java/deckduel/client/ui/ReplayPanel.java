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
import java.util.function.BooleanSupplier;
import javax.swing.*;

/**
 * Plays back one of the account's recorded matches over the match room: both Unit rows, the
 * owner's hand and the cards their Spy revealed, drawn with the painted card faces, a score
 * breakdown for both sides, and Previous, Play/Pause, Next and speed controls. It only reads the
 * {@link ReplayPlayback}, also used for bundled training recordings; no control here can send
 * a request or reach a live match.
 */
final class ReplayPanel extends JPanel {
    private static final int MARGIN = 24, RAIL = 300, HEADER = 74, CONTROLS = 120, GAP = 12;
    /** How long the brass glow around the latest card fades, unless motion is reduced. */
    private static final int GLOW_MILLIS = 700;

    private final BooleanSupplier reducedMotion, active;
    private final JButton back;
    private final JButton previous = UiKit.pill("Previous", UiKit.Pill.GOLD_OUTLINE);
    private final JButton play = UiKit.pill("Pause", UiKit.Pill.GOLD);
    private final JButton next = UiKit.pill("Next", UiKit.Pill.GOLD_OUTLINE);
    private final JButton speed = UiKit.pill("Speed 0.5×", UiKit.Pill.GOLD_OUTLINE);
    private final JButton details = UiKit.pill("Details", UiKit.Pill.GOLD_OUTLINE);
    private final ReplayTimeline timeline = new ReplayTimeline();
    private final ReplayDetailsPanel drawer = new ReplayDetailsPanel();
    private final Runnable playbackChanged = this::changed;
    private final JLabel title = label(Theme.display(24f), Theme.CREAM);
    private final JLabel subtitle = label(Theme.normal(13f), Theme.FADED);
    private final JLabel position = label(Theme.bold(13f), Theme.BRASS_LIGHT);
    private final JLabel action = label(Theme.normal(13f), Theme.CREAM);
    private final Timer stepper = new Timer(ReplayPlayback.BASE_STEP_MILLIS, event -> advance());
    private final Timer glow = new Timer(30, event -> glowFrame());
    private ReplayPlayback playback;
    private boolean training;
    private long glowStartedAt;

    /** Builds the screen; Back returns to match history through the model. */
    ReplayPanel(ClientModel model) {
        this(model::reducedMotion, () -> model.screen() == Screen.REPLAY, model::closeReplay);
        model.addListener(new ClientModelListener() {
            @Override public void onScreenChanged(Screen screen) {
                if (screen == Screen.REPLAY) open(model.replay());
                else stop();
            }
            @Override public void onLobbyChanged() { if (reducedMotion.getAsBoolean()) glow.stop(); repaint(); }
        });
    }

    /** Local playback surface shared by personal replays and the guide; has no server connection. */
    ReplayPanel(BooleanSupplier reducedMotion, BooleanSupplier active, Runnable backAction) {
        this.reducedMotion = reducedMotion; this.active = active;
        KingArt.whenReady(this::repaint);
        setLayout(null);
        setBackground(Theme.BG);
        setFocusable(true);
        getAccessibleContext().setAccessibleName("Match replay");
        back = LobbyPanel.smallButton("Back", backAction);
        back.setName("replayBack");
        back.getAccessibleContext().setAccessibleDescription("Return to match history");
        previous.setName("replayPrevious");
        play.setName("replayPlay");
        next.setName("replayNext");
        speed.setName("replaySpeed");
        details.setName("replayDetailsToggle");
        details.addActionListener(event -> {
            drawer.setVisible(!drawer.isVisible());
            details.setText(!training && drawer.isVisible() ? "SCORES" : "DETAILS");
            details.getAccessibleContext().setAccessibleDescription(drawer.isVisible()
                    ? "Show the score rail" : "Show the action journal and score changes");
            doLayout(); repaint();
        });
        drawer.setVisible(false);
        position.setName("replayPosition");
        action.setName("replayAction");
        title.setName("replayTitle");
        subtitle.setName("replaySubtitle");
        previous.addActionListener(event -> { if (playback != null) playback.previous(); });
        next.addActionListener(event -> { if (playback != null) playback.next(); });
        play.addActionListener(event -> togglePlay());
        speed.addActionListener(event -> cycleSpeed());
        for (JComponent child : new JComponent[] {back, previous, play, next, speed, details, timeline, drawer,
                title, subtitle, position, action}) add(child);
        stepper.setRepeats(true);
        bindKeys();
    }

    private void bindKeys() {
        bind("SPACE", "replay.play", this::togglePlay);
        bind("LEFT", "replay.previous", () -> previous.doClick(0));
        bind("RIGHT", "replay.next", () -> next.doClick(0));
        bind("HOME", "replay.first", () -> { if (playback != null) playback.seek(0); });
        bind("END", "replay.last", () -> { if (playback != null) playback.seek(playback.length() - 1); });
        bind("PAGE_UP", "replay.previousRound", () -> { if (playback != null) playback.previousRound(); });
        bind("PAGE_DOWN", "replay.nextRound", () -> { if (playback != null) playback.nextRound(); });
        bind("ESCAPE", "replay.back", () -> back.doClick(0));
        bind("BACK_SPACE", "replay.back", () -> back.doClick(0));
    }

    private void bind(String key, String name, Runnable action) {
        // Scoped to this page, so other pages of the window keep their keys.
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                if (active.getAsBoolean() && (!training || name.equals("replay.next") || name.equals("replay.back"))) action.run();
            }
        });
    }

    private void open(ReplayPlayback next) {
        stop();
        training = false;
        for (JComponent control : new JComponent[] {previous, play, speed, timeline}) control.setVisible(true);
        drawer.training(false);
        if (playback != null) playback.removeListener(playbackChanged);
        playback = next;
        timeline.attach(next); drawer.attach(next);
        drawer.setVisible(false); details.setText("DETAILS"); back.setVisible(true);
        if (playback == null) return;
        playback.addListener(playbackChanged);
        var replay = playback.replay();
        title.setText("Replay · vs " + replay.opponent());
        subtitle.setText(date(replay.playedAt()) + (replay.practice() ? "  ·  Practice" : "")
                + (replay.complete() ? "" : "  ·  Incomplete recording: some actions are missing"));
        subtitle.setForeground(replay.complete() ? Theme.FADED : Theme.CORAL);
        changed();
        SwingUtilities.invokeLater(() -> { if (active.getAsBoolean()) requestFocusInWindow(); });
    }

    /** Opens a contiguous training excerpt with its own identity, apart from account history. */
    void openTraining(ReplayPlayback next) {
        open(next);
        training = true;
        for (JComponent control : new JComponent[] {previous, play, speed, timeline}) control.setVisible(false);
        drawer.training(true);
        back.setVisible(false);
        title.setText("Recorded example");
        subtitle.setText("Next: one recorded action · Details: explanation");
        doLayout();
    }

    void stop() {
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
        if (playback == null) return;
        timeline.refresh(); drawer.refresh();
        // Pills letter their captions in capitals; relabelled ones must match.
        play.setText(playback.playing() ? "PAUSE" : "PLAY");
        speed.setText("SPEED " + speedText(playback.speed()));
        previous.setEnabled(playback.index() > 0);
        next.setEnabled(!playback.atEnd());
        position.setText(playback.position());
        action.setText(playback.action());
        action.setToolTipText(playback.action());
        if (!training && playback.playing() && active.getAsBoolean()) {
            stepper.setInitialDelay(playback.stepMillis());
            stepper.setDelay(playback.stepMillis());
            if (!stepper.isRunning()) stepper.start();
        } else stepper.stop();
        glowStartedAt = System.nanoTime();
        if (reducedMotion.getAsBoolean()) glow.stop();
        else glow.restart();
        repaint();
    }

    private void glowFrame() {
        if (reducedMotion.getAsBoolean() || glowAlpha() <= 0 || !isShowing()) glow.stop();
        repaint();
    }

    /** The latest card's glow: fading after each step, steady with reduced motion. */
    private float glowAlpha() {
        if (reducedMotion.getAsBoolean()) return .8f;
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
        int textX = back.isVisible() ? MARGIN + backSize.width + 18 : MARGIN;
        title.setBounds(textX, MARGIN - 4, Math.max(0, w - textX - MARGIN - 60), 32);
        subtitle.setBounds(textX, MARGIN + 28, Math.max(0, w - textX - MARGIN - 60), 20);
        timeline.setBounds(MARGIN, h - MARGIN - CONTROLS + 4, Math.max(0, w - 2 * MARGIN), 44);
        int y = h - MARGIN - 66;
        int x = MARGIN;
        for (JButton button : new JButton[] {previous, play, next, speed, details}) {
            if (!button.isVisible()) continue;
            Dimension size = button.getPreferredSize();
            button.setBounds(x, y, size.width, size.height);
            x += size.width + 10;
        }
        int textWidth = Math.max(0, w - x - MARGIN - 8);
        position.setBounds(x + 8, y - 2, textWidth, 20);
        action.setBounds(x + 8, y + 20, textWidth, 20);
        drawer.setBounds(w - MARGIN - RAIL, MARGIN + HEADER, RAIL,
                Math.max(0, h - 2 * MARGIN - HEADER - controlsHeight()));
    }

    private int controlsHeight() { return training ? 70 : CONTROLS; }

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
                    getHeight() - 2 * MARGIN - HEADER - controlsHeight());
            paintBoard(g, view, board);
            if (!drawer.isVisible()) paintRail(g, view, new Rectangle(getWidth() - MARGIN - RAIL, board.y, RAIL, board.height));
        } finally { g.dispose(); }
    }

    private void paintBoard(Graphics2D g, PlayerView view, Rectangle board) {
        if (view.phase() == deckduel.core.card.MatchPhase.KING_SELECTION) {
            caption(g, board.x, board.y, view.selectedKing() == null ? "KING OFFER · choose one" : "CONFIRMED " + view.selectedKing().title() + " · waiting");
            int height = Math.min(170, board.height / 4), width = height * 1060 / 1482;
            for (int i = 0; i < view.kingOffer().size(); i++)
                KingArt.paint(g, view.kingOffer().get(i), new Rectangle(board.x + i * (width + 12), board.y + 24, width, height));
            board = new Rectangle(board.x, board.y + height + 36, board.width, board.height - height - 36);
        }
        // Fit both rows and the hand above the seek bar, including private inspection captions.
        double available = board.height - 110 - (view.revealed().isEmpty() ? 0 : 50);
        int cardWidth = (int) Math.max(24, Math.min((board.width - 104 - 6 * GAP) / 7.0, available / (1.4 * 2.72)));
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
        Rectangle units = new Rectangle(board.x + 104, board.y, board.width - 104, board.height);
        paintKings(g, view.opponent(), board.x, y, cardHeight);
        paintRow(g, view.opponent().row(), units, y, cardWidth, cardHeight, latest);
        y = caption(g, board.x, y + cardHeight + 4, "YOUR ROW") + 2;
        paintKings(g, view.you(), board.x, y, cardHeight);
        paintRow(g, view.you().row(), units, y, cardWidth, cardHeight, latest);
        y += cardHeight + 18;
        y = caption(g, board.x, y, "YOUR HAND  ·  deck " + view.you().deckCount() + "  ·  plays left "
                + view.you().playsRemaining() + "  ·  draws left " + view.you().drawsRemaining());
        int lookedWidth = handWidth * 3 / 4, lookedHeight = handHeight * 3 / 4;
        int lookRoom = view.looking().isEmpty() ? 0 : Math.max(110, view.looking().size() * (lookedWidth + 8) - 8);
        int handRoom = lookRoom == 0 ? board.width : board.width - lookRoom - 16;
        paintCards(g, view.hand(), board.x, y, handWidth, handHeight, latest, handRoom);
        if (!view.looking().isEmpty()) {
            // Inspected cards need their own space; a full hand otherwise pushes them under the rail.
            int lookX = board.x + handRoom + 16;
            g.setFont(Theme.bold(11f));
            g.setColor(Theme.BRASS_LIGHT);
            g.drawString((view.choiceKind() == deckduel.core.card.ChoiceKind.SECRET_EDICT ? "SECRET EDICT" : "RECONNAISSANCE"), lookX, y + 12);
            paintCards(g, view.looking(), lookX, y + 18, lookedWidth, lookedHeight, null, lookRoom);
        }
    }

    private static void paintKings(Graphics2D g, SideView side, int x, int y, int room) {
        int height = Math.min(68, room), width = height * 1060 / 1482;
        for (int i = 0; i < side.kings().kings().size(); i++)
            KingArt.paint(g, side.kings().kings().get(i), new Rectangle(x + i * (width + 4), y, width, height));
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
        int x = rail.x + 24, y = rail.y + (compact() ? 24 : 34), width = rail.width - 52;
        g.setFont(Theme.display(16f));
        g.setColor(Theme.BRASS_LIGHT);
        g.drawString("ROUND " + view.round() + " OF 3", x, y);
        y = side(g, "YOU", view.you(), x, y + (compact() ? 20 : 26), width);
        y = side(g, playback.replay().opponent().toUpperCase(java.util.Locale.ROOT),
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
        String kings = side.kings().kings().stream().map(k -> k.title()).collect(java.util.stream.Collectors.joining(", "));
        if (!compact() && !kings.isEmpty()) y = line(g, "Kings: " + kings, x, y + 22) - 4;
        y = line(g, "Units " + score.units() + "   ·   Combos " + score.combos(), x, y + (compact() ? 18 : 22));
        y = line(g, score.formation() == null ? "No formation"
                : score.formation().title() + "  +" + score.formationBonus() + "  ×" + score.multiplier(), x, y);
        if (score.bonus() != 0) y = line(g, "Bonus +" + score.bonus() + " (added after ×)", x, y);
        for (var king : score.kings()) y = line(g, king.king().title() + " +" + king.points() + " (after ×)", x, y);
        if (side.kings().kings().contains(deckduel.core.card.King.NETHRION))
            y = line(g, "Secret Edict: " + (side.kings().edictUsed() ? "used" : "ready"), x, y);
        return line(g, "Match total " + side.matchTotal(), x, y);
    }

    private boolean compact() { return getHeight() < 680; }

    private int line(Graphics2D g, String text, int x, int y) {
        g.drawString(text, x, y);
        return y + (compact() ? 16 : 18);
    }
}

package deckduel.client.ui;

import deckduel.client.state.ClientModel;
import deckduel.client.state.Screen;
import deckduel.core.duel.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.*;

/** Private, one-time opening choice with authoritative replacement and deck-to-hand flights. */
final class OpeningHandPanel extends JLayeredPane {
    private enum Stage { DEAL, SELECT, RETURN, DRAW, WAIT, HAND }
    private final ClientModel model;
    private final DeckStackPanel deck;
    private final Supplier<List<SkillCardPanel>> hand;
    private final Runnable finished;
    private final DeckDrawOverlay flights = new DeckDrawOverlay();
    private final List<SkillCardPanel> cards = new ArrayList<>();
    private final List<JButton> toggles = new ArrayList<>();
    private final Set<String> marked = new HashSet<>();
    private final JLabel title = MatchStyle.label("YOUR OPENING HAND", 24);
    private final JLabel hint = MatchStyle.label("Choose skills to replace, then confirm. You can keep them all.", 13);
    private final JButton confirm = UiKit.pill("Confirm", UiKit.Pill.GOLD);
    private final Timer completion = new Timer(1, e -> advance());
    private PlayerView latest;
    private List<CardInstance> shown = List.of();
    private String matchId;
    private Stage stage;
    private boolean seen;
    private boolean deferred;

    OpeningHandPanel(ClientModel model, DeckStackPanel deck, Supplier<List<SkillCardPanel>> hand, Runnable finished) {
        this.model = model; this.deck = deck; this.hand = hand; this.finished = finished;
        setName("openingHand"); setOpaque(false); setVisible(false);
        completion.setRepeats(false);
        add(title, Integer.valueOf(0)); add(hint, Integer.valueOf(0));
        add(confirm, Integer.valueOf(0)); add(flights, Integer.valueOf(100));
        confirm.setName("confirmOpening");
        confirm.addActionListener(e -> {
            if (stage == Stage.SELECT && model.inFlightRequest() == null && !model.currentSync().paused())
                model.sendAction(new GameAction.ConfirmOpening(shown.stream().map(CardInstance::instanceId)
                        .filter(marked::contains).toList()));
        });
    }

    /** Syncs only permitted own cards and confirmation flags; marking never touches the host. */
    void sync(boolean entryPending) {
        if (model.currentSync() == null) { suspend(); return; }
        String id = model.currentSync().matchId();
        if (!id.equals(matchId)) {
            suspend(); matchId = id; seen = false; shown = List.of(); marked.clear();
        }
        latest = model.view();
        if (entryPending || model.screen() != Screen.GAME || latest.outcome() != null) { suspend(); return; }
        if (stage == null && latest.phase() != TurnPhase.OPENING) return;
        if (stage == null) {
            setVisible(true); model.setOpeningPresentationPending(true); rebuild(latest.ownSkillHand());
            stage = latest.openingConfirmed() ? Stage.WAIT : seen ? Stage.SELECT : Stage.DEAL;
            if (stage == Stage.DEAL) deferDeal();
        } else if (!animating() && latest.openingConfirmed()) {
            if (!shown.equals(latest.ownSkillHand())) {
                startReturn();
            } else if (latest.phase() != TurnPhase.OPENING && stage != Stage.HAND) startHand();
            else stage = Stage.WAIT;
        }
        if (model.reducedMotion() && completion.isRunning()) {
            flights.setReducedMotion(true); completion.setInitialDelay(1); completion.restart();
        }
        updateControls(); revalidate(); repaint();
    }

    private boolean animating() { return stage == Stage.DEAL || stage == Stage.RETURN || stage == Stage.DRAW || stage == Stage.HAND; }

    private void deferDeal() {
        if (deferred) return;
        deferred = true;
        SwingUtilities.invokeLater(() -> {
            deferred = false;
            if (!isVisible() || stage != Stage.DEAL) return;
            seen = true;
            doLayout(); startDraw(true);
        });
    }

    private void rebuild(List<CardInstance> next) {
        for (var card : cards) remove(card);
        for (var toggle : toggles) remove(toggle);
        cards.clear(); toggles.clear(); shown = List.copyOf(next);
        for (CardInstance instance : shown) {
            int slot = cards.size();
            var card = new SkillCardPanel(instance, latest.rules(), latest.viewer(), latest.rules().manaCap(),
                    TurnPhase.SKILL, true, marked.contains(instance.instanceId()), () -> toggle(slot));
            card.setReducedMotion(model.reducedMotion()); cards.add(card); add(card, Integer.valueOf(0));
            JButton button = UiKit.pill("Replace", UiKit.Pill.CREAM_OUTLINE);
            button.setName("openingReplace" + slot);
            button.addActionListener(e -> toggle(slot)); toggles.add(button); add(button, Integer.valueOf(0));
        }
        doLayout();
    }

    private void toggle(int slot) {
        if (stage != Stage.SELECT || model.inFlightRequest() != null || model.currentSync().paused()) return;
        String id = shown.get(slot).instanceId();
        if (!marked.remove(id) && marked.size() < latest.skillDeckCount()) marked.add(id);
        updateControls();
    }

    private void updateControls() {
        boolean editable = stage == Stage.SELECT && model.inFlightRequest() == null && !model.currentSync().paused();
        for (int i = 0; i < cards.size(); i++) {
            boolean replace = marked.contains(shown.get(i).instanceId());
            cards.get(i).updateState(latest.rules().manaCap(), TurnPhase.SKILL, true, replace);
            toggles.get(i).setText(replace ? "Undo replace" : "Replace");
            toggles.get(i).setEnabled(editable && (replace || marked.size() < latest.skillDeckCount()));
            toggles.get(i).setVisible(stage == Stage.SELECT);
        }
        confirm.setEnabled(editable); confirm.setVisible(stage == Stage.SELECT);
        confirm.setText(marked.isEmpty() ? "Keep all" : "Confirm (" + marked.size() + ")");
        hint.setText(model.currentSync().paused() ? "Match paused — reconnecting…"
                : stage == Stage.SELECT ? "Mark the skills you want to replace, then confirm once."
                : stage == Stage.WAIT ? "Hand ready — waiting for your opponent…" : "Preparing your hand…");
    }

    private Supplier<Rectangle> deckBounds() {
        return () -> SwingUtilities.convertRectangle(deck, deck.topBounds(), flights);
    }

    private Supplier<Rectangle> cardBounds(int slot) { return DeckDrawOverlay.boundsOf(cards.get(slot), flights); }

    private void startReturn() {
        stage = Stage.RETURN;
        var batch = new ArrayList<DeckDrawOverlay.Flight>();
        for (int i = 0; i < shown.size(); i++) if (!shown.get(i).equals(latest.ownSkillHand().get(i))) {
            var face = DeckDrawOverlay.snapshot(cards.get(i));
            String key = "return:" + i;
            batch.add(DeckDrawOverlay.Flight.concealed(key, face, cardBounds(i), deckBounds(), batch.size() * 80));
            cards.get(i).putClientProperty("deckFlightMask", (java.util.function.BooleanSupplier) () -> stage == Stage.RETURN);
        }
        run(batch);
    }

    private void startDraw(boolean all) {
        List<CardInstance> previous = shown;
        if (!all) { flights.cancel(); rebuild(latest.ownSkillHand()); }
        stage = all ? Stage.DEAL : Stage.DRAW;
        var batch = new ArrayList<DeckDrawOverlay.Flight>();
        for (int i = 0; i < shown.size(); i++) if (all || !shown.get(i).equals(previous.get(i))) {
            String key = "draw:" + i;
            batch.add(DeckDrawOverlay.Flight.reveal(key, deck.backImage(), DeckDrawOverlay.snapshot(cards.get(i)),
                    deckBounds(), cardBounds(i), batch.size() * 80));
            flights.bind(cards.get(i), key);
        }
        run(batch);
    }

    private void startHand() {
        stage = Stage.HAND;
        var batch = new ArrayList<DeckDrawOverlay.Flight>();
        List<SkillCardPanel> targets = hand.get();
        for (int i = 0; i < cards.size() && i < targets.size(); i++) {
            String key = "hand:" + i;
            var face = DeckDrawOverlay.snapshot(cards.get(i));
            batch.add(DeckDrawOverlay.Flight.concealed(key, face, cardBounds(i),
                    handBounds(targets.get(i)), i * 80));
            cards.get(i).putClientProperty("deckFlightMask", (java.util.function.BooleanSupplier) () -> stage == Stage.HAND);
        }
        run(batch);
    }

    private Supplier<Rectangle> handBounds(JComponent target) {
        // The real hand is behind this modal layer, outside the preview's own subtree.
        return () -> target.getParent() == null || !target.isVisible()
                || SwingUtilities.getRoot(target) != SwingUtilities.getRoot(this) ? null
                : SwingUtilities.convertRectangle(target.getParent(), target.getBounds(), flights);
    }

    private void run(List<DeckDrawOverlay.Flight> batch) {
        flights.setReducedMotion(model.reducedMotion()); flights.play(batch); updateControls();
        completion.setInitialDelay(model.reducedMotion() || batch.isEmpty() ? 1 : 650 + (batch.size() - 1) * 80);
        completion.restart();
    }

    private void advance() {
        if (!isVisible()) return;
        flights.cancel();
        switch (stage) {
            case RETURN -> startDraw(false);
            case DEAL -> { stage = Stage.SELECT; sync(false); }
            case DRAW -> { marked.clear(); stage = Stage.WAIT; sync(false); }
            case HAND -> {
                stage = null; setVisible(false);
                model.setOpeningPresentationPending(false); finished.run();
            }
            default -> { }
        }
    }

    /** Cancels visual work on loading, navigation, reconnect or disposal; no gameplay action is sent. */
    void suspend() {
        completion.stop(); flights.cancel(); stage = null; setVisible(false);
        model.setOpeningPresentationPending(false);
    }

    @Override public void removeNotify() { suspend(); super.removeNotify(); }

    @Override public void doLayout() {
        int gap = 22;
        int width = Math.max(70, Math.min(196, (getWidth() - 64 - gap * Math.max(0, cards.size() - 1)) / Math.max(1, cards.size())));
        int height = width * 7 / 5;
        int left = (getWidth() - cards.size() * width - Math.max(0, cards.size() - 1) * gap) / 2;
        int top = Math.max(92, (getHeight() - height - 110) / 2);
        title.setBounds(24, top - 78, getWidth() - 48, 34); title.setHorizontalAlignment(SwingConstants.CENTER);
        hint.setBounds(24, top - 36, getWidth() - 48, 26); hint.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).setBounds(left + i * (width + gap), top, width, height);
            toggles.get(i).setBounds(left + i * (width + gap), top + height + 12, width, 36);
        }
        confirm.setBounds((getWidth() - 220) / 2, top + height + 64, 220, 42);
        flights.setBounds(0, 0, getWidth(), getHeight());
    }

    @Override protected void paintComponent(Graphics graphics) {
        graphics.setColor(new Color(10, 15, 17, 215)); graphics.fillRect(0, 0, getWidth(), getHeight());
    }
}

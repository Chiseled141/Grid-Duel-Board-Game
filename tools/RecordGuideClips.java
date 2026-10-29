package deckduel.client.ui;

import deckduel.client.ScoringSounds;
import deckduel.core.card.*;
import deckduel.net.MatchUpdate;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.util.Duration;
import javax.imageio.ImageIO;

/**
 * Records the How to Play quick-guide clips from a real match on the real {@link CardTableView}:
 * the engine applies every move, the greedy bot plays the opponent, and scripted pointer input
 * presses, drags and releases on the table. Writes PNG frames per clip under {@code args[0]}
 * (seed in {@code args[1]}; a third argument records only the match end). See docs/README.md#how-to-play.
 */
public class RecordGuideClips {
    static final Seat ME = Seat.FIRST;
    static File out;
    static MatchState state;
    static CardTableView table;
    static Scene scene;
    static volatile Point2D pointer;
    static volatile boolean pressedLook;
    static final List<BufferedImage> frames = new ArrayList<>();
    static volatile boolean recording;
    /** Frames render at twice the 1280×720 scene so the guide stays sharp on HiDPI screens. */
    static final int SCALE = 2;
    static final SnapshotParameters HI_DPI = new SnapshotParameters();
    static { HI_DPI.setTransform(Transform.scale(SCALE, SCALE)); }

    public static void main(String[] args) throws Exception {
        out = new File(args[0]); out.mkdirs();
        FxKit.start();
        VfxSprites.preload();
        var catalog = deckduel.cards.CatalogLoader.loadBundled();
        PaintedCardArt.preload(catalog.cards());
        var deck = DeckRules.deck(catalog);
        long seed = Long.parseLong(args[1]);
        state = MatchState.start(deck, deck, seed);
        fx(() -> {
            table = new CardTableView(new CardTableView.Actions() {
                @Override public void move(Move move) { new Thread(() -> mine(move)).start(); }
                @Override public void rematch() { }
                @Override public void leave() { }
                @Override public void quit() { }
                @Override public void toggle(PauseMenu.Preference preference) { }
            }, new ScoringSounds(() -> false));
            scene = new Scene(table, 1280, 720);
            scene.getStylesheets().add(FxKit.STYLESHEET);
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            Timeline capture = new Timeline(new KeyFrame(Duration.millis(100), e -> {
                if (!recording) return;
                BufferedImage shot = SwingFXUtils.fromFXImage(table.snapshot(HI_DPI, null), null);
                frames.add(withPointer(shot));
            }));
            capture.setCycleCount(Timeline.INDEFINITE);
            capture.play();
            return null;
        });
        Thread.sleep(2500);
        boolean winOnly = args.length > 2;
        if (winOnly) { finish(); return; }
        // Kings: the offer rises; the bot has chosen, we pick and confirm.
        state = state.apply(Seat.SECOND, GreedyBot.choose(state.view(Seat.SECOND)));
        show(false);
        fx(() -> { table.shown(0); return null; });
        for (int i = 0; i < 120 && !fx(() -> table.lookup("#kingSelection") != null && table.lookup("#kingSelection").isVisible()); i++) sleep(100);
        record("kings", () -> {
            sleep(1600);
            King king = state.view(ME).kingOffer().get(1);
            point(fx(() -> centreOf("#offer-" + king.id())), 700);
            fx(() -> { ((javafx.scene.control.Button) table.lookup("#offer-" + king.id())).fire(); return null; });
            sleep(900);
            point(fx(() -> centreOf("#confirm-" + king.id())), 600);
            fx(() -> { ((javafx.scene.control.Button) table.lookup("#confirm-" + king.id())).fire(); return null; });
            sleep(3800);
        });
        waitForTurn();
        // Play: drag the best Unit onto the row.
        record("play", () -> { dragBestUnit(false); sleep(2600); waitForTurn(); sleep(300); });
        record("combos", () -> { dragBestUnit(true); sleep(3600); });
        waitForTurn();
        // Formation: keep placing Units, recording the turn that completes one.
        for (int turn = 0; turn < 6; turn++) {
            PlayCard formation = formationPlay();
            if (formation != null) { PlayCard f = formation; record("formation", () -> { drag(f); sleep(4200); }); break; }
            if (!dragBestUnit(false)) break;
            sleep(2800); waitForTurn();
        }
        waitForTurn();
        record("cast", () -> {
            CardInstance spell = state.view(ME).hand().stream().filter(c -> c.def().kind() == CardKind.SPELL
                    && !Rules.takesTarget(c.def().effect())).findFirst()
                    .orElse(state.view(ME).hand().stream().filter(c -> c.def().kind() == CardKind.SPELL).findFirst().orElseThrow());
            int index = fx(() -> table.shownHand().indexOf(spell.id()));
            click(fx(() -> centre(table.handBounds(index))));
            sleep(900);
            click(fx(() -> centre(table.castBounds())));
            sleep(700);
            if (Rules.takesTarget(spell.def().effect())) {
                var target = state.view(ME).legalPlays().stream().filter(p -> p.cardId() == spell.id() && p.targetId() != null).findFirst();
                if (target.isPresent()) {
                    long id = target.get().targetId();
                    RowCard foe = state.view(ME).opponent().row().find(id), own = state.view(ME).you().row().find(id);
                    Point2D at = fx(() -> centre(foe != null ? table.slotBounds(TableZone.OPPONENT, foe.slot()) : own != null
                            ? table.slotBounds(TableZone.OWN, own.slot()) : table.handBounds(table.shownHand().indexOf(id))));
                    click(at);
                }
            }
            sleep(4200);
        });
        waitForTurn();
        record("swap", () -> {
            click(fx(() -> centre(table.swapBounds())));
            sleep(700);
            for (int i : new int[] {1, 3}) { int at = i; click(fx(() -> centre(table.handBounds(at)))); sleep(600); }
            sleep(400);
            click(fx(() -> centre(table.swapBounds())));
            sleep(3000);
        });
        finish();
    }

    /** Win: everyone plays on quickly (the bot only passes in round 3); the final play and result are recorded. */
    static void finish() {
        pointer = null;
        // The last two plays of round 3 are played in full; the earlier ones fast-forward.
        while (!(state.round() == 3 && state.phase() == MatchPhase.IN_PROGRESS && state.view(ME).you().playsRemaining() <= 2)) {
            Seat seat = state.awaits(ME) ? ME : Seat.SECOND;
            state = state.apply(seat, botMove(seat));
            show(true);
            sleep(30);
        }
        show(false);
        sleep(1500);
        record("win", () -> {
            while (state.phase() != MatchPhase.FINISHED) {
                Seat seat = state.awaits(ME) ? ME : Seat.SECOND;
                state = state.apply(seat, botMove(seat));
                show(false);
                sleep(seat == ME ? 2600 : 300);
            }
            sleep(4500);
        });
        for (String name : List.of("kings", "play", "combos", "formation", "cast", "swap", "win"))
            if (new File(out, name).exists()) System.out.println(name + " " + new File(out, name).list().length);
        Platform.exit();
        System.exit(0);
    }

    /** Bot moves for both seats; the opponent only passes in round 3, so the clip ends in a win. */
    static Move botMove(Seat seat) {
        PlayerView acting = state.view(seat);
        return seat != ME && state.round() == 3 && acting.canSkip() ? new SkipTurn() : GreedyBot.choose(acting);
    }

    /** The player's own move as the server would take it, then the bot's replies after a pause. */
    static synchronized void mine(Move move) {
        try {
            state = state.apply(ME, move);
            show(false);
            while (state.phase() == MatchPhase.IN_PROGRESS && state.turn() == Seat.SECOND) {
                sleep(1400);
                state = state.apply(Seat.SECOND, GreedyBot.choose(state.view(Seat.SECOND)));
                show(false);
            }
        } catch (RuleViolation rejected) { System.out.println("rejected " + move + ": " + rejected.getMessage()); }
    }

    static void show(boolean reducedMotion) {
        MatchUpdate update = new MatchUpdate("CLIP", 1, "You", "Bot", true, true, false, false, false, state.view(ME),
                System.currentTimeMillis() + 30_000, state.closingRoundView(ME));
        var shown = new CardTableView.State(update, false, reducedMotion);
        Platform.runLater(() -> table.show(shown));
    }

    static void waitForTurn() {
        for (int i = 0; i < 100 && !(state.view(ME).yourTurn() && fx(() -> !table.animating() && !table.scoring())); i++) sleep(100);
        sleep(400);
    }

    static PlayCard formationPlay() {
        PlayerView view = state.view(ME);
        for (PlayCard play : view.legalPlays()) {
            CardInstance card = view.hand().stream().filter(c -> c.id() == play.cardId()).findFirst().orElseThrow();
            if (card.def().kind() != CardKind.UNIT || play.slot() < 0) continue;
            Row row = Rules.resolve(card, play, view.hand(), view.board()).board().own();
            if (Scoring.score(row).formation() != null && view.you().score().formation() == null) return play;
        }
        return null;
    }

    static boolean dragBestUnit(boolean combo) {
        PlayerView view = state.view(ME);
        PlayCard best = null; long bestValue = Long.MIN_VALUE;
        for (PlayCard play : view.legalPlays()) {
            CardInstance card = view.hand().stream().filter(c -> c.id() == play.cardId()).findFirst().orElseThrow();
            if (card.def().kind() != CardKind.UNIT) continue;
            var score = Scoring.score(Rules.resolve(card, play, view.hand(), view.board()).board().own());
            long value = combo ? score.combos() * 100 + score.total() : score.total();
            if (value > bestValue) { bestValue = value; best = play; }
        }
        if (best == null) return false;
        drag(best);
        return true;
    }

    static void drag(PlayCard play) {
        int index = fx(() -> table.shownHand().indexOf(play.cardId()));
        Point2D from = fx(() -> centre(table.handBounds(index)));
        Point2D to = fx(() -> centre(table.slotBounds(TableZone.OWN, play.slot())));
        point(from, 500);
        fx(() -> { table.pressAt(from); return null; });
        for (int i = 1; i <= 14; i++) {
            Point2D at = from.add(to.subtract(from).multiply(i / 14.0));
            pointer = at;
            fx(() -> { table.dragTo(at); return null; });
            sleep(45);
        }
        fx(() -> { table.releaseAt(to); return null; });
    }

    static void click(Point2D at) {
        point(at, 500);
        pressedLook = true;
        fx(() -> { table.moveTo(at); table.pressAt(at); table.releaseAt(at); return null; });
        sleep(150);
        pressedLook = false;
    }

    /** Glides the drawn pointer to {@code to}, hovering the table as it goes. */
    static void point(Point2D to, int millis) {
        Point2D from = pointer == null ? new Point2D(640, 600) : pointer;
        int steps = Math.max(1, millis / 40);
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps, ease = t * t * (3 - 2 * t);
            Point2D at = from.add(to.subtract(from).multiply(ease));
            pointer = at;
            fx(() -> { table.moveTo(at); return null; });
            sleep(40);
        }
    }

    static Point2D centreOf(String selector) {
        var node = table.lookup(selector);
        var b = node.localToScene(node.getBoundsInLocal());
        return new Point2D(b.getCenterX(), b.getCenterY());
    }

    static Point2D centre(java.awt.Rectangle r) { return new Point2D(r.getCenterX(), r.getCenterY()); }

    static BufferedImage withPointer(BufferedImage shot) {
        Point2D at = pointer;
        if (at == null) return shot;
        Graphics2D g = shot.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(SCALE, SCALE);
        int x = (int) at.getX(), y = (int) at.getY();
        int[] xs = {x, x, x + 7, x + 11, x + 14, x + 10, x + 17};
        int[] ys = {y, y + 22, y + 16, y + 25, y + 23, y + 14, y + 14};
        if (pressedLook) { g.setColor(new Color(244, 191, 96, 150)); g.fillOval(x - 16, y - 16, 32, 32); }
        g.setColor(Color.WHITE); g.fillPolygon(xs, ys, xs.length);
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1.6f)); g.drawPolygon(xs, ys, xs.length);
        g.dispose();
        return shot;
    }

    interface Script { void run() throws Exception; }

    static void record(String name, Script script) {
        frames.clear();
        recording = true;
        try { script.run(); } catch (Exception e) { throw new RuntimeException(e); }
        recording = false;
        sleep(150);
        File dir = new File(out, name); dir.mkdirs();
        List<BufferedImage> copy = new ArrayList<>(frames);
        for (int i = 0; i < copy.size(); i++) {
            try { ImageIO.write(copy.get(i), "png", new File(dir, String.format("f%04d.png", i))); } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    static void sleep(long millis) { try { Thread.sleep(millis); } catch (InterruptedException e) { throw new RuntimeException(e); } }

    static <T> T fx(Callable<T> work) {
        if (Platform.isFxApplicationThread()) try { return work.call(); } catch (Exception e) { throw new RuntimeException(e); }
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> { try { result.complete(work.call()); } catch (Throwable t) { result.completeExceptionally(t); } });
        try { return result.get(); } catch (Exception e) { throw new RuntimeException(e); }
    }
}

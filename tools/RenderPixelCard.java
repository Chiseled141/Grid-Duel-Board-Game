package deckduel.client.ui;

import deckduel.cards.CatalogLoader;
import deckduel.client.ClientSettings;
import deckduel.client.ScoringSounds;
import deckduel.core.card.*;
import deckduel.net.MatchUpdate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/** Reproducible preview of the same pixel face and JavaFX table used by the client. */
public final class RenderPixelCard {
    private static final Catalog CATALOG = CatalogLoader.loadBundled();
    private static final CardDef CARD = CATALOG.card("pl-knight");

    /** Exports PNG evidence to the first argument; optional --show keeps the preview open. */
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "art/cards/concepts/java-pixel" : args[0]);
        Files.createDirectories(output);
        for (CardDef card : CATALOG.cards().stream().filter(PixelCardFace::supports).toList()) {
            ImageIO.write(PixelCardFace.render(card), "png", output.resolve(card.id() + "-native.png").toFile());
            ImageIO.write(CardFace.sprite(card, List.of(), 320, 480, false, 1), "png",
                    output.resolve(card.id() + "-rendered.png").toFile());
        }
        ImageIO.write(CardFace.backSprite(320, 480, 1), "png", output.resolve("card-back-rendered.png").toFile());
        if (List.of(args).contains("--native")) return;
        for (CardDef card : CATALOG.cards()) CardImages.shared().decode(card.id());
        var previous = new java.util.HashMap<String, java.awt.image.BufferedImage>();
        for (String id : List.of("pl-bowmen", "pl-riders")) {
            Path file = output.resolve("before/" + id + ".png");
            if (Files.isRegularFile(file)) previous.put(id, ImageIO.read(file.toFile()));
        }
        boolean show = List.of(args).contains("--show");
        FxKit.start();
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                Pane preview = preview();
                new Scene(preview, 880, 610);
                ImageIO.write(SwingFXUtils.fromFXImage(preview.snapshot(null, null), null), "png",
                        output.resolve("javafx-preview.png").toFile());
                gallery(output, "nations-preview.png", "KNIGHTS / SEVEN NATIONS / COORDINATED COLOURS",
                        List.of("pl-knight", "hu-knight", "au-knight", "bo-knight", "st-knight", "cr-knight", "tr-knight"));
                gallery(output, "axemen-preview.png", "AXEMEN / SEVEN NATIONS / FOUR POINTS",
                        List.of("pl-axemen", "hu-axemen", "au-axemen", "bo-axemen", "st-axemen", "cr-axemen", "tr-axemen"));
                gallery(output, "bowmen-preview.png", "BOWMEN / UPRIGHT LONGBOW / SEVEN NATIONS",
                        List.of("pl-bowmen", "hu-bowmen", "au-bowmen", "bo-bowmen", "st-bowmen", "cr-bowmen", "tr-bowmen"));
                gallery(output, "riders-preview.png", "RIDERS / MOUNTED PORTRAIT / SEVEN NATIONS",
                        List.of("pl-riders", "hu-riders", "au-riders", "bo-riders", "st-riders", "cr-riders", "tr-riders"));
                readabilityPreview(output, previous);
                unitGrid(output);
                gallery(output, "poland-preview.png", "APPROVED / THREE NEW FIGURES + ORIGINAL KNIGHTS AND AXEMEN",
                        List.of("pl-pikemen", "pl-bowmen", "pl-riders", "pl-knight", "pl-axemen"));
                spellGrid(output);
                renderTable(output);
                if (show) {
                    Stage stage = new Stage();
                    stage.setTitle("Deck Duel — Java pixel card trial");
                    stage.setScene(preview.getScene());
                    stage.setOnHidden(event -> Platform.exit());
                    stage.show();
                }
                done.complete(null);
            } catch (Throwable failure) { done.completeExceptionally(failure); }
        });
        try { done.get(30, TimeUnit.SECONDS); }
        finally { if (!show || done.isCompletedExceptionally()) Platform.exit(); }
    }

    private static void readabilityPreview(Path output, java.util.Map<String, java.awt.image.BufferedImage> previous) throws Exception {
        if (previous.size() != 2) return;
        Canvas canvas = new Canvas(1200, 740);
        var g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#172D30")); g.fillRect(0, 0, 1200, 740);
        g.setFill(FxKit.CREAM); g.setFont(FxKit.bold(22));
        g.fillText("BOWMEN + RIDERS / BEFORE AND AFTER", 30, 34);
        g.setFont(FxKit.normal(13));
        g.fillText("Top: 3x native grid. Below: 104 x 156 card size. JavaFX / production card renderer.", 30, 58);
        for (int col = 0; col < 4; col++) {
            String id = col < 2 ? "pl-bowmen" : "pl-riders";
            int x = 30 + col * 294;
            boolean before = col % 2 == 0;
            var card = CATALOG.card(id);
            if (before) {
                g.setImageSmoothing(false);
                g.drawImage(FxKit.convert(previous.get(id)), x, 82, 240, 360);
                g.drawImage(FxKit.convert(previous.get(id)), x + 68, 505, 104, 156);
            } else {
                FxKit.drawFace(g, card, List.of(), 240, 360, false, 1, x, 82);
                FxKit.drawFace(g, card, List.of(), 104, 156, false, 1, x + 68, 505);
            }
            g.setFill(before ? FxKit.MUTED : FxKit.CREAM); g.setFont(FxKit.bold(16));
            g.fillText((before ? "BEFORE / " : "AFTER / ") + card.unitClass().toUpperCase(), x, 473);
            g.setFill(FxKit.MUTED); g.setFont(FxKit.normal(12));
            g.fillText("104 x 156", x + 85, 689);
        }
        Pane pane = new Pane(canvas);
        new Scene(pane, 1200, 740);
        ImageIO.write(SwingFXUtils.fromFXImage(pane.snapshot(null, null), null), "png",
                output.resolve("before-after.png").toFile());
    }

    private static void gallery(Path output, String file, String title, List<String> ids) throws Exception {
        int cardWidth = ids.size() > 5 ? 160 : 240, cardHeight = cardWidth * 3 / 2;
        int step = cardWidth + 48, width = 48 + step * ids.size(), height = cardHeight + 200;
        Canvas canvas = new Canvas(width, height);
        var g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#172D30")); g.fillRect(0, 0, width, height);
        g.setFill(FxKit.CREAM); g.setFont(FxKit.bold(20));
        g.fillText(title, 36, 34);
        g.setFill(FxKit.MUTED); g.setFont(FxKit.normal(13));
        g.fillText("DECK DUEL  /  JAVA PIXEL ART  /  NATIVE 80 x 120", 36, 58);
        for (int i = 0; i < ids.size(); i++) {
            CardDef card = CATALOG.card(ids.get(i));
            int x = 36 + step * i;
            FxKit.drawFace(g, card, List.of(), cardWidth, cardHeight, false, 1, x, 84);
            g.setFill(FxKit.CREAM); g.setFont(FxKit.bold(ids.size() > 5 ? 12 : 16));
            g.fillText(card.name(), x, cardHeight + 117);
            g.setFill(FxKit.MUTED); g.setFont(FxKit.normal(ids.size() > 5 ? 11 : 14));
            int y = cardHeight + 140;
            String line = "";
            for (String word : card.text().split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (FxKit.width(next, g.getFont()) > cardWidth + 8) {
                    g.fillText(line, x, y); y += 19; line = word;
                } else line = next;
            }
            g.fillText(line, x, y);
        }
        Pane pane = new Pane(canvas);
        new Scene(pane, width, height);
        ImageIO.write(SwingFXUtils.fromFXImage(pane.snapshot(null, null), null), "png", output.resolve(file).toFile());
    }

    private static void unitGrid(Path output) throws Exception {
        var nations = List.of("pl", "hu", "au", "bo", "st", "cr", "tr");
        var classes = List.of("pikemen", "bowmen", "riders", "knight", "axemen");
        Canvas canvas = new Canvas(1040, 2130);
        var g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#172D30")); g.fillRect(0, 0, 1040, 2130);
        g.setFill(FxKit.CREAM); g.setFont(FxKit.bold(22));
        g.fillText("ALL 35 UNITS / RENDERED FROM JAVA CODE", 28, 34);
        g.setFont(FxKit.normal(13));
        g.fillText("7 nation palettes and crests / 5 class silhouettes / catalog points", 28, 58);
        for (int row = 0; row < nations.size(); row++) for (int col = 0; col < classes.size(); col++) {
            var card = CATALOG.card(nations.get(row) + "-" + classes.get(col));
            int x = 28 + col * 204, y = 82 + row * 290;
            FxKit.drawFace(g, card, List.of(), 160, 240, false, 1, x, y);
            g.setFill(FxKit.CREAM); g.setFont(FxKit.normal(12));
            g.fillText(card.name(), x, y + 261);
        }
        Pane pane = new Pane(canvas);
        new Scene(pane, 1040, 2130);
        ImageIO.write(SwingFXUtils.fromFXImage(pane.snapshot(null, null), null), "png",
                output.resolve("all-units.png").toFile());
    }

    private static void spellGrid(Path output) throws Exception {
        var spells = CATALOG.cards().stream().filter(card -> card.kind() == CardKind.SPELL).toList();
        Canvas canvas = new Canvas(1120, 940);
        var g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#172D30")); g.fillRect(0, 0, 1120, 940);
        g.setFill(FxKit.CREAM); g.setFont(FxKit.bold(22));
        g.fillText("DECK DUEL / NEUTRAL SPELLS", 28, 34);
        g.setFill(FxKit.MUTED); g.setFont(FxKit.normal(13));
        g.fillText("pxl artwork / 80 x 120 native pixels / production JavaFX renderer", 28, 59);
        for (int i = 0; i < spells.size(); i++) {
            CardDef card = spells.get(i);
            int x = 28 + i % 5 * 220, y = 84 + i / 5 * 340;
            FxKit.drawFace(g, card, List.of(), 192, 288, false, 1, x, y);
            g.setFill(FxKit.CREAM); g.setFont(FxKit.bold(14));
            g.fillText(card.name(), x, y + 313);
        }
        FxKit.drawBack(g, 80, 120, 1, 28, 788);
        g.setFill(FxKit.CREAM); g.setFont(FxKit.bold(16));
        g.fillText("SHARED CARD BACK", 136, 822);
        g.setFill(FxKit.MUTED); g.setFont(FxKit.normal(13));
        g.fillText("Same design for every hidden card. Existing 35 Unit figures retained.", 136, 850);
        Pane pane = new Pane(canvas);
        new Scene(pane, 1120, 940);
        ImageIO.write(SwingFXUtils.fromFXImage(pane.snapshot(null, null), null), "png",
                output.resolve("spells-preview.png").toFile());
    }

    private static Pane preview() {
        Canvas canvas = new Canvas(880, 610);
        var g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#172D30")); g.fillRect(0, 0, 880, 610);
        g.setFont(Font.font("Monospaced", 18)); g.setFill(FxKit.CREAM);
        g.fillText("DECK DUEL / PIXEL CARD TRIAL", 32, 35);
        g.setFont(Font.font("Monospaced", 12));
        g.fillText("80 x 120 native grid / Java renderer / no image assets", 32, 57);
        FxKit.drawFace(g, CARD, List.of(), 320, 480, false, 1, 32, 84);
        FxKit.drawFace(g, CARD, List.of(), 160, 240, true, 1, 400, 84);
        FxKit.drawFace(g, CARD, List.of(Suppression.TAGS), 160, 240, false, 1, 600, 84);
        FxKit.drawFace(g, CARD, List.of(), 80, 120, false, 1, 400, 390);
        g.fillText("SELECTED / 2x", 400, 352);
        g.fillText("TAGS DISABLED / 2x", 600, 352);
        g.fillText("NATIVE / 1x", 400, 538);
        g.fillText("4x — SAME FACE AS THE MATCH TABLE", 32, 590);
        Pane pane = new Pane(canvas);
        pane.setPrefSize(880, 610);
        return pane;
    }

    private static MatchState sampleState() {
        var deck = DeckRules.deck(CATALOG);
        for (long seed = 1; seed <= 100; seed++) {
            var state = MatchState.start(deck, deck, seed);
            Seat seat = state.turn();
            var knight = state.view(seat).hand().stream().filter(c -> c.def().id().equals(CARD.id())).findFirst();
            if (knight.isPresent()) return state.apply(seat, PlayCard.place(knight.get().id(), 3));
        }
        throw new IllegalStateException("No seeded sample hand found");
    }

    private static void renderTable(Path output) throws Exception {
        MatchState state = sampleState();
        Seat seat = state.turn().other();
        var settings = new ClientSettings();
        settings.setReducedMotion(true);
        CardTableView table = new CardTableView(new CardTableView.Actions() {
            public void move(Move move) { }
            public void rematch() { }
            public void leave() { }
        }, settings, new ScoringSounds(() -> false));
        try {
            new Scene(table, 1280, 800).getStylesheets().add(FxKit.STYLESHEET);
            ((ImageView) table.getChildren().get(0)).setImage(new Image(
                    RenderPixelCard.class.getResource("/assets/match-room.png").toExternalForm(), false));
            table.resize(1280, 800);
            table.show(new CardTableView.State(new MatchUpdate("Pixel trial", 1, "You", "Practice bot",
                    true, true, false, false, false, state.view(seat)), false, true));
            table.applyCss(); table.layout();
            ImageIO.write(SwingFXUtils.fromFXImage(table.snapshot(null, null), null), "png",
                    output.resolve("match-table.png").toFile());
        } finally { table.hidden(); }
    }
}

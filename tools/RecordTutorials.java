import deckduel.cards.CatalogLoader;
import deckduel.core.card.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Records reproducible teaching assets from accepted engine moves; never reads a player database. */
public final class RecordTutorials {
    private static final List<CardDef> DECK = DeckRules.deck(CatalogLoader.loadBundled());
    private static final Path OUTPUT = Path.of("src/main/resources/tutorial");

    private static final class Recording {
        final long seed;
        MatchState state;
        final List<String> actions = new ArrayList<>();
        int clip;

        Recording(long seed) { this.seed = seed; state = MatchState.start(DECK, DECK, seed); }
        PlayerView view() { return state.view(Seat.FIRST); }
        void mark() { clip = actions.size(); }
        void move(Seat seat, Move move) {
            state = state.apply(seat, move);
            actions.add(seat + " " + encode(move));
        }
        void firstTurn() {
            if (state.turn() != Seat.FIRST) move(Seat.SECOND, new SkipTurn());
        }
        void kings(boolean edict) {
            var offers = view().kingOffer();
            King own = edict ? King.NETHRION : offers.stream().filter(k -> k != King.NETHRION).findFirst().orElseThrow();
            move(Seat.FIRST, new ChooseKing(own));
            move(Seat.SECOND, new ChooseKing(offers.stream().filter(k -> k != own).findFirst().orElseThrow()));
            firstTurn();
        }
        void put(CardInstance card, int slot) {
            firstTurn(); move(Seat.FIRST, PlayCard.place(card.id(), slot));
        }
        void save(String id) throws Exception {
            String text = "DECK_DUEL_TUTORIAL:1\nseed " + seed + "\nclip " + clip + "\n" + String.join("\n", actions) + "\n";
            Files.writeString(OUTPUT.resolve(id + ".duel"), text, StandardCharsets.UTF_8);
            System.out.println(id + ": " + (actions.size() - clip + 1) + " frames; " + actions.size() + " validated actions");
        }
    }

    private static String encode(Move move) {
        if (move instanceof ChooseKing king) return "K " + king.king();
        if (move instanceof DrawCard) return "D";
        if (move instanceof SkipTurn) return "S";
        if (move instanceof KeepCard keep) return "R " + keep.cardId();
        if (move instanceof KeepEdictCard keep) return "E " + keep.cardId();
        if (move instanceof PlayCard play) return "P " + play.cardId() + " " + play.slot() + " " + (play.targetId() == null ? "-" : play.targetId());
        throw new IllegalArgumentException("Unrecorded move " + move);
    }

    private static Recording find(Predicate<MatchState> predicate) {
        for (long seed = 0; seed < 50_000; seed++) {
            var match = MatchState.start(DECK, DECK, seed);
            if (predicate.test(match)) return new Recording(seed);
        }
        throw new IllegalStateException("No training deal found");
    }

    private static List<CardInstance> units(PlayerView view) {
        return view.hand().stream().filter(card -> card.def().occupiesSpace()).toList();
    }

    private static List<CardInstance> comboCards(PlayerView view) {
        List<CardInstance> units = units(view);
        for (CardInstance anchor : units) {
            var group = units.stream().filter(c -> c.def().unitClass().equals(anchor.def().unitClass()))
                    .sorted(java.util.Comparator.comparing(c -> c.id() != anchor.id())).limit(3).toList();
            var other = units.stream().filter(c -> c.def().nation().equals(anchor.def().nation())
                    && !c.def().unitClass().equals(anchor.def().unitClass())).findFirst();
            if (group.size() == 3 && other.isPresent()) return List.of(group.get(0), other.get(), group.get(1), group.get(2));
        }
        return List.of();
    }

    private static void combos() throws Exception {
        var record = find(state -> comboCards(state.view(Seat.FIRST)).size() == 4);
        var cards = comboCards(record.view());
        record.kings(false);
        record.mark();
        record.put(cards.get(0), 0); record.put(cards.get(1), 1); record.put(cards.get(2), 2);
        record.save("combos");
        record.firstTurn(); record.mark(); record.put(cards.get(3), 3);
        record.save("formations");
    }

    private static void replacement() throws Exception {
        var record = find(state -> units(state.view(Seat.FIRST)).size() >= 8);
        var cards = units(record.view()); record.kings(false);
        for (int i = 0; i < 6; i++) record.put(cards.get(i), i);
        record.firstTurn(); record.mark();
        record.put(cards.get(6), 6); record.put(cards.get(7), 0);
        record.save("replacement");
    }

    private static void drawAndKings() throws Exception {
        var record = find(state -> true); record.kings(false); record.mark();
        for (int i = 0; i < 3; i++) record.move(Seat.FIRST, new DrawCard());
        record.put(units(record.view()).get(0), 0); record.firstTurn();
        record.move(Seat.FIRST, new SkipTurn()); record.save("draw-skip");
        record = find(state -> state.view(Seat.FIRST).kingOffer().contains(King.NETHRION));
        record.kings(true); record.move(Seat.FIRST, new DrawCard());
        record.move(Seat.FIRST, new KeepEdictCard(record.view().looking().get(0).id()));
        record.save("kings");
    }

    private static CardInstance spell(PlayerView view, String id) {
        return view.hand().stream().filter(c -> c.def().id().equals(id)).findFirst().orElseThrow();
    }
    private static boolean has(PlayerView view, String id) {
        return view.hand().stream().anyMatch(c -> c.def().id().equals(id));
    }

    private static void spells() throws Exception {
        for (CardDef spell : DECK.stream().filter(card -> !card.occupiesSpace()).toList()) {
            String id = spell.id();
            var record = find(state -> has(state.view(Seat.FIRST), id)
                    && !units(state.view(Seat.FIRST)).isEmpty() && !units(state.view(Seat.SECOND)).isEmpty()
                    && (!id.equals("clear-disable") || has(state.view(Seat.SECOND), "disable-tags")));
            record.kings(false);
            var ownUnit = units(record.view()).get(0);
            record.put(ownUnit, 0);
            var enemyView = record.state.view(Seat.SECOND);
            var enemyUnit = units(enemyView).get(0);
            if (id.equals("clear-disable"))
                record.move(Seat.SECOND, PlayCard.cast(spell(enemyView, "disable-tags").id(), ownUnit.id()));
            else record.move(Seat.SECOND, PlayCard.place(enemyUnit.id(), 0));
            record.mark();
            Long target = switch (id) {
                case "disable-tags", "disable-points", "destroy-unit" -> enemyUnit.id();
                case "clear-disable" -> ownUnit.id();
                case "exchange" -> units(record.view()).get(0).id();
                default -> null;
            };
            record.move(Seat.FIRST, PlayCard.cast(spell(record.view(), id).id(), target));
            if (record.view().keeping()) record.move(Seat.FIRST, new KeepCard(record.view().looking().get(0).id()));
            record.save(id);
        }
    }

    private static void fullMatch() throws Exception {
        var record = find(state -> true);
        while (record.view().phase() != MatchPhase.FINISHED) {
            if (record.view().phase() == MatchPhase.KING_SELECTION) {
                for (Seat seat : Seat.values()) if (record.state.view(seat).choosingKing())
                    record.move(seat, GreedyBot.choose(record.state.view(seat)));
            } else record.move(record.state.turn(), GreedyBot.choose(record.state.view(record.state.turn())));
            if (record.actions.size() == 7) { record.clip = 0; record.save("basics"); }
            if (record.view().round() == 1) record.clip = record.actions.size();
        }
        // Include the action before the first bank, then continue through rounds 2 and 3.
        record.clip = Math.max(0, record.clip - 1);
        record.save("rounds");
    }

    /** Run from the repository root after compiling the production core; see docs/how-to-play.md. */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        combos(); replacement(); drawAndKings(); spells(); fullMatch();
    }
}

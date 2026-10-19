package deckduel.cards;

import deckduel.core.card.CardDef;
import deckduel.core.card.CardKind;
import deckduel.core.card.Catalog;
import deckduel.core.card.Effect;
import deckduel.core.card.Suppression;
import deckduel.core.card.TagType;
import deckduel.core.card.TargetSide;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Parses the pipe-separated card configuration into an immutable {@link Catalog}. It runs once
 * at startup, outside core; matches receive the resulting snapshot and nothing reloads mid-match.
 */
public final class CatalogLoader {
    /** Classpath location of the bundled catalog. */
    public static final String BUNDLED = "/cards/catalog.txt";

    private CatalogLoader() { }

    /** Loads the bundled catalog. */
    public static Catalog loadBundled() {
        var stream = CatalogLoader.class.getResourceAsStream(BUNDLED);
        if (stream == null) throw new IllegalStateException("Missing card catalog " + BUNDLED);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Parses and validates a catalog; errors name the offending line. */
    public static Catalog parse(Reader source) throws IOException {
        String version = null;
        List<String> regions = List.of();
        List<CardDef> cards = new ArrayList<>();
        BufferedReader reader = new BufferedReader(source);
        int number = 0;
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            number++;
            if (line.isBlank() || line.startsWith("#")) continue;
            List<String> fields = Arrays.stream(line.split("\\|")).map(String::trim).toList();
            try {
                switch (fields.get(0)) {
                    case "version" -> version = field(fields, 1);
                    case "regions" -> regions = list(field(fields, 1));
                    case "unit" -> cards.add(new CardDef(field(fields, 1), field(fields, 2), CardKind.UNIT,
                            Integer.parseInt(field(fields, 3)), list(field(fields, 4)), list(field(fields, 5)), null));
                    case "landmark", "spell" -> cards.add(new CardDef(field(fields, 1), field(fields, 2),
                            CardKind.valueOf(fields.get(0).toUpperCase(Locale.ROOT)), 0,
                            List.of(), List.of(), effect(field(fields, 3))));
                    default -> throw new IllegalArgumentException("Unknown entry " + fields.get(0));
                }
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Card catalog line " + number + ": " + exception.getMessage(), exception);
            }
        }
        return new Catalog(version, regions, cards);
    }

    private static Effect effect(String text) {
        String[] word = text.split("\\s+");
        return switch (word[0]) {
            case "SHARED_TAG_BONUS" -> new Effect.SharedTagBonus(TagType.valueOf(word[1]), number(word, 2));
            case "WEAK_UNIT_BONUS" -> new Effect.WeakUnitBonus(number(word, 1), number(word, 2));
            case "EMPTY_SPACE_BONUS" -> new Effect.EmptySpaceBonus(number(word, 1));
            case "FIRST_SPELL_DRAW" -> new Effect.FirstSpellDraw(number(word, 1));
            case "DECK_TOP_VIEW" -> new Effect.DeckTopView();
            case "DISABLE" -> new Effect.Disable(TargetSide.valueOf(word[1]), Suppression.valueOf(word[2]));
            case "DESTROY" -> new Effect.Destroy(TargetSide.valueOf(word[1]));
            case "CLEAR_DISABLE" -> new Effect.ClearDisable();
            case "RANDOM_DISCARD" -> new Effect.RandomDiscard(number(word, 1));
            case "RANDOM_STEAL" -> new Effect.RandomSteal(number(word, 1));
            case "DRAW" -> new Effect.Draw(number(word, 1));
            case "EXCHANGE" -> new Effect.Exchange(number(word, 1), number(word, 2));
            case "RECONNAISSANCE" -> new Effect.Reconnaissance(number(word, 1));
            case "SABOTAGE" -> new Effect.Sabotage();
            case "SPY" -> new Effect.Spy(number(word, 1));
            default -> throw new IllegalArgumentException("Unknown effect " + word[0]);
        };
    }

    private static int number(String[] words, int index) {
        if (index >= words.length) throw new IllegalArgumentException("Missing value in effect " + String.join(" ", words));
        return Integer.parseInt(words[index]);
    }

    private static String field(List<String> fields, int index) {
        if (index >= fields.size() || fields.get(index).isEmpty()) throw new IllegalArgumentException("Missing field " + index);
        return fields.get(index);
    }

    private static List<String> list(String value) {
        return Arrays.stream(value.split(";")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }
}

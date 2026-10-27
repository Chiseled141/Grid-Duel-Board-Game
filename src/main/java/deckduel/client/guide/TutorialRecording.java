package deckduel.client.guide;

import deckduel.core.card.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads bundled training action recordings, validating every transition through the real engine. */
final class TutorialRecording {
    private TutorialRecording() { }

    static List<PlayerView> load(String id, Catalog catalog) throws IOException {
        try (InputStream input = TutorialRecording.class.getResourceAsStream("/tutorial/" + id + ".duel")) {
            if (input == null) throw new IOException("Missing training recording: " + id);
            return read(input, catalog);
        }
    }

    static List<PlayerView> read(InputStream input, Catalog catalog) throws IOException {
        var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        if (!"DECK_DUEL_TUTORIAL:1".equals(reader.readLine())) throw new IOException("Unknown training format");
        try {
            long seed = Long.parseLong(value(reader.readLine(), "seed"));
            int clip = Integer.parseInt(value(reader.readLine(), "clip"));
            var deck = DeckRules.deck(catalog);
            MatchState state = MatchState.start(deck, deck, seed);
            List<PlayerView> frames = new ArrayList<>(); frames.add(state.view(Seat.FIRST));
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank() || line.startsWith("#")) continue;
                if (frames.size() > 512) throw new IOException("Training recording too long");
                String[] fields = line.split(" ");
                state = state.apply(Seat.valueOf(fields[0]), move(fields));
                frames.add(state.view(Seat.FIRST));
            }
            if (clip < 0 || clip >= frames.size() - 1) throw new IOException("Empty training excerpt");
            return List.copyOf(frames.subList(clip, frames.size()));
        } catch (IllegalArgumentException | IndexOutOfBoundsException | RuleViolation invalid) {
            throw new IOException("Invalid training action", invalid);
        }
    }

    private static String value(String line, String key) throws IOException {
        if (line == null || !line.startsWith(key + " ")) throw new IOException("Missing " + key);
        return line.substring(key.length() + 1);
    }

    private static Move move(String[] fields) {
        return switch (fields[1]) {
            case "K" -> new ChooseKing(King.valueOf(fields[2]));
            case "D" -> new DrawCard();
            case "S" -> new SkipTurn();
            case "R" -> new KeepCard(Long.parseLong(fields[2]));
            case "E" -> new KeepEdictCard(Long.parseLong(fields[2]));
            case "P" -> new PlayCard(Long.parseLong(fields[2]), Integer.parseInt(fields[3]),
                    fields[4].equals("-") ? null : Long.valueOf(fields[4]));
            default -> throw new IllegalArgumentException("Unknown training action");
        };
    }
}

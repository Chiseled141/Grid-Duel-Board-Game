package deckduel.net;

import deckduel.core.card.SkipTurn;

import deckduel.cards.CatalogLoader;
import deckduel.core.card.DeckRules;
import deckduel.core.card.DrawCard;
import deckduel.core.card.GreedyBot;
import deckduel.core.card.KeepCard;
import deckduel.core.card.MatchPhase;
import deckduel.core.card.MatchState;
import deckduel.core.card.PlayCard;
import deckduel.core.card.Seat;
import java.io.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageSerializationTest {
    static List<Message> messages() {
        var user = new UserProfile("alice", 0, 0);
        var catalog = CatalogLoader.loadBundled();
        var deck = DeckRules.deck(catalog);
        // A real view after a played round exercises every card contract that crosses the wire.
        var state = MatchState.start(deck, deck, 5);
        while (state.round() == 1 && state.phase() != MatchPhase.FINISHED) {
            Seat seat = state.awaits(Seat.FIRST) ? Seat.FIRST : Seat.SECOND;
            state = state.apply(seat, GreedyBot.choose(state.view(seat)));
        }
        var view = state.view(Seat.FIRST);
        return List.of(new RegisterRequest("alice", "secret"), new LoginRequest("alice", "secret"),
                new LoginResponse(true, null, user), new LoginResponse(false, "Invalid login", null),
                new CreateMatchRequest(), new MatchCreated("ABCDE"), new JoinMatchRequest("ABCDE"),
                new FindMatchRequest(true), new MatchmakingStatus(false, "Matchmaking is unavailable"),
                new PracticeRequest(true), new MoveRequest(3, 7, PlayCard.cast(4, 9L)), new MoveRequest(4, 7, new DrawCard()),
                new MoveRequest(6, 8, new KeepCard(12)),
                new MoveRequest(7, 9, new SkipTurn()),
                new ActionRejected(3, "Not your turn"),
                new LeaveMatchRequest(), new RematchRequest(), new ProfileUpdate(user),
                new MatchUpdate("M1", 1, "alice", "bob", false, true, false, false, true, view),
                new ListMatchesRequest(), new ListMatchesResponse(List.of(new MatchSummary("ABCDE", "alice"))),
                new HistoryRequest(), new HistoryResponse(List.of(new HistoryEntry("2026-10-19T10:00:00", "bob",
                        HistoryEntry.Result.DRAW, "DRAW", "cards-neutral-1", List.of(30L), List.of(30L)))),
                new Ping(10), new Pong(10),
                new ErrorMessage("Example"), new ProtocolHello(Message.PROTOCOL_VERSION), new ProtocolReady(Message.PROTOCOL_VERSION));
    }

    @Test void everyEffectTypeAndBothCardFamiliesCanCrossTheWire() throws Exception {
        for (var card : CatalogLoader.loadBundled().cards()) {
            var bytes = new ByteArrayOutputStream();
            try (var out = new ObjectOutputStream(bytes)) { out.writeObject(new deckduel.core.card.CardInstance(1, card)); }
            try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                in.setObjectInputFilter(Message.WIRE_FILTER);
                assertEquals(card, ((deckduel.core.card.CardInstance) in.readObject()).def());
            }
        }
    }

    @Test void everyShellMessageRoundTripsAndUsesTheStableSerialId() throws Exception {
        for (var message : messages()) {
            var bytes = new ByteArrayOutputStream();
            try (var out = new ObjectOutputStream(bytes)) { out.writeObject(message); out.flush(); out.reset(); }
            try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                in.setObjectInputFilter(Message.WIRE_FILTER);
                assertEquals(message, in.readObject());
            }
            assertEquals(1L, ObjectStreamClass.lookup(message.getClass()).getSerialVersionUID());
        }
        var actualTypes = messages().stream().map(Object::getClass).collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(Message.class.getPermittedSubclasses()), actualTypes);
    }
}

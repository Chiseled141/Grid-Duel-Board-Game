package deckduel.net;

import deckduel.SerializationProbe;
import java.io.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageWireFilterTest {
    private Object read(Object value) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(value); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            in.setObjectInputFilter(Message.WIRE_FILTER);
            return in.readObject();
        }
    }
    @Test void foreignClassesRandomStateAndOversizedArraysAreRejected() {
        for (Object value : List.of(new SerializationProbe(), new java.util.Date(), new java.util.Random(), new Object[1001]))
            assertThrows(IOException.class, () -> read(value));
    }
    @Test void supportedListCollectionsRemainReadable() throws Exception {
        var room = new MatchSummary("ABCDE", "alice");
        for (List<MatchSummary> list : List.of(new java.util.ArrayList<>(List.of(room)),
                new java.util.LinkedList<>(List.of(room)), java.util.Arrays.asList(room),
                java.util.Collections.unmodifiableList(new java.util.ArrayList<>(List.of(room))),
                java.util.Collections.singletonList(room), java.util.Collections.<MatchSummary>emptyList())) {
            assertEquals(new ListMatchesResponse(list), read(new ListMatchesResponse(list)));
        }
    }
}

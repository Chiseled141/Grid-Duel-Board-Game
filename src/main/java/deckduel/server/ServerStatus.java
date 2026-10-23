package deckduel.server;

import java.time.Instant;

/**
 * What an operator needs before a restart: whether maintenance is on, connections, open rooms,
 * matches in progress (including final persistence) and seats held for reconnecting players, plus the certificate's expiry.
 */
public record ServerStatus(boolean draining, int connections, int openRooms, int activeMatches, int heldSeats,
                           Instant certificateExpires) {

    /** Restarting now loses no match: maintenance is on and no game or final write is in progress and no seat is held. */
    public boolean safeToRestart() { return draining && activeMatches == 0 && heldSeats == 0 && openRooms == 0; }

    /** One line of {@code key=value} pairs for the operator tool and logs. */
    public String describe() {
        return "state=" + (draining ? "draining" : "open") + " connections=" + connections + " openRooms=" + openRooms
                + " activeMatches=" + activeMatches + " heldSeats=" + heldSeats + " safeToRestart=" + safeToRestart()
                + (certificateExpires == null ? " tls=off" : " certificateExpires=" + certificateExpires);
    }
}

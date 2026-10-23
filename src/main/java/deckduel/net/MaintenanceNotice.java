package deckduel.net;

import java.io.Serial;

/**
 * S→C. The server is (or is no longer) preparing for a restart: while {@code active}, no new
 * rooms, joins, practice matches or rematches start, waiting rooms are closed, and matches in
 * progress finish normally.
 */
public record MaintenanceNotice(boolean active, String text) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;
}

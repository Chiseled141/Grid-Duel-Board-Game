package onitama.net;

import java.io.Serial;
import java.util.List;

/**
 * S→C. The open matches and their hosts, for the lobby browser.
 */
public record ListMatchesResponse(List<MatchSummary> openMatches) implements Message {

    @Serial
    private static final long serialVersionUID = 1L;
}

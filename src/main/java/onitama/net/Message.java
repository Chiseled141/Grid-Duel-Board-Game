package onitama.net;

import java.io.ObjectInputFilter;
import java.io.Serializable;

/**
 * Root type of every object sent between client and server. Each message is
 * one small record carrying only data; one Java-serialized object is written
 * and flushed per message.
 *
 * <p>Message is a {@code sealed} interface (instead of the abstract class the
 * brief sketches) because records — the natural fit for these pure data
 * carriers — cannot extend classes. Sealing keeps the protocol closed: every
 * message type is listed in the permits clause, and adding one is a conscious
 * protocol change.
 */
public sealed interface Message extends Serializable
        permits RegisterRequest, LoginRequest, LoginResponse,
        CreateMatchRequest, MatchCreated, JoinMatchRequest,
        ListMatchesRequest, ListMatchesResponse,
        MatchStart, MoveRequest, MoveApplied, MoveRejected, PassTurn,
        GameOver, RematchRequest, RematchAccept, ResignRequest,
        OpponentLeft, ReconnectRequest, LeaderboardRequest, LeaderboardResponse,
        Ping, Pong, ErrorMessage {

    /**
     * The deserialization filter (JEP 290) every endpoint installs on its
     * {@code ObjectInputStream} immediately after creating it. It is an
     * allowlist: only message records, the {@code core} classes they embed
     * (GameState, Board, Card, ...) and JDK classes may be read — anything
     * else, such as a crafted gadget-chain class, is rejected during
     * {@code readObject} before any of its code can run.
     *
     * <p>Only {@code maxdepth} (per object graph, resets with every
     * {@code readObject}) is used as a size guard: {@code maxrefs} and
     * {@code maxbytes} are cumulative over the whole stream and would reject
     * legitimate traffic once a long-lived connection has carried enough
     * messages.
     */
    ObjectInputFilter WIRE_FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=64;onitama.net.*;onitama.core.*;java.base/*;!*");
}

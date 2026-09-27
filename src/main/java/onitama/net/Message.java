package onitama.net;

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
 * protocol change. See docs/DESIGN_DECISIONS.md.
 */
public sealed interface Message extends Serializable
        permits RegisterRequest, LoginRequest, LoginResponse,
        CreateMatchRequest, MatchCreated, JoinMatchRequest,
        ListMatchesRequest, ListMatchesResponse,
        MatchStart, MoveRequest, MoveApplied, MoveRejected, PassTurn,
        GameOver, RematchRequest, RematchAccept, ResignRequest,
        OpponentLeft, ReconnectRequest, LeaderboardRequest, LeaderboardResponse,
        Ping, Pong, ErrorMessage {
}

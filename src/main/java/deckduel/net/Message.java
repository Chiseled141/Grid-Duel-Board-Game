package deckduel.net;

import java.io.ObjectInputFilter;
import java.io.Serializable;

/** Closed account/lobby protocol; retired gameplay classes are not accepted. */
public sealed interface Message extends Serializable permits CreateMatchRequest, ErrorMessage, FindMatchRequest, JoinMatchRequest, LeaderboardRequest, LeaderboardResponse, ListMatchesRequest, ListMatchesResponse, LoginRequest, LoginResponse, MatchCreated, MatchmakingStatus, Ping, Pong, ProtocolHello, ProtocolReady, RegisterRequest {
    /** Incompatible with the retired board-game protocol. */
    int PROTOCOL_VERSION = 8;
    /** Greeting shared by both peers. */
    String GREETING = "DECK_DUEL_PROTOCOL:" + PROTOCOL_VERSION;
    /** Restricts deserialization to the shell messages and bounded collection values. */
    ObjectInputFilter WIRE_FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=32;maxarray=1000;"
                    + "deckduel.net.CreateMatchRequest;"
                    + "deckduel.net.ErrorMessage;"
                    + "deckduel.net.FindMatchRequest;"
                    + "deckduel.net.JoinMatchRequest;"
                    + "deckduel.net.LeaderboardRequest;"
                    + "deckduel.net.LeaderboardResponse;"
                    + "deckduel.net.ListMatchesRequest;"
                    + "deckduel.net.ListMatchesResponse;"
                    + "deckduel.net.LoginRequest;"
                    + "deckduel.net.LoginResponse;"
                    + "deckduel.net.MatchCreated;"
                    + "deckduel.net.MatchSummary;"
                    + "deckduel.net.MatchmakingStatus;"
                    + "deckduel.net.Ping;"
                    + "deckduel.net.Pong;"
                    + "deckduel.net.ProtocolHello;"
                    + "deckduel.net.ProtocolReady;"
                    + "deckduel.net.RegisterRequest;"
                    + "deckduel.net.UserProfile;"
                    + "java.lang.Object;java.lang.String;java.lang.Integer;java.lang.Number;"
                    + "java.util.CollSer;java.util.ImmutableCollections$List12;java.util.ImmutableCollections$ListN;"
                    + "java.util.ArrayList;java.util.LinkedList;java.util.Arrays$ArrayList;"
                    + "java.util.Collections$UnmodifiableCollection;java.util.Collections$UnmodifiableList;"
                    + "java.util.Collections$UnmodifiableRandomAccessList;java.util.Collections$EmptyList;"
                    + "java.util.Collections$SingletonList;!*");
}

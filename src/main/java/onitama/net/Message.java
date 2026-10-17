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
        CreateMatchRequest, MatchCreated, JoinMatchRequest, FindMatchRequest, MatchmakingStatus,
        ListMatchesRequest, ListMatchesResponse,
        GameOver, RematchRequest, RematchAccept,
        OpponentLeft, ReconnectRequest, LeaderboardRequest, LeaderboardResponse,
        Ping, Pong, ErrorMessage, ChatRequest, ChatMessage,
        ProtocolHello, ProtocolReady, ActionRequest, MatchSync, ActionReply, GameUpdate {

    /**
     * The deserialization filter (JEP 290) installed on every {@code ObjectInputStream}.
     * Specifically rejects full game state, boards, RNG, and pending host state.
     * Enforces depth bounds and per-array length limits.
     */
    ObjectInputFilter WIRE_FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=64;maxarray=1000;"
                    + "onitama.net.RegisterRequest;"
                    + "onitama.net.LoginRequest;"
                    + "onitama.net.LoginResponse;"
                    + "onitama.net.CreateMatchRequest;"
                    + "onitama.net.FindMatchRequest;"
                    + "onitama.net.MatchmakingStatus;"
                    + "onitama.net.MatchCreated;"
                    + "onitama.net.JoinMatchRequest;"
                    + "onitama.net.ListMatchesRequest;"
                    + "onitama.net.ListMatchesResponse;"
                    + "onitama.net.GameOver;"
                    + "onitama.net.RematchRequest;"
                    + "onitama.net.RematchAccept;"
                    + "onitama.net.OpponentLeft;"
                    + "onitama.net.ReconnectRequest;"
                    + "onitama.net.LeaderboardRequest;"
                    + "onitama.net.LeaderboardResponse;"
                    + "onitama.net.Ping;"
                    + "onitama.net.Pong;"
                    + "onitama.net.ErrorMessage;"
                    + "onitama.net.ChatRequest;"
                    + "onitama.net.ChatMessage;"
                    + "onitama.net.ProtocolHello;"
                    + "onitama.net.ProtocolReady;"
                    + "onitama.net.ActionRequest;"
                    + "onitama.net.MatchSync;"
                    + "onitama.net.ActionReply;"
                    + "onitama.net.ActionReceipt;"
                    + "onitama.net.GameUpdate;"
                    + "onitama.net.UserProfile;"
                    + "onitama.net.MatchSummary;"
                    + "onitama.core.PlayerColor;"
                    + "onitama.core.Square;"
                    + "onitama.core.Offset;"
                    + "onitama.core.Card;"
                    + "onitama.core.duel.PlayerView;"
                    + "onitama.core.duel.ObjectiveLayout;"
                    + "onitama.core.duel.RulesConfig;"
                    + "onitama.core.duel.SkillDefinition;"
                    + "onitama.core.duel.SkillCategory;"
                    + "onitama.core.duel.SkillParameter;"
                    + "onitama.core.duel.CardInstance;"
                    + "onitama.core.duel.PlacedPiece;"
                    + "onitama.core.duel.Piece;"
                    + "onitama.core.duel.TurnPhase;"
                    + "onitama.core.duel.ForesightView;"
                    + "onitama.core.duel.GameOutcome;"
                    + "onitama.core.duel.EndReason;"
                    + "onitama.core.duel.Direction;"
                    + "onitama.core.duel.Relocation;"
                    + "onitama.core.duel.ActionError;"
                    + "onitama.core.duel.GameAction$MovePiece;"
                    + "onitama.core.duel.GameAction$SkipMovement;"
                    + "onitama.core.duel.GameAction$CastSkill;"
                    + "onitama.core.duel.GameAction$ResolveForesight;"
                    + "onitama.core.duel.GameAction$EndTurn;"
                    + "onitama.core.duel.GameAction$Resign;"
                    + "onitama.core.duel.SkillTargets$Relocate;"
                    + "onitama.core.duel.SkillTargets$Swap;"
                    + "onitama.core.duel.SkillTargets$Attack;"
                    + "onitama.core.duel.SkillTargets$Enemy;"
                    + "onitama.core.duel.SkillTargets$Displace;"
                    + "onitama.core.duel.SkillTargets$RelocateEnemy;"
                    + "onitama.core.duel.SkillTargets$ShiftEnemies;"
                    + "onitama.core.duel.SkillTargets$MoveGroup;"
                    + "onitama.core.duel.SkillTargets$Rally;"
                    + "onitama.core.duel.SkillTargets$None;"
                    + "onitama.core.duel.PublicEvent$PieceMoved;"
                    + "onitama.core.duel.PublicEvent$PieceEliminated;"
                    + "onitama.core.duel.PublicEvent$MovementPlayed;"
                    + "onitama.core.duel.PublicEvent$SkillCommitted;"
                    + "onitama.core.duel.PublicEvent$TurnStarted;"
                    + "onitama.core.duel.PublicEvent$MatchEnded;"
                    + "java.lang.Object;"
                    + "java.lang.String;"
                    + "java.lang.Enum;"
                    + "java.lang.Number;"
                    + "java.lang.Integer;"
                    + "java.util.CollSer;"
                    + "java.util.ImmutableCollections$List12;"
                    + "java.util.ImmutableCollections$ListN;"
                    + "java.util.ImmutableCollections$Map1;"
                    + "java.util.ImmutableCollections$MapN;"
                    + "java.util.ArrayList;"
                    + "java.util.LinkedList;"
                    + "java.util.Arrays$ArrayList;"
                    + "java.util.Collections$UnmodifiableCollection;"
                    + "java.util.Collections$UnmodifiableList;"
                    + "java.util.Collections$UnmodifiableRandomAccessList;"
                    + "java.util.Collections$EmptyList;"
                    + "java.util.Collections$SingletonList;"
                    + "!*");
}

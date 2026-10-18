package deckduel.net;

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
                    + "deckduel.net.RegisterRequest;"
                    + "deckduel.net.LoginRequest;"
                    + "deckduel.net.LoginResponse;"
                    + "deckduel.net.CreateMatchRequest;"
                    + "deckduel.net.FindMatchRequest;"
                    + "deckduel.net.MatchmakingStatus;"
                    + "deckduel.net.MatchCreated;"
                    + "deckduel.net.JoinMatchRequest;"
                    + "deckduel.net.ListMatchesRequest;"
                    + "deckduel.net.ListMatchesResponse;"
                    + "deckduel.net.GameOver;"
                    + "deckduel.net.RematchRequest;"
                    + "deckduel.net.RematchAccept;"
                    + "deckduel.net.OpponentLeft;"
                    + "deckduel.net.ReconnectRequest;"
                    + "deckduel.net.LeaderboardRequest;"
                    + "deckduel.net.LeaderboardResponse;"
                    + "deckduel.net.Ping;"
                    + "deckduel.net.Pong;"
                    + "deckduel.net.ErrorMessage;"
                    + "deckduel.net.ChatRequest;"
                    + "deckduel.net.ChatMessage;"
                    + "deckduel.net.ProtocolHello;"
                    + "deckduel.net.ProtocolReady;"
                    + "deckduel.net.ActionRequest;"
                    + "deckduel.net.MatchSync;"
                    + "deckduel.net.ActionReply;"
                    + "deckduel.net.ActionReceipt;"
                    + "deckduel.net.GameUpdate;"
                    + "deckduel.net.UserProfile;"
                    + "deckduel.net.MatchSummary;"
                    + "deckduel.core.PlayerColor;"
                    + "deckduel.core.Square;"
                    + "deckduel.core.Offset;"
                    + "deckduel.core.Card;"
                    + "deckduel.core.duel.PlayerView;"
                    + "deckduel.core.duel.ObjectiveLayout;"
                    + "deckduel.core.duel.RulesConfig;"
                    + "deckduel.core.duel.SkillDefinition;"
                    + "deckduel.core.duel.SkillCategory;"
                    + "deckduel.core.duel.SkillParameter;"
                    + "deckduel.core.duel.CardInstance;"
                    + "deckduel.core.duel.PlacedPiece;"
                    + "deckduel.core.duel.Piece;"
                    + "deckduel.core.duel.TurnPhase;"
                    + "deckduel.core.duel.ForesightView;"
                    + "deckduel.core.duel.GameOutcome;"
                    + "deckduel.core.duel.EndReason;"
                    + "deckduel.core.duel.Direction;"
                    + "deckduel.core.duel.Relocation;"
                    + "deckduel.core.duel.ActionError;"
                    + "deckduel.core.duel.GameAction$ConfirmOpening;"
                    + "deckduel.core.duel.GameAction$MovePiece;"
                    + "deckduel.core.duel.GameAction$SkipMovement;"
                    + "deckduel.core.duel.GameAction$CastSkill;"
                    + "deckduel.core.duel.GameAction$ResolveForesight;"
                    + "deckduel.core.duel.GameAction$EndTurn;"
                    + "deckduel.core.duel.GameAction$Resign;"
                    + "deckduel.core.duel.SkillTargets$Relocate;"
                    + "deckduel.core.duel.SkillTargets$Swap;"
                    + "deckduel.core.duel.SkillTargets$Attack;"
                    + "deckduel.core.duel.SkillTargets$Enemy;"
                    + "deckduel.core.duel.SkillTargets$Displace;"
                    + "deckduel.core.duel.SkillTargets$RelocateEnemy;"
                    + "deckduel.core.duel.SkillTargets$ShiftEnemies;"
                    + "deckduel.core.duel.SkillTargets$MoveGroup;"
                    + "deckduel.core.duel.SkillTargets$Rally;"
                    + "deckduel.core.duel.SkillTargets$None;"
                    + "deckduel.core.duel.PublicEvent$PieceMoved;"
                    + "deckduel.core.duel.PublicEvent$PieceEliminated;"
                    + "deckduel.core.duel.PublicEvent$MovementPlayed;"
                    + "deckduel.core.duel.PublicEvent$SkillCommitted;"
                    + "deckduel.core.duel.PublicEvent$TurnStarted;"
                    + "deckduel.core.duel.PublicEvent$MatchEnded;"
                    + "deckduel.core.duel.PublicEvent$CardBurned;"
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

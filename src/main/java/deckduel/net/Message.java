package deckduel.net;

import java.io.ObjectInputFilter;
import java.io.Serializable;

/** Closed account, lobby and card-match protocol; anything outside the allowlist is rejected. */
public sealed interface Message extends Serializable permits ActionRejected,
        CreateMatchRequest, ErrorMessage, FindMatchRequest, HistoryRequest, HistoryResponse,
        JoinMatchRequest, LeaveMatchRequest, ListMatchesRequest, ListMatchesResponse, LoginRequest,
        LoginResponse, MatchCreated, MatchUpdate, MatchmakingStatus, MoveRequest, Ping, Pong,
        PracticeRequest, ProfileUpdate, ProtocolHello, ProtocolReady, RegisterRequest, RematchRequest {
    /**
     * Version 11 adds voluntary SkipTurn moves to the neutral-card rules and server-built deck.
     * Older peers cannot handle this move and are rejected at the handshake.
     */
    int PROTOCOL_VERSION = 11;
    /** Greeting shared by both peers. */
    String GREETING = "DECK_DUEL_PROTOCOL:" + PROTOCOL_VERSION;
    /** Restricts deserialization to protocol records, card view contracts and bounded collections. */
    ObjectInputFilter WIRE_FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=32;maxarray=1000;"
                    + "deckduel.net.ActionRejected;"
                    + "deckduel.net.CreateMatchRequest;"
                    + "deckduel.net.ErrorMessage;"
                    + "deckduel.net.FindMatchRequest;"
                    + "deckduel.net.JoinMatchRequest;"
                    + "deckduel.net.HistoryEntry;"
                    + "deckduel.net.HistoryEntry$Result;"
                    + "deckduel.net.HistoryRequest;"
                    + "deckduel.net.HistoryResponse;"
                    + "deckduel.net.LeaveMatchRequest;"
                    + "deckduel.net.ListMatchesRequest;"
                    + "deckduel.net.ListMatchesResponse;"
                    + "deckduel.net.LoginRequest;"
                    + "deckduel.net.LoginResponse;"
                    + "deckduel.net.MatchCreated;"
                    + "deckduel.net.MatchSummary;"
                    + "deckduel.net.MatchUpdate;"
                    + "deckduel.net.MatchmakingStatus;"
                    + "deckduel.net.MoveRequest;"
                    + "deckduel.net.Ping;"
                    + "deckduel.net.Pong;"
                    + "deckduel.net.PracticeRequest;"
                    + "deckduel.net.ProfileUpdate;"
                    + "deckduel.net.ProtocolHello;"
                    + "deckduel.net.ProtocolReady;"
                    + "deckduel.net.RegisterRequest;"
                    + "deckduel.net.RematchRequest;"
                    + "deckduel.net.UserProfile;"
                    + "deckduel.core.card.CardDef;deckduel.core.card.CardInstance;deckduel.core.card.CardKind;"
                    + "deckduel.core.card.DrawCard;deckduel.core.card.KeepCard;deckduel.core.card.PickLandmarks;"
                    + "deckduel.core.card.PlayCard;deckduel.core.card.SkipTurn;deckduel.core.card.LandmarkCard;"
                    + "deckduel.core.card.Effect$SharedTagBonus;deckduel.core.card.Effect$WeakUnitBonus;"
                    + "deckduel.core.card.Effect$EmptySpaceBonus;deckduel.core.card.Effect$FirstSpellDraw;"
                    + "deckduel.core.card.Effect$DeckTopView;deckduel.core.card.Effect$Disable;deckduel.core.card.Effect$Destroy;"
                    + "deckduel.core.card.Effect$ClearDisable;deckduel.core.card.Effect$RandomDiscard;"
                    + "deckduel.core.card.Effect$RandomSteal;deckduel.core.card.Effect$Draw;deckduel.core.card.Effect$Exchange;"
                    + "deckduel.core.card.Effect$Reconnaissance;deckduel.core.card.Effect$Sabotage;deckduel.core.card.Effect$Spy;"
                    + "deckduel.core.card.MatchEvent;deckduel.core.card.MatchOutcome;deckduel.core.card.MatchPhase;"
                    + "deckduel.core.card.PairBonus;deckduel.core.card.PlayRecord;deckduel.core.card.PlayerView;"
                    + "deckduel.core.card.RoundResult;deckduel.core.card.Row;deckduel.core.card.RowCard;"
                    + "deckduel.core.card.ScoreBreakdown;deckduel.core.card.Seat;deckduel.core.card.SideView;"
                    + "deckduel.core.card.Suppression;deckduel.core.card.TagType;deckduel.core.card.TargetSide;"
                    + "java.lang.Enum;java.lang.Object;java.lang.String;java.lang.Integer;java.lang.Long;"
                    + "java.lang.Number;java.lang.Boolean;"
                    + "java.util.CollSer;java.util.ImmutableCollections$List12;java.util.ImmutableCollections$ListN;"
                    + "java.util.ArrayList;java.util.LinkedList;java.util.Arrays$ArrayList;"
                    + "java.util.Collections$UnmodifiableCollection;java.util.Collections$UnmodifiableList;"
                    + "java.util.Collections$UnmodifiableRandomAccessList;java.util.Collections$EmptyList;"
                    + "java.util.Collections$SingletonList;!*");
}

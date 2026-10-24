package deckduel.net;

import java.io.ObjectInputFilter;
import java.io.Serializable;

/** Closed account, lobby and card-match protocol; anything outside the allowlist is rejected. */
public sealed interface Message extends Serializable permits ActionRejected, CancelRoomRequest,
        CreateMatchRequest, ErrorMessage, HistoryRequest, HistoryResponse,
        JoinMatchRequest, LeaveMatchRequest, ListMatchesRequest, ListMatchesResponse, LoginRequest,
        LoginResponse, MaintenanceNotice, MatchCreated, MatchUpdate, MoveRequest, Ping, Pong,
        PracticeRequest, ProfileUpdate, ProtocolHello, ProtocolReady, RegisterRequest, RematchRequest,
        ReplayRequest, ReplayResponse, UpdateRequired {
    /**
     * Version 17 adds simultaneous King choices, ownership, bonus breakdown and private Secret Edict.
     * Version 16 added the client's release version to ProtocolHello, UpdateRequired (the server's
     * minimum client version, answered before login) and MaintenanceNotice (drain before a
     * restart). Version 15 added CancelRoomRequest, 14 personal replays, and 13 removed the
     * matchmaking queue. Older peers are rejected at the handshake.
     */
    int PROTOCOL_VERSION = 17;
    /** Greeting shared by both peers. */
    String GREETING = "DECK_DUEL_PROTOCOL:" + PROTOCOL_VERSION;
    /** Restricts deserialization to protocol records, card view contracts and bounded collections. */
    ObjectInputFilter WIRE_FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=32;maxarray=1000;"
                    + "deckduel.net.ActionRejected;"
                    + "deckduel.net.CancelRoomRequest;"
                    + "deckduel.net.CreateMatchRequest;"
                    + "deckduel.net.ErrorMessage;"
                    + "deckduel.net.JoinMatchRequest;"
                    + "deckduel.net.HistoryEntry;"
                    + "deckduel.net.HistoryEntry$Replay;"
                    + "deckduel.net.HistoryEntry$Result;"
                    + "deckduel.net.HistoryRequest;"
                    + "deckduel.net.HistoryResponse;"
                    + "deckduel.net.LeaveMatchRequest;"
                    + "deckduel.net.ListMatchesRequest;"
                    + "deckduel.net.ListMatchesResponse;"
                    + "deckduel.net.LoginRequest;"
                    + "deckduel.net.LoginResponse;"
                    + "deckduel.net.MaintenanceNotice;"
                    + "deckduel.net.MatchCreated;"
                    + "deckduel.net.MatchSummary;"
                    + "deckduel.net.MatchUpdate;"
                    + "deckduel.net.MoveRequest;"
                    + "deckduel.net.Ping;"
                    + "deckduel.net.Pong;"
                    + "deckduel.net.PracticeRequest;"
                    + "deckduel.net.ProfileUpdate;"
                    + "deckduel.net.ProtocolHello;"
                    + "deckduel.net.ProtocolReady;"
                    + "deckduel.net.RegisterRequest;"
                    + "deckduel.net.RematchRequest;"
                    + "deckduel.net.ReplayRequest;"
                    + "deckduel.net.ReplayResponse;"
                    + "deckduel.net.UpdateRequired;"
                    + "deckduel.net.UserProfile;"
                    + "deckduel.core.card.CardDef;deckduel.core.card.CardInstance;deckduel.core.card.CardKind;"
                    + "deckduel.core.card.King;deckduel.core.card.KingStatus;deckduel.core.card.KingBonus;"
                    + "deckduel.core.card.ChooseKing;deckduel.core.card.KeepEdictCard;deckduel.core.card.ChoiceKind;"
                    + "deckduel.core.card.DrawCard;deckduel.core.card.KeepCard;"
                    + "deckduel.core.card.PlayCard;deckduel.core.card.SkipTurn;deckduel.core.card.Formation;"
                    + "deckduel.core.card.Effect$Disable;deckduel.core.card.Effect$Destroy;"
                    + "deckduel.core.card.Effect$ClearDisable;deckduel.core.card.Effect$RandomDiscard;"
                    + "deckduel.core.card.Effect$RandomSteal;deckduel.core.card.Effect$Draw;deckduel.core.card.Effect$Exchange;"
                    + "deckduel.core.card.Effect$Reconnaissance;deckduel.core.card.Effect$Spy;"
                    + "deckduel.core.card.MatchEvent;deckduel.core.card.MatchOutcome;deckduel.core.card.MatchPhase;"
                    + "deckduel.core.card.PairBonus;deckduel.core.card.PlayRecord;deckduel.core.card.PlayerView;"
                    + "deckduel.core.card.RoundResult;deckduel.core.card.Row;deckduel.core.card.RowCard;"
                    + "deckduel.core.card.ScoreBreakdown;deckduel.core.card.Seat;deckduel.core.card.SideView;"
                    + "deckduel.core.card.Suppression;deckduel.core.card.TargetSide;"
                    + "java.lang.Enum;java.lang.Object;java.lang.String;java.lang.Integer;java.lang.Long;"
                    + "java.lang.Number;java.lang.Boolean;"
                    + "java.util.CollSer;java.util.ImmutableCollections$List12;java.util.ImmutableCollections$ListN;"
                    + "java.util.ArrayList;java.util.LinkedList;java.util.Arrays$ArrayList;"
                    + "java.util.Collections$UnmodifiableCollection;java.util.Collections$UnmodifiableList;"
                    + "java.util.Collections$UnmodifiableRandomAccessList;java.util.Collections$EmptyList;"
                    + "java.util.Collections$SingletonList;!*");
}

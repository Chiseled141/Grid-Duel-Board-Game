package deckduel.core.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Immutable authoritative match state (§5.1–5.5). Every accepted move returns a new state; a
 * rejected move throws {@link RuleViolation} and leaves this state untouched. Shuffles and random
 * Spell picks are derived from one seed, so a match replays identically, but
 * neither the seed nor deck order ever reaches a {@link PlayerView}.
 */
public final class MatchState {
    private static final int EVENT_LIMIT = 20;
    /** Card ids of the second seat start this far after the first's, beyond any deck size. */
    private static final int SEAT_IDS = 1000;

    /** One player's complete private and public state. */
    private record Side(List<CardInstance> library, List<CardInstance> drawPile, List<CardInstance> hand, Row row,
                        int playsUsed, int drawsUsed, long matchTotal, int discardCount, List<CardInstance> looking, List<Long> known, KingStatus kings, King selectedKing, ChoiceKind choiceKind) {
        Side {
            drawPile = List.copyOf(drawPile);
            hand = List.copyOf(hand);
            looking = List.copyOf(looking);
            known = List.copyOf(known);
        }

        /** Whether this player can still do anything this round: play a card, or draw one to play. */
        boolean canAct() {
            return playsUsed < Rules.PLAYS_PER_ROUND
                    && (!hand.isEmpty() || drawsUsed < Rules.MANUAL_DRAWS && !drawPile.isEmpty());
        }
    }

    /** A side being changed by one move; {@link #build()} returns the next immutable side. */
    private static final class Draft {
        final List<CardInstance> library;
        List<CardInstance> drawPile, hand, looking;
        Set<Long> known;
        Row row;
        int playsUsed, drawsUsed, discardCount;
        long matchTotal;
        KingStatus kings;
        King selectedKing;
        ChoiceKind choiceKind;

        Draft(Side side) {
            kings = side.kings();
            selectedKing = side.selectedKing();
            choiceKind = side.choiceKind();
            library = side.library();
            drawPile = new ArrayList<>(side.drawPile());
            hand = new ArrayList<>(side.hand());
            looking = new ArrayList<>(side.looking());
            known = new LinkedHashSet<>(side.known());
            row = side.row();
            playsUsed = side.playsUsed();
            drawsUsed = side.drawsUsed();
            discardCount = side.discardCount();
            matchTotal = side.matchTotal();
        }

        /** Draws up to {@code count} cards from the top of the deck; returns how many came. */
        int draw(int count) {
            int drawn = 0;
            for (; drawn < count && !drawPile.isEmpty(); drawn++) hand.add(drawPile.remove(0));
            return drawn;
        }

        Side build() {
            return new Side(library, drawPile, hand, row, playsUsed, drawsUsed, matchTotal, discardCount, looking,
                    List.copyOf(known), kings, selectedKing, choiceKind);
        }
    }

    private final long seed;
    private final int round, revision;
    private final Seat starter, turn;
    private final Side first, second;
    private final List<RoundResult> rounds;
    private final List<MatchEvent> events;
    private final MatchOutcome outcome;
    private final PlayRecord lastPlay;

    private MatchState(long seed, int round, int revision, Seat starter, Seat turn, Side first, Side second,
                       List<RoundResult> rounds, List<MatchEvent> events, MatchOutcome outcome, PlayRecord lastPlay) {
        this.seed = seed;
        this.round = round;
        this.revision = revision;
        this.starter = starter;
        this.turn = turn;
        this.first = first;
        this.second = second;
        this.rounds = List.copyOf(rounds);
        this.events = List.copyOf(events.subList(Math.max(0, events.size() - EVENT_LIMIT), events.size()));
        this.outcome = outcome;
        this.lastPlay = lastPlay;
    }

    /** Opens a match and deals round one. The seed picks the first starter and every shuffle. */
    public static MatchState start(List<CardDef> firstDeck, List<CardDef> secondDeck, long seed) {
        Side first = deal(newSide(firstDeck, 1), seed, 1, Seat.FIRST);
        Side second = deal(newSide(secondDeck, 1 + SEAT_IDS), seed, 1, Seat.SECOND);
        Seat starter = new Random(seed).nextBoolean() ? Seat.FIRST : Seat.SECOND;
        return new MatchState(seed, 1, 0, starter, starter, first, second, List.of(),
                List.of(new MatchEvent(null, "Round 1 begins.")), null, null);
    }

    private static Side newSide(List<CardDef> deck, long firstId) {
        if (deck.isEmpty() || deck.size() >= SEAT_IDS) throw new IllegalArgumentException("Invalid deck size " + deck.size());
        List<CardInstance> library = new ArrayList<>();
        for (int i = 0; i < deck.size(); i++) library.add(new CardInstance(firstId + i, deck.get(i)));
        return new Side(List.copyOf(library), List.of(), List.of(), Row.EMPTY, 0, 0, 0, 0, List.of(), List.of(), KingStatus.EMPTY, null, ChoiceKind.NONE);
    }

    /** Clears the row, reshuffles the whole deck and deals ten fresh cards. */
    private static Side deal(Side side, long seed, int round, Seat seat) {
        List<CardInstance> shuffled = new ArrayList<>(side.library());
        Collections.shuffle(shuffled, new Random(seed * 31 + round * 2L + seat.ordinal()));
        int hand = Math.min(Rules.HAND_SIZE, shuffled.size());
        return new Side(side.library(), shuffled.subList(hand, shuffled.size()), shuffled.subList(0, hand), Row.EMPTY,
                0, 0, side.matchTotal(), 0, List.of(), List.of(),
                new KingStatus(side.kings().kings(), 0, false), null, ChoiceKind.NONE);
    }

    // ------------------------------------------------------------------ moves

    /** Applies one move by {@code actor}, or throws without changing anything. */
    public MatchState apply(Seat actor, Move move) {
        if (outcome != null) throw new RuleViolation("The match is over.");
        if (move instanceof ChooseKing choice) return chooseKing(actor, choice);
        if (phase() == MatchPhase.KING_SELECTION) throw new RuleViolation("Both players must confirm a King first.");
        if (actor != turn) throw new RuleViolation("It is not your turn.");
        if (move instanceof KeepEdictCard keep) return keep(actor, keep.cardId(), ChoiceKind.SECRET_EDICT);
        if (move instanceof KeepCard keep) return keep(actor, keep.cardId(), ChoiceKind.RECONNAISSANCE);
        if (!side(actor).looking().isEmpty()) throw new RuleViolation("Finish your private card choice first.");
        if (move instanceof DrawCard) return draw(actor);
        if (move instanceof SkipTurn) return skip(actor);
        return playCard(actor, (PlayCard) move);
    }

    /** Applies one card play; shorthand for {@link #apply}. */
    public MatchState play(Seat actor, PlayCard play) { return apply(actor, play); }

    /** Uses one play allowance without touching cards, effects or draw allowances. */
    private MatchState skip(Seat actor) {
        Side mine = side(actor), other = side(actor.other());
        if (mine.playsUsed() >= Rules.PLAYS_PER_ROUND) throw new RuleViolation("You have used all ten plays this round.");
        Draft me = new Draft(mine);
        me.playsUsed++;
        List<MatchEvent> log = new ArrayList<>(events);
        log.add(new MatchEvent(actor, "skipped their turn (uses one play)"));
        Side next = me.build();
        return advance(actor, actor == Seat.FIRST ? next : other, actor == Seat.FIRST ? other : next, log, null);
    }

    private MatchState playCard(Seat actor, PlayCard play) {
        Side mine = side(actor), theirs = side(actor.other());
        if (mine.playsUsed() >= Rules.PLAYS_PER_ROUND) throw new RuleViolation("You have used all ten plays this round.");
        CardInstance card = mine.hand().stream().filter(c -> c.id() == play.cardId()).findFirst()
                .orElseThrow(() -> new RuleViolation("That card is not in your hand."));
        Rules.Resolution resolution = Rules.resolve(card, play, mine.hand(), board(actor));
        Draft me = new Draft(mine), them = new Draft(theirs);
        me.hand.remove(card);
        me.row = resolution.board().own();
        them.row = resolution.board().opponent();
        me.playsUsed++;
        if (card.def().kind() == CardKind.SPELL)
            me.kings = new KingStatus(me.kings.kings(), me.kings.spellsPlayed() + 1, me.kings.edictUsed());
        List<CardInstance> discarded = new ArrayList<>(resolution.discarded());
        Outcome extra = card.def().kind() == CardKind.SPELL ? cast(card, play, me, them, discarded) : Outcome.NONE;
        for (CardInstance gone : discarded) (mine.library().contains(gone) ? me : them).discardCount++;
        List<MatchEvent> log = new ArrayList<>(events);
        log.add(new MatchEvent(actor, resolution.description() + extra.detail()));
        PlayRecord record = record(actor, card, play, resolution, extra, me.row, them.row);
        Side nextMine = me.build(), nextTheirs = them.build();
        Side nextFirst = actor == Seat.FIRST ? nextMine : nextTheirs, nextSecond = actor == Seat.FIRST ? nextTheirs : nextMine;
        // A Reconnaissance waits for its caster to keep a card before the turn moves on.
        if (!nextMine.looking().isEmpty())
            return new MatchState(seed, round, revision + 1, starter, actor, nextFirst, nextSecond, rounds, log, null, record);
        return advance(actor, nextFirst, nextSecond, log, record);
    }

    /** What a Spell did beyond the public board: a card taken from a hand, cards drawn, and a log detail. */
    private record Outcome(CardInstance handCard, int drawn, String detail) {
        static final Outcome NONE = new Outcome(null, 0, "");
    }

    /** Applies a Spell's effect on hands and decks. */
    private Outcome cast(CardInstance card, PlayCard play, Draft me, Draft them, List<CardInstance> discarded) {
        Effect effect = card.def().effect();
        Random random = new Random(seed * 7919 + revision);
        CardInstance handCard = null;
        int drawn = 0;
        String detail = "";
        if (effect instanceof Effect.Exchange exchange && play.targetId() != null) {
            handCard = me.hand.stream().filter(c -> c.id() == play.targetId()).findFirst().orElseThrow();
            me.hand.remove(handCard);
            discarded.add(handCard);
            drawn = me.draw(exchange.draw());
        } else if (effect instanceof Effect.Draw draw) {
            drawn = me.draw(draw.cards());
        } else if (effect instanceof Effect.RandomDiscard discard) {
            for (int i = 0; i < discard.cards() && !them.hand.isEmpty(); i++) {
                handCard = them.hand.remove(random.nextInt(them.hand.size()));
                discarded.add(handCard);
            }
            detail = handCard == null ? ": the enemy hand was empty" : ", discarding " + handCard.def().name();
        } else if (effect instanceof Effect.RandomSteal steal) {
            for (int i = 0; i < steal.cards() && !them.hand.isEmpty(); i++) {
                handCard = them.hand.remove(random.nextInt(them.hand.size()));
                me.hand.add(handCard);
            }
            detail = handCard == null ? ": the enemy hand was empty" : ", taking " + handCard.def().name();
        } else if (effect instanceof Effect.Reconnaissance recon) {
            me.choiceKind = ChoiceKind.RECONNAISSANCE;
            for (int i = 0; i < recon.look() && !me.drawPile.isEmpty(); i++) me.looking.add(me.drawPile.remove(0));
        } else if (effect instanceof Effect.Spy spy) {
            List<CardInstance> shown = new ArrayList<>(them.hand);
            Collections.shuffle(shown, random);
            shown.subList(0, Math.min(spy.cards(), shown.size())).forEach(seen -> me.known.add(seen.id()));
            detail = ", privately seeing " + Math.min(spy.cards(), shown.size()) + " cards in the enemy hand";
        }
        if (drawn > 0) detail += ", drawing " + drawn;
        return new Outcome(handCard, drawn, detail);
    }

    private static PlayRecord record(Seat actor, CardInstance card, PlayCard play, Rules.Resolution resolution, Outcome extra,
                                     Row actorRow, Row opponentRow) {
        CardInstance other = resolution.discarded().stream().filter(c -> c.id() != card.id()).findFirst().orElse(null);
        boolean placed = card.def().occupiesSpace();
        return new PlayRecord(actor, card, play.slot(), play.targetId(), placed ? other : null, placed ? null : other,
                extra.handCard(), extra.drawn(), actorRow, opponentRow);
    }

    /** A manual draw: one card, without using or ending the turn. */
    private MatchState draw(Seat actor) {
        Side mine = side(actor);
        if (mine.drawsUsed() >= Rules.MANUAL_DRAWS) throw new RuleViolation("You have no manual draws left this round.");
        if (mine.drawPile().isEmpty()) throw new RuleViolation("Your deck is empty.");
        Draft me = new Draft(mine);
        boolean edict = me.kings.kings().contains(King.NETHRION) && !me.kings.edictUsed();
        if (edict) {
            me.kings = new KingStatus(me.kings.kings(), me.kings.spellsPlayed(), true);
            if (me.drawPile.size() > 1) {
                me.choiceKind = ChoiceKind.SECRET_EDICT;
                for (int i = 0; i < King.NETHRION.threshold(); i++) me.looking.add(me.drawPile.remove(0));
            } else me.draw(1);
        } else me.draw(1);
        me.drawsUsed++;
        List<MatchEvent> log = new ArrayList<>(events);
        log.add(new MatchEvent(actor, "drew a card"));
        return with(actor, me.build(), log, turn);
    }

    /** Keeps one card a Reconnaissance revealed; the others go to the bottom of the deck in their order. */
    private MatchState keep(Seat actor, long cardId, ChoiceKind kind) {
        Side mine = side(actor);
        if (mine.choiceKind() != kind || mine.looking().isEmpty()) throw new RuleViolation("There is nothing to keep.");
        CardInstance kept = mine.looking().stream().filter(c -> c.id() == cardId).findFirst()
                .orElseThrow(() -> new RuleViolation("Keep one of the revealed cards."));
        Draft me = new Draft(mine);
        me.looking.remove(kept);
        me.hand.add(kept);
        me.drawPile.addAll(me.looking);
        me.looking.clear();
        me.choiceKind = ChoiceKind.NONE;
        List<MatchEvent> log = new ArrayList<>(events);
        log.add(new MatchEvent(actor, "kept a card from " + (kind == ChoiceKind.SECRET_EDICT ? "Secret Edict" : "Reconnaissance")));
        if (kind == ChoiceKind.SECRET_EDICT) return with(actor, me.build(), log, actor);
        Side nextMine = me.build(), other = side(actor.other());
        return advance(actor, actor == Seat.FIRST ? nextMine : other, actor == Seat.FIRST ? other : nextMine, log, null);
    }

    private MatchState with(Seat actor, Side changed, List<MatchEvent> log, Seat nextTurn) {
        Side nextFirst = actor == Seat.FIRST ? changed : first, nextSecond = actor == Seat.FIRST ? second : changed;
        return new MatchState(seed, round, revision + 1, starter, nextTurn, nextFirst, nextSecond, rounds, log, null, null);
    }

    /**
     * Passes the turn after a completed play: to the opponent when they can act, otherwise back to
     * the actor, whose unused plays are kept; the round ends when neither can act.
     */
    private MatchState advance(Seat actor, Side nextFirst, Side nextSecond, List<MatchEvent> log, PlayRecord record) {
        Side other = actor == Seat.FIRST ? nextSecond : nextFirst, mine = actor == Seat.FIRST ? nextFirst : nextSecond;
        Seat next = other.canAct() ? actor.other() : mine.canAct() ? actor : null;
        if (next == null) return endRound(nextFirst, nextSecond, log, record);
        if (next == actor && other.playsUsed() < Rules.PLAYS_PER_ROUND) log.add(new MatchEvent(actor.other(), "cannot play; the turn passes"));
        return new MatchState(seed, round, revision + 1, starter, next, nextFirst, nextSecond, rounds, log, null, record);
    }

    /** Banks both live scores, then deals the next round or finishes the match. */
    private MatchState endRound(Side first, Side second, List<MatchEvent> log, PlayRecord record) {
        long firstScore = Scoring.score(first.row(), first.kings()).total();
        long secondScore = Scoring.score(second.row(), second.kings()).total();
        RoundResult result = new RoundResult(round, starter, firstScore, secondScore);
        List<RoundResult> history = new ArrayList<>(rounds);
        history.add(result);
        Side bankedFirst = bank(first, firstScore), bankedSecond = bank(second, secondScore);
        log.add(new MatchEvent(null, "Round " + round + " ends."));
        if (round == Rules.ROUNDS) {
            Seat winner = bankedFirst.matchTotal() == bankedSecond.matchTotal() ? null
                    : bankedFirst.matchTotal() > bankedSecond.matchTotal() ? Seat.FIRST : Seat.SECOND;
            log.add(new MatchEvent(null, winner == null ? "The match is drawn." : "The match is decided."));
            return new MatchState(seed, round, revision + 1, starter, null, bankedFirst, bankedSecond, history, log,
                    new MatchOutcome(winner, false), record);
        }
        // The round's loser starts next; after a tie the starting player alternates.
        Seat nextStarter = result.winner() == null ? starter.other() : result.winner().other();
        int nextRound = round + 1;
        log.add(new MatchEvent(null, "Round " + nextRound + " begins."));
        return new MatchState(seed, nextRound, revision + 1, nextStarter, nextStarter,
                deal(bankedFirst, seed, nextRound, Seat.FIRST), deal(bankedSecond, seed, nextRound, Seat.SECOND),
                history, log, null, record);
    }

    private static Side bank(Side side, long score) {
        Draft banked = new Draft(side);
        banked.matchTotal += score;
        return banked.build();
    }

    /** Ends the match immediately in the opponent's favour. */
    public MatchState forfeit(Seat loser) {
        if (outcome != null) throw new RuleViolation("The match is over.");
        List<MatchEvent> log = new ArrayList<>(events);
        log.add(new MatchEvent(loser, "forfeited the match"));
        return new MatchState(seed, round, revision + 1, starter, null, first, second, rounds, log,
                new MatchOutcome(loser.other(), true), null);
    }

    // ------------------------------------------------------------------ views

    /**
     * Projects the state for one recipient: both public sides, and only the recipient's own hand,
     * Reconnaissance cards and Spy knowledge.
     */
    public PlayerView view(Seat seat) {
        Side mine = side(seat), theirs = side(seat.other());
        List<CardInstance> revealed = theirs.hand().stream().filter(card -> mine.known().contains(card.id())).toList();
        return new PlayerView(seat, revision, phase(), round, turn, sideView(mine), sideView(theirs),
                mine.hand(), rounds, events, outcome, lastPlay, mine.looking(), revealed,
                phase() == MatchPhase.KING_SELECTION ? kingOffer() : List.of(), mine.selectedKing(),
                mine.looking().isEmpty() ? ChoiceKind.NONE : mine.choiceKind());
    }

    /** One side's public facts. */
    private static SideView sideView(Side side) {
        return new SideView(side.row(), Scoring.score(side.row(), side.kings()), side.playsUsed(), side.drawsUsed(),
                side.hand().size(), side.drawPile().size(), side.discardCount(), side.matchTotal(), side.kings());
    }

    /** Shared deterministic offers: the first six of seven shuffled Kings, in disjoint groups of three. */
    private List<King> kingOffer() {
        List<King> kings = new ArrayList<>(List.of(King.values()));
        Collections.shuffle(kings, new Random(seed ^ 0x4B494E47534CL));
        return List.copyOf(kings.subList((round - 1) * 3, round * 3));
    }

    private MatchState chooseKing(Seat actor, ChooseKing choice) {
        Side mine = side(actor);
        if (phase() != MatchPhase.KING_SELECTION || mine.selectedKing() != null)
            throw new RuleViolation("You cannot choose a King now.");
        if (choice.king() == null || !kingOffer().contains(choice.king()) || mine.kings().kings().contains(choice.king()) || mine.kings().kings().size() >= 2)
            throw new RuleViolation("Choose a new King from the current offer.");
        Draft me = new Draft(mine), them = new Draft(side(actor.other()));
        me.selectedKing = choice.king();
        List<MatchEvent> log = new ArrayList<>(events);
        log.add(new MatchEvent(actor, "confirmed a King"));
        if (them.selectedKing != null) {
            for (Draft side : List.of(me, them)) {
                List<King> owned = new ArrayList<>(side.kings.kings());
                owned.add(side.selectedKing);
                side.kings = new KingStatus(owned, side.kings.spellsPlayed(), side.kings.edictUsed());
                side.selectedKing = null;
            }
            log.add(new MatchEvent(null, "Both Kings are revealed. Normal turns begin."));
        }
        return new MatchState(seed, round, revision + 1, starter, turn,
                actor == Seat.FIRST ? me.build() : them.build(), actor == Seat.FIRST ? them.build() : me.build(),
                rounds, log, null, null);
    }

    private Rules.Board board(Seat seat) { return new Rules.Board(side(seat).row(), side(seat.other()).row()); }

    private Side side(Seat seat) { return seat == Seat.FIRST ? first : second; }

    /** Where the match is. */
    public MatchPhase phase() {
        if (outcome != null) return MatchPhase.FINISHED;
        return round <= 2 && (first.kings().kings().size() < round || second.kings().kings().size() < round)
                ? MatchPhase.KING_SELECTION : MatchPhase.IN_PROGRESS;
    }

    /** Whether {@code seat} holds the turn. */
    public boolean awaits(Seat seat) { return phase() == MatchPhase.KING_SELECTION ? side(seat).selectedKing() == null
            : phase() == MatchPhase.IN_PROGRESS && turn == seat; }

    /** Increases with every accepted change; clients quote it to reject stale actions. */
    public int revision() { return revision; }
    /** Seat to play, or null after the match ends. */
    public Seat turn() { return turn; }
    /** Current round, 1–3. */
    public int round() { return round; }
    /** Final outcome, or null while in progress. */
    public MatchOutcome outcome() { return outcome; }
    /** Banked results of finished rounds. */
    public List<RoundResult> rounds() { return rounds; }
    /** A seat's banked match total. */
    public long matchTotal(Seat seat) { return side(seat).matchTotal(); }
}

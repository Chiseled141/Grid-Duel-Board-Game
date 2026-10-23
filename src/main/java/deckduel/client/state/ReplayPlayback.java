package deckduel.client.state;

import deckduel.core.card.MatchEvent;
import deckduel.core.card.PlayerView;
import deckduel.net.ReplayResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Playback state for one personal replay: which recorded frame is shown, whether it plays, and
 * how fast. It holds no connection and never touches the live match, so nothing done here can
 * send a request. Owned by the Swing EDT; observers are told after every change.
 */
public final class ReplayPlayback {
    /** The speeds offered, as multiples of {@link #BASE_STEP_MILLIS}. */
    public static final List<Double> SPEEDS = List.of(0.5, 1.0, 2.0, 4.0);
    /** How long one action stays on screen at 1× speed. */
    public static final int BASE_STEP_MILLIS = 1400;

    private final ReplayResponse replay;
    private final List<Runnable> listeners = new ArrayList<>();
    private int index;
    private boolean playing;
    private double speed = 1.0;

    /** Starts paused on the opening deal. */
    public ReplayPlayback(ReplayResponse replay) { this.replay = Objects.requireNonNull(replay); }

    /** Adds an observer of position, play state and speed. */
    public void addListener(Runnable listener) { listeners.add(listener); }
    /** The replay as the server sent it. */
    public ReplayResponse replay() { return replay; }
    /** The frame shown now. */
    public PlayerView frame() { return replay.frames().get(index); }
    /** The frame before the one shown, or null on the first. */
    public PlayerView previousFrame() { return index == 0 ? null : replay.frames().get(index - 1); }
    /** Zero-based position of the shown frame. */
    public int index() { return index; }
    /** How many frames the replay has. */
    public int length() { return replay.frames().size(); }
    /** Whether automatic stepping is on. */
    public boolean playing() { return playing; }
    /** The playback speed. */
    public double speed() { return speed; }
    /** Milliseconds each action stays on screen at the current speed. */
    public int stepMillis() { return (int) Math.round(BASE_STEP_MILLIS / speed); }
    /** Whether the last frame is shown. */
    public boolean atEnd() { return index == length() - 1; }

    /** Starts automatic stepping; from the last frame it starts over. */
    public void play() {
        if (atEnd()) index = 0;
        playing = true;
        changed();
    }

    /** Stops automatic stepping. */
    public void pause() {
        if (!playing) return;
        playing = false;
        changed();
    }

    /** Shows the next action; playback pauses at the end. */
    public void next() {
        if (atEnd()) { pause(); return; }
        index++;
        if (atEnd()) playing = false;
        changed();
    }

    /** Shows the previous action and pauses. */
    public void previous() {
        if (index == 0 && !playing) return;
        index = Math.max(0, index - 1);
        playing = false;
        changed();
    }

    /** Chooses one of {@link #SPEEDS}. */
    public void setSpeed(double next) {
        if (!SPEEDS.contains(next)) throw new IllegalArgumentException("Unsupported speed " + next);
        speed = next;
        changed();
    }

    /** "Round 2 · Action 14 of 63": the shown frame's round and its place in the replay. */
    public String position() {
        return "Round " + frame().round() + " · Action " + index + " of " + (length() - 1);
    }

    /** What the shown action was, from the log lines it added, or the opening deal. */
    public String action() {
        if (index == 0) return "Opening deal";
        PlayerView view = frame();
        List<String> lines = new ArrayList<>();
        for (MatchEvent event : added(previousFrame().events(), view.events())) {
            String actor = event.actor() == null ? "" : event.actor() == view.seat() ? "You: " : replay.opponent() + ": ";
            lines.add(actor + event.text());
        }
        return lines.isEmpty() ? "No logged change" : String.join("  ·  ", lines);
    }

    /**
     * The lines {@code after} added to {@code before}. The log keeps only its newest lines, so the
     * new ones are what remains once the longest part of {@code after} that ends {@code before} is removed.
     */
    static List<MatchEvent> added(List<MatchEvent> before, List<MatchEvent> after) {
        for (int kept = Math.min(before.size(), after.size()); kept > 0; kept--)
            if (before.subList(before.size() - kept, before.size()).equals(after.subList(0, kept)))
                return after.subList(kept, after.size());
        return after;
    }

    private void changed() { List.copyOf(listeners).forEach(Runnable::run); }
}

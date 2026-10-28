package deckduel.client.ui;

import deckduel.client.state.ReplayIndex;
import deckduel.client.state.ReplayPlayback;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Hashtable;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/** A local seek bar and recorded-round selector; user seeks always pause playback. */
final class ReplayTimeline extends JPanel {
    private final JSlider slider = new JSlider();
    private final JComboBox<ReplayIndex.RoundStart> rounds = new JComboBox<>();
    private final JButton first = UiKit.pill("First", UiKit.Pill.GOLD_OUTLINE);
    private final JButton last = UiKit.pill("Last", UiKit.Pill.GOLD_OUTLINE);
    private final JButton previousRound = UiKit.pill("‹ R", UiKit.Pill.GOLD_OUTLINE);
    private final JButton nextRound = UiKit.pill("R ›", UiKit.Pill.GOLD_OUTLINE);
    private ReplayPlayback playback;
    private boolean updating;

    ReplayTimeline() {
        super(new BorderLayout(10, 0));
        setOpaque(false); setName("replayTimeline");
        slider.setName("replaySeek"); slider.setOpaque(false);
        slider.setForeground(Theme.BRASS_LIGHT);
        slider.getAccessibleContext().setAccessibleName("Replay position");
        slider.getAccessibleContext().setAccessibleDescription("Seek to a recorded action; playback pauses while scrubbing");
        rounds.setName("replayRounds"); rounds.setFont(Theme.normal(12));
        rounds.setForeground(Theme.CREAM); rounds.setBackground(Theme.IRON_DARK);
        rounds.getAccessibleContext().setAccessibleName("Jump to recorded round");
        first.setName("replayFirst"); last.setName("replayLast");
        previousRound.setName("replayPreviousRound"); nextRound.setName("replayNextRound");
        previousRound.getAccessibleContext().setAccessibleName("Previous round start");
        nextRound.getAccessibleContext().setAccessibleName("Next round start");
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); left.setOpaque(false);
        left.add(first); left.add(previousRound); left.add(rounds); left.add(nextRound);
        add(left, BorderLayout.WEST); add(slider, BorderLayout.CENTER); add(last, BorderLayout.EAST);
        first.addActionListener(event -> { if (playback != null) playback.seek(0); });
        last.addActionListener(event -> { if (playback != null) playback.seek(playback.length() - 1); });
        previousRound.addActionListener(event -> { if (playback != null) playback.previousRound(); });
        nextRound.addActionListener(event -> { if (playback != null) playback.nextRound(); });
        rounds.addActionListener(event -> {
            if (!updating && playback != null && rounds.getSelectedItem() instanceof ReplayIndex.RoundStart round)
                playback.seek(round.index());
        });
        slider.addChangeListener(event -> {
            if (updating || playback == null) return;
            // Pause before changing the index: timer ticks cannot race the user's drag.
            playback.pause();
            playback.seek(slider.getValue());
        });
        setPreferredSize(new Dimension(600, 44));
        attach(null);
    }

    void attach(ReplayPlayback next) {
        updating = true;
        try {
            slider.setPaintLabels(false);
            playback = next;
            rounds.removeAllItems();
            var markers = new Hashtable<Integer, JLabel>();
            if (next != null) {
                slider.setMinimum(0); slider.setMaximum(next.length() - 1);
                for (var round : next.navigation().rounds()) {
                    rounds.addItem(round);
                    JLabel label = new JLabel("R" + round.round());
                    label.setFont(Theme.bold(10)); label.setForeground(Theme.BRASS_LIGHT);
                    markers.put(round.index(), label);
                }
            } else { slider.setMinimum(0); slider.setMaximum(0); }
            slider.setLabelTable(markers.isEmpty() ? null : markers);
            slider.setPaintLabels(!markers.isEmpty());
            slider.setEnabled(next != null && next.length() > 1);
            rounds.setEnabled(next != null && rounds.getItemCount() > 1);
        } finally { updating = false; }
        refresh();
    }

    void refresh() {
        updating = true;
        try {
            boolean ready = playback != null;
            first.setEnabled(ready && playback.index() > 0);
            last.setEnabled(ready && !playback.atEnd());
            previousRound.setEnabled(ready && playback.index() > 0);
            nextRound.setEnabled(ready && playback.navigation().rounds().stream()
                    .anyMatch(round -> round.index() > playback.index()));
            if (!ready) return;
            // Keep the slider's drag state intact when its seek synchronously notifies observers.
            if (slider.getValue() != playback.index()) slider.setValue(playback.index());
            rounds.setSelectedItem(playback.navigation().roundAt(playback.index()));
            slider.setToolTipText(playback.position());
            slider.getAccessibleContext().setAccessibleDescription(playback.position());
        } finally { updating = false; }
    }
}

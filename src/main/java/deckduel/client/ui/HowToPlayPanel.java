package deckduel.client.ui;

import deckduel.client.guide.TutorialLibrary;
import deckduel.client.guide.TutorialLibrary.Lesson;
import deckduel.client.state.ClientModel;
import deckduel.client.state.ClientModelListener;
import deckduel.client.state.ReplayPlayback;
import deckduel.client.state.Screen;
import deckduel.net.ReplayResponse;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;

/** English guide with paired, read-only training recordings; independent of account replays. */
final class HowToPlayPanel extends JPanel {
    private static final Logger LOG = Logger.getLogger(HowToPlayPanel.class.getName());
    private final ClientModel model;
    private final JComboBox<Lesson> chapters = new JComboBox<>();
    private final JTextArea rules = text(15), observation = text(14);
    private final JLabel status = new JLabel("Loading recorded examples…");
    private final JButton previous = UiKit.pill("Previous lesson", UiKit.Pill.GOLD_OUTLINE);
    private final JButton next = UiKit.pill("Next lesson", UiKit.Pill.GOLD_OUTLINE);
    private final JButton retry = UiKit.pill("Retry", UiKit.Pill.GOLD);
    private final ReplayPanel recording;
    private List<Lesson> lessons = List.of();
    private SwingWorker<List<Lesson>, Void> loader;

    HowToPlayPanel(ClientModel model) {
        this.model = model;
        setName("howToPlay"); setLayout(new BorderLayout(18, 14)); setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        getAccessibleContext().setAccessibleName("How to Play");
        JPanel header = new JPanel(new BorderLayout(16, 0)); header.setOpaque(false);
        JButton back = LobbyPanel.smallButton("Back", model::leaveToLobby); back.setName("guideBack");
        JLabel title = new JLabel("How to Play"); title.setFont(Theme.display(26)); title.setForeground(Theme.BRASS_LIGHT);
        header.add(back, BorderLayout.WEST); header.add(title, BorderLayout.CENTER);
        // The frame's music button remains in the top-right corner above this page.
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 68));
        add(header, BorderLayout.NORTH);
        JPanel article = new JPanel(new BorderLayout(0, 12)); article.setOpaque(false);
        article.setPreferredSize(new Dimension(320, 0));
        chapters.setName("guideChapters"); chapters.setFont(Theme.bold(15)); chapters.setMaximumRowCount(17);
        chapters.setBackground(Theme.BG); chapters.setForeground(Theme.CREAM);
        chapters.setBorder(BorderFactory.createLineBorder(Theme.BRASS));
        chapters.getAccessibleContext().setAccessibleName("Choose a lesson");
        chapters.addActionListener(event -> selectLesson());
        article.add(chapters, BorderLayout.NORTH);
        rules.setName("guideRules");
        JScrollPane scroll = scroll(rules); article.add(scroll, BorderLayout.CENTER);
        JPanel foot = new JPanel(new BorderLayout(0, 8)); foot.setOpaque(false);
        observation.setName("guideObservation"); observation.setForeground(Theme.BRASS_LIGHT);
        observation.setRows(5);
        foot.add(scroll(observation), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0)); buttons.setOpaque(false);
        previous.setName("guidePrevious"); next.setName("guideNext");
        previous.setFont(Theme.bold(11)); next.setFont(Theme.bold(11));
        previous.addActionListener(event -> chapters.setSelectedIndex(chapters.getSelectedIndex() - 1));
        next.addActionListener(event -> chapters.setSelectedIndex(chapters.getSelectedIndex() + 1));
        buttons.add(previous); buttons.add(next); foot.add(buttons, BorderLayout.SOUTH);
        article.add(foot, BorderLayout.SOUTH); add(article, BorderLayout.WEST);
        recording = new ReplayPanel(model::reducedMotion, this::active, model::leaveToLobby);
        recording.setName("guideRecording"); add(recording, BorderLayout.CENTER);
        JPanel loading = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); loading.setOpaque(false);
        status.setName("guideStatus"); status.setFont(Theme.normal(13)); status.setForeground(Theme.FADED);
        retry.setVisible(false); retry.setName("guideRetry"); retry.addActionListener(event -> load());
        loading.add(status); loading.add(retry); add(loading, BorderLayout.SOUTH);
        enabled(false);
        model.addListener(new ClientModelListener() {
            @Override public void onScreenChanged(Screen screen) {
                if (active()) {
                    if (lessons.isEmpty()) load(); else selectLesson();
                } else recording.stop();
            }
        });
    }

    private boolean active() { return model.screen() == Screen.HOW_TO_PLAY; }

    private void load() {
        if (loader != null) return;
        status.setText("Loading recorded examples…"); retry.setVisible(false);
        loader = new SwingWorker<>() {
            @Override protected List<Lesson> doInBackground() throws Exception { return TutorialLibrary.load(); }
            @Override protected void done() {
                try {
                    lessons = get();
                    chapters.setModel(new DefaultComboBoxModel<>(lessons.toArray(Lesson[]::new)));
                    enabled(true);
                    if (active()) selectLesson();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); failed(interrupted);
                } catch (ExecutionException failure) { failed(failure.getCause()); }
                finally { loader = null; }
            }
        };
        loader.execute();
    }

    private void failed(Throwable failure) {
        LOG.log(Level.WARNING, "could not load bundled guide recordings", failure);
        status.setText("Could not load the recorded examples. "); retry.setVisible(true);
    }

    private void enabled(boolean ready) {
        chapters.setEnabled(ready); previous.setEnabled(ready && chapters.getSelectedIndex() > 0);
        next.setEnabled(ready && chapters.getSelectedIndex() < chapters.getItemCount() - 1);
    }

    private void selectLesson() {
        Lesson lesson = (Lesson) chapters.getSelectedItem();
        if (!active() || lesson == null) return;
        recording.stop();
        rules.setText(lesson.text()); rules.setCaretPosition(0);
        var playback = new ReplayPlayback(new ReplayResponse(0, "Training opponent", true, "", true, lesson.frames()));
        playback.addListener(() -> {
            observation.setText(observation(lesson, playback));
            observation.setCaretPosition(0);
        });
        observation.setText(lesson.before()); observation.setCaretPosition(0);
        recording.openTraining(playback);
        status.setText("Lesson " + (chapters.getSelectedIndex() + 1) + " of " + lessons.size()
                + " · Recorded training game · Press Play to watch, or Next for one action.");
        enabled(true);
    }

    private static String observation(Lesson lesson, ReplayPlayback playback) {
        if (playback.index() == 0) return lesson.before();
        if (playback.atEnd()) return lesson.after();
        var frame = playback.frame();
        if (frame.choosingKing()) return "Both players are choosing. Neither new King is public yet.";
        if (frame.phase() == deckduel.core.card.MatchPhase.KING_SELECTION)
            return "Your King is confirmed privately. Both choices become public after the other player confirms.";
        if (frame.choiceKind() == deckduel.core.card.ChoiceKind.SECRET_EDICT)
            return "Secret Edict shows two cards only to you. One manual draw has been spent; keep one card and bottom the other without ending your turn.";
        if (frame.keeping()) return "Only you see these inspected cards. Keep one; the others go to the deck bottom and the Spell turn ends.";
        return playback.action();
    }

    private static JTextArea text(int size) {
        JTextArea area = new JTextArea(); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
        area.setFont(Theme.normal(size)); area.setForeground(Theme.CREAM); area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 8));
        return area;
    }

    private static JScrollPane scroll(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area); scroll.setOpaque(false); scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BRASS));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }
}

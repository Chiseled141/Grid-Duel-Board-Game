package onitama.client.ui;

import onitama.core.GameState;
import onitama.core.HalfMove;
import onitama.core.Move;
import onitama.core.PlayerColor;
import onitama.core.RulesEngine;
import onitama.replay.ReplayFile;
import onitama.replay.ReplayFormatException;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Replay viewer dialog: opens a {@code .onitama-replay} file, replays every
 * half-move through the rules engine (corrupted files are reported, never
 * crashed on) and lets the user step forward and back through the game.
 * The replay is always viewed from Blue's side.
 */
public final class ReplayViewer extends JDialog {

    private final BoardPanel boardPanel = new BoardPanel();
    private final JLabel infoLabel = UiKit.label("No replay loaded", 14f);
    private final JLabel positionLabel = UiKit.label(" ", 13f);
    private final JButton prevButton = UiKit.pill("◀ Prev", UiKit.Pill.CREAM_OUTLINE);
    private final JButton nextButton = UiKit.pill("Next ▶", UiKit.Pill.CREAM_OUTLINE);

    private List<GameState> positions = List.of();
    private int index;

    /** Creates the (modal) viewer dialog. */
    public ReplayViewer(java.awt.Window owner) {
        super(owner, "Replay viewer", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(10, 10));
        setSize(600, 700);
        setLocationRelativeTo(owner);

        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(Theme.BG);
        infoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        north.add(infoLabel, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        controls.setBackground(Theme.BG);
        JButton open = UiKit.pill("Open replay", UiKit.Pill.GOLD);
        open.addActionListener(event -> openFile());
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);
        prevButton.addActionListener(event -> step(-1));
        nextButton.addActionListener(event -> step(1));
        controls.add(open);
        controls.add(prevButton);
        controls.add(positionLabel);
        controls.add(nextButton);
        north.add(controls, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER));
        center.setBackground(Theme.BG);
        center.add(boardPanel);
        add(center, BorderLayout.CENTER);
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Onitama replay", "onitama-replay"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        try {
            ReplayFile.Replay replay = ReplayFile.read(path);
            positions = computePositions(replay);
            index = 0;
            infoLabel.setText(replay.blueUsername() + " (blue)  vs  " + replay.redUsername()
                    + " (red)   —   " + replay.playedAt().toLocalDate()
                    + "   —   " + replay.moves().size() + " half-moves");
            prevButton.setEnabled(true);
            nextButton.setEnabled(true);
            showPosition();
        } catch (IOException e) {
            showError("Could not read the file: " + e.getMessage());
        } catch (ReplayFormatException e) {
            showError("This replay is invalid: " + e.getMessage());
        }
    }

    /**
     * Re-applies the recorded half-moves, snapshotting every intermediate
     * position via a serialization round-trip (the engine's GameState is
     * mutable, so stepping back needs true copies).
     */
    private List<GameState> computePositions(ReplayFile.Replay replay) {
        List<GameState> positions = new ArrayList<>();
        GameState current = replay.initialState();
        positions.add(copy(current));
        for (HalfMove move : replay.moves()) {
            if (move.isPass()) {
                RulesEngine.pass(current, move.cardId());
            } else {
                RulesEngine.apply(current,
                        new Move(move.from(), move.to(), move.cardId()));
            }
            positions.add(copy(current));
        }
        return positions;
    }

    private static GameState copy(GameState state) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var out = new java.io.ObjectOutputStream(bytes)) {
                out.writeObject(state);
            }
            try (var in = new java.io.ObjectInputStream(
                    new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
                return (GameState) in.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new ReplayFormatException("could not snapshot the game state");
        }
    }

    private void step(int direction) {
        int next = index + direction;
        if (next >= 0 && next < positions.size()) {
            index = next;
            showPosition();
        }
    }

    private void showPosition() {
        boardPanel.setView(positions.get(index), PlayerColor.BLUE);
        boardPanel.setSelection(null, List.of());
        boardPanel.setLastMove(index == 0 ? null : positions.get(index).lastMove());
        positionLabel.setText("MOVE " + index + " / " + (positions.size() - 1));
    }

    private void showError(String text) {
        javax.swing.JOptionPane.showMessageDialog(this, text, "Replay viewer",
                javax.swing.JOptionPane.ERROR_MESSAGE);
    }
}

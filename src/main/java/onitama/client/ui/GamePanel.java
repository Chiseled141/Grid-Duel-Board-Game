package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;
import onitama.core.GameState;
import onitama.net.GameOver;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;

/**
 * The game screen: opponent cards on top (patterns drawn from your own
 * perspective), the board in the center, your hand cards, the transit card,
 * turn indicator, move history and captured trays at the bottom/side. Click
 * a card and a piece (either order) to see legal destinations, then click a
 * destination to move. ESC clears the selection.
 */
public final class GamePanel extends JPanel {

    private final ClientModel model;

    private final BoardPanel boardPanel = new BoardPanel();
    private final JLabel turnLabel = new JLabel();
    private final JLabel opponentLabel = new JLabel();
    private final JLabel youLabel = new JLabel();
    private final JLabel transitLabel = new JLabel();
    private final JPanel opponentCards = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
    private final JPanel myCards = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
    private final JList<String> historyList = new JList<>();
    private final JLabel capturesLabel = new JLabel();

    /** Builds the game screen and subscribes it to the model. */
    public GamePanel(ClientModel model) {
        this.model = model;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(Theme.BACKGROUND);

        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(Theme.BACKGROUND);
        opponentLabel.setForeground(Theme.FOREGROUND);
        opponentLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        north.add(opponentLabel, BorderLayout.NORTH);
        north.add(opponentCards, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER));
        center.setBackground(Theme.BACKGROUND);
        center.add(boardPanel);
        add(center, BorderLayout.CENTER);

        add(buildHistoryPanel(), BorderLayout.EAST);
        add(buildSouthPanel(), BorderLayout.SOUTH);

        boardPanel.onClick(model::boardClicked);
        // ESC clears the card/piece selection.
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSelection");
        getActionMap().put("clearSelection", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                model.clearSelection();
                refresh();
            }
        });

        model.addListener(new ClientModelListener() {
            @Override
            public void onScreenChanged(Screen screen) {
                if (screen == Screen.GAME) {
                    refresh();
                }
            }

            @Override
            public void onMatchChanged() {
                refresh();
            }

            @Override
            public void onGameOver(GameOver over) {
                showGameOverDialog(over);
            }

            @Override
            public void onOpponentLeft(int graceSeconds) {
                turnLabel.setText("Opponent disconnected - reconnecting within "
                        + graceSeconds + "s or the match is forfeited");
            }

            @Override
            public void onRematchOffered() {
                turnLabel.setText("Opponent offered a rematch!");
            }
        });
    }

    private JPanel buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(BorderFactory.createTitledBorder("Moves"));
        panel.setPreferredSize(new java.awt.Dimension(230, 100));
        historyList.setBackground(Theme.BACKGROUND.brighter());
        historyList.setForeground(Theme.FOREGROUND);
        panel.add(new JScrollPane(historyList), BorderLayout.CENTER);
        capturesLabel.setForeground(Theme.FOREGROUND);
        panel.add(capturesLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSouthPanel() {
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(Theme.BACKGROUND);
        youLabel.setForeground(Theme.FOREGROUND);
        youLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        south.add(youLabel, BorderLayout.NORTH);

        JPanel middle = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 4));
        middle.setBackground(Theme.BACKGROUND);
        middle.add(myCards);
        transitLabel.setForeground(Theme.FOREGROUND);
        transitLabel.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);
        middle.add(transitLabel);
        south.add(middle, BorderLayout.CENTER);

        turnLabel.setForeground(Theme.SELECTION);
        turnLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        JButton resign = new JButton("Resign");
        resign.addActionListener(event -> confirmResign());
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Theme.BACKGROUND);
        bottom.add(turnLabel, BorderLayout.CENTER);
        bottom.add(resign, BorderLayout.EAST);
        south.add(bottom, BorderLayout.SOUTH);
        return south;
    }

    /** Redraws everything from the model (called on every match change). */
    private void refresh() {
        GameState state = model.state();
        if (state == null) {
            return;
        }
        opponentLabel.setText("Opponent: " + model.opponentName());
        youLabel.setText("You: " + (model.me() == null ? "" : model.me().username()));

        opponentCards.removeAll();
        state.hand(model.myColor().opponent()).forEach(card -> {
            // Opponent patterns are drawn from MY perspective (pre-rotated).
            opponentCards.add(new CardPanel(card, model.myColor(), null));
        });

        myCards.removeAll();
        state.hand(model.myColor()).forEach(card -> {
            CardPanel panel = new CardPanel(card, model.myColor(),
                    () -> model.cardClicked(card.id()));
            panel.setSelected(card.id().equals(model.selectedCardId()));
            myCards.add(panel);
        });

        transitLabel.setText("Transit: " + state.transit().name());
        turnLabel.setText(model.statusLine());
        historyList.setListData(model.historyLines().toArray(new String[0]));
        capturesLabel.setText("You captured: " + describe(model.myCaptures())
                + "    |    You lost: " + describe(model.enemyCaptures()));

        boardPanel.setView(state, model.myColor());
        boardPanel.setSelection(model.selectedSquare(), model.highlightedTargets());
        boardPanel.setLastMove(state.lastMove());
        revalidate();
        repaint();
    }

    private static String describe(java.util.List<onitama.core.Piece> pieces) {
        if (pieces.isEmpty()) {
            return "-";
        }
        StringBuilder text = new StringBuilder();
        for (onitama.core.Piece piece : pieces) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(piece.master() ? "Master" : "Student");
        }
        return text.toString();
    }

    private void confirmResign() {
        int answer = JOptionPane.showConfirmDialog(this,
                "Resign this match?", "Resign", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            model.resign();
        }
    }

    private void showGameOverDialog(GameOver over) {
        if (model.gameOverAnnounced()) {
            return;
        }
        model.markGameOverAnnounced();
        refresh();
        String result;
        if (over.winnerColor() == null) {
            result = "Draw - the move limit was reached.";
        } else if (over.winnerColor() == model.myColor()) {
            result = "You win by " + over.way() + "!";
        } else {
            result = model.opponentName() + " wins by " + over.way() + ".";
        }
        String ratings = "New Elo - you: " + myEloAfter(over) + ", opponent: "
                + opponentEloAfter(over);
        Object[] options = {"Offer rematch", "Back to lobby"};
        int choice = JOptionPane.showOptionDialog(this, result + "\n" + ratings,
                "Game over", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);
        if (choice == 0) {
            model.requestRematch();
        } else {
            model.leaveToLobby();
        }
    }

    private int myEloAfter(GameOver over) {
        return model.myColor() == onitama.core.PlayerColor.BLUE
                ? over.blueEloAfter() : over.redEloAfter();
    }

    private int opponentEloAfter(GameOver over) {
        return model.myColor() == onitama.core.PlayerColor.BLUE
                ? over.redEloAfter() : over.blueEloAfter();
    }
}

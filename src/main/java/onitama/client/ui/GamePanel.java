package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;
import onitama.core.GameState;
import onitama.core.Piece;
import onitama.net.GameOver;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;

/**
 * The game screen: opponent cards on top (patterns drawn from your own
 * perspective), the board in the center, your hand cards, the transit card,
 * the turn banner, move history and captured trays. Click a card and a piece
 * (either order) to see legal destinations, then click a destination to move.
 * ESC clears the selection.
 */
public final class GamePanel extends JPanel {

    private final ClientModel model;

    private final BoardPanel boardPanel = new BoardPanel();
    private final UiKit.HeadingLabel turnLabel = new UiKit.HeadingLabel(15f);
    private final JLabel opponentLabel = UiKit.label("", 14f);
    private final JLabel youLabel = UiKit.label("", 14f);
    private final JLabel transitLabel = UiKit.label("", 14f);
    private final JPanel opponentCards = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
    private final JPanel myCards = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
    private final JList<String> historyList = new JList<>();
    private final JLabel capturesLabel = UiKit.label(" ", 13f);

    /** Builds the game screen and subscribes it to the model. */
    public GamePanel(ClientModel model) {
        this.model = model;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(Theme.BG);

        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(Theme.BG);
        opponentLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        opponentCards.setBackground(Theme.BG);
        north.add(opponentLabel, BorderLayout.NORTH);
        north.add(opponentCards, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER));
        center.setBackground(Theme.BG);
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
                turnLabel.setText("OPPONENT LOST CONNECTION");
            }

            @Override
            public void onRematchOffered() {
                turnLabel.setText("OPPONENT WANTS A REMATCH!");
            }
        });
    }

    private JPanel buildHistoryPanel() {
        JPanel panel = UiKit.surface(10);
        panel.setLayout(new BorderLayout(6, 6));
        panel.setPreferredSize(new java.awt.Dimension(250, 120));

        JLabel title = UiKit.boldLabel("MOVES", 13f);
        panel.add(title, BorderLayout.NORTH);

        historyList.setBackground(Theme.SURFACE);
        historyList.setForeground(Theme.CREAM);
        historyList.setSelectionBackground(Theme.AMBER);
        historyList.setSelectionForeground(Theme.INK);
        historyList.setFont(Theme.normal(13f));
        JScrollPane scroll = new JScrollPane(historyList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(Theme.SURFACE);
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scroll, BorderLayout.CENTER);

        capturesLabel.setForeground(Theme.CREAM);
        panel.add(capturesLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSouthPanel() {
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(Theme.BG);
        youLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        south.add(youLabel, BorderLayout.NORTH);

        JPanel middle = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 4));
        middle.setBackground(Theme.BG);
        middle.add(myCards);
        middle.add(transitLabel);
        south.add(middle, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setBackground(Theme.BG);
        JPanel bannerRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bannerRow.setBackground(Theme.BG);
        bannerRow.add(turnLabel);
        bottom.add(bannerRow, BorderLayout.CENTER);
        JButton resign = UiKit.pill("Resign", UiKit.Pill.DANGER);
        resign.addActionListener(event -> confirmResign());
        JPanel resignRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        resignRow.setBackground(Theme.BG);
        resignRow.add(resign);
        bottom.add(resignRow, BorderLayout.EAST);
        south.add(bottom, BorderLayout.SOUTH);
        return south;
    }

    /** Redraws everything from the model (called on every match change). */
    private void refresh() {
        GameState state = model.state();
        if (state == null) {
            return;
        }
        opponentLabel.setText("vs  " + model.opponentName().toUpperCase());
        youLabel.setText(model.me() == null ? "" : model.me().username().toUpperCase());

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

        transitLabel.setText("transit: " + state.transit().name().toUpperCase());
        turnLabel.setText(model.statusLine().toUpperCase());
        historyList.setListData(model.historyLines().toArray(new String[0]));
        capturesLabel.setText("took: " + describe(model.myCaptures())
                + "   ·   lost: " + describe(model.enemyCaptures()));

        boardPanel.setView(state, model.myColor());
        boardPanel.setSelection(model.selectedSquare(), model.highlightedTargets());
        boardPanel.setLastMove(state.lastMove());
        revalidate();
        repaint();
    }

    private static String describe(List<Piece> pieces) {
        if (pieces.isEmpty()) {
            return "—";
        }
        StringBuilder text = new StringBuilder();
        for (Piece piece : pieces) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(piece.master() ? "master" : "student");
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
            result = "Draw — the move limit was reached.";
        } else if (over.winnerColor() == model.myColor()) {
            result = "You win by " + over.way() + "!";
        } else {
            result = model.opponentName() + " wins by " + over.way() + ".";
        }
        String ratings = "New Elo — you: " + myEloAfter(over) + ", opponent: "
                + opponentEloAfter(over);
        Object[] options = {"OFFER REMATCH", "BACK TO LOBBY"};
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

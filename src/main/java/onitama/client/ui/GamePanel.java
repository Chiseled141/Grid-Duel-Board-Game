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
    private final PieceTray myCapturesTray = new PieceTray("—");
    private final PieceTray enemyCapturesTray = new PieceTray("—");
    private JPanel myColumn;
    private int lastEffectMove = -1;

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

        // My cards live in a left column so the board can grow big in the
        // center on widescreen windows.
        add(buildMyColumn(), BorderLayout.WEST);

        // The board stretches to fill all remaining space (responsive).
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Theme.BG);
        center.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        center.add(boardPanel, BorderLayout.CENTER);
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
        panel.setPreferredSize(new java.awt.Dimension(260, 120));

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

        // Capture trays: tiny discs instead of words.
        JPanel trays = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        trays.setBackground(Theme.SURFACE);
        trays.add(UiKit.label("TOOK", 10f));
        trays.add(myCapturesTray);
        JPanel separator = new JPanel() { };
        separator.setBackground(Theme.SURFACE);
        separator.setPreferredSize(new java.awt.Dimension(2, 18));
        trays.add(separator);
        trays.add(UiKit.label("LOST", 10f));
        trays.add(enemyCapturesTray);
        panel.add(trays, BorderLayout.SOUTH);
        return panel;
    }

    /** The left column: your name, your two hand cards, the transit card. */
    private JPanel buildMyColumn() {
        myColumn = UiKit.surface(12);
        myColumn.setLayout(new BorderLayout(8, 8));
        myColumn.setPreferredSize(new java.awt.Dimension(230, 100));

        youLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        myColumn.add(youLabel, BorderLayout.NORTH);

        myCards.setLayout(new GridLayout(2, 1, 8, 8));
        myCards.setBackground(Theme.SURFACE);
        myCards.setOpaque(true);
        myColumn.add(myCards, BorderLayout.CENTER);

        JPanel transitRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        transitRow.setBackground(Theme.SURFACE);
        transitLabel.setFont(Theme.bold(12f));
        transitRow.add(transitLabel);
        myColumn.add(transitRow, BorderLayout.SOUTH);
        return myColumn;
    }

    private JPanel buildSouthPanel() {
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setBackground(Theme.BG);

        JPanel bannerRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bannerRow.setBackground(Theme.BG);
        bannerRow.add(turnLabel);
        south.add(bannerRow, BorderLayout.CENTER);

        JPanel resignRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        resignRow.setBackground(Theme.BG);
        JButton resign = UiKit.pill("Resign", UiKit.Pill.DANGER);
        resign.addActionListener(event -> confirmResign());
        resignRow.add(resign);
        south.add(resignRow, BorderLayout.EAST);
        return south;
    }

    /** Redraws everything from the model (called on every match change). */
    private void refresh() {
        GameState state = model.state();
        if (state == null) {
            return;
        }
        opponentLabel.setText("vs  " + model.opponentName().toUpperCase());
        opponentLabel.setForeground(Theme.playerColor(model.myColor().opponent()));
        youLabel.setText(model.me() == null ? "" : model.me().username().toUpperCase());
        youLabel.setForeground(Theme.playerColor(model.myColor()));
        // Player identity: your column carries your color's top border.
        myColumn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(3, 0, 0, 0,
                        Theme.playerColor(model.myColor())),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        opponentCards.removeAll();
        state.hand(model.myColor().opponent()).forEach(card -> {
            // Opponent patterns are drawn from MY perspective (pre-rotated)
            // and dimmed so your own cards stand out.
            opponentCards.add(new CardPanel(card, model.myColor(), true, null));
        });

        myCards.removeAll();
        state.hand(model.myColor()).forEach(card -> {
            CardPanel panel = new CardPanel(card, model.myColor(), false,
                    () -> model.cardClicked(card.id()));
            panel.setSelected(card.id().equals(model.selectedCardId()));
            JPanel slot = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            slot.setBackground(Theme.SURFACE);
            slot.add(panel);
            myCards.add(slot);
        });

        transitLabel.setText("transit: " + state.transit().name().toUpperCase());
        turnLabel.setText(model.statusLine().toUpperCase());
        historyList.setListData(model.historyLines().toArray(new String[0]));
        myCapturesTray.setPieces(model.myCaptures());
        enemyCapturesTray.setPieces(model.enemyCaptures());

        boardPanel.setView(state, model.myColor());
        boardPanel.setSelection(model.selectedSquare(), model.highlightedTargets());
        boardPanel.setLastMove(state.lastMove());
        if (state.moveNumber() != lastEffectMove) {
            lastEffectMove = state.moveNumber();
            if (model.lastCaptureSquare() != null) {
                boardPanel.playCaptureEffect(model.lastCaptureSquare(),
                        state.turn().opponent());
            }
        }
        revalidate();
        repaint();
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

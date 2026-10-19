package deckduel.client.ui;

import java.awt.*;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;

/** Empty card-table shell; it contains no old board, fake cards, or simulated game state. */
public final class CardTablePanel extends JPanel {
    private final DuelScorePanel score = new DuelScorePanel();
    private final JLabel notice = new JLabel("TABLE PREVIEW · New card gameplay is not ready yet", SwingConstants.CENTER);
    private final JButton back = UiKit.pill("Back to menu", UiKit.Pill.GOLD_OUTLINE);
    private final CompletableFuture<Image> background = CompletableFuture.supplyAsync(() -> AssetStore.optional("match-room.png"));

    /** Builds the scene and keeps returning to the menu available without a server connection. */
    public CardTablePanel(Runnable onBack) {
        setName("cardTablePreview");
        setLayout(null);
        setBackground(Theme.BG);
        score.setName("duelScorePanel");
        notice.setForeground(Theme.CREAM); notice.setFont(Theme.bold(13));
        back.addActionListener(event -> onBack.run());
        add(score); add(notice); add(back);
        background.thenRun(() -> SwingUtilities.invokeLater(this::repaint));
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "back");
        getActionMap().put("back", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { onBack.run(); }
        });
    }

    @Override public void doLayout() {
        int rail = Math.max(196, Math.min(252, getWidth() / 6));
        score.setBounds(20, 100, rail, Math.max(0, getHeight() - 120));
        back.setBounds(20, 20, 175, 42);
        notice.setBounds(220, 24, Math.max(0, getWidth() - 310), 32);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        BattlefieldMenuPanel.paintCover(graphics, background.getNow(null), getWidth(), getHeight(), this);
        Graphics2D g = UiKit.nice(graphics);
        g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 175), 0, 180, new Color(0, 0, 0, 0)));
        g.fillRect(0, 0, getWidth(), 180);
        int left = score.getX() + score.getWidth() + 28;
        int available = Math.max(0, getWidth() - left - 85);
        int gap = 10, width = Math.max(1, (available - 7 * gap) / 8);
        int height = Math.min(155, width * 3 / 2);
        row(g, left, getHeight() * 35 / 100, width, height, gap, "OPPONENT'S ROW");
        row(g, left, getHeight() * 62 / 100, width, height, gap, "YOUR ROW");
        g.dispose();
    }

    private void row(Graphics2D g, int left, int top, int width, int height, int gap, String title) {
        g.setFont(Theme.normal(12)); g.setColor(Theme.CREAM);
        g.drawString(title, left, top - 12);
        g.setColor(new Color(235, 212, 170, 90));
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{4, 6}, 0));
        for (int i = 0; i < 8; i++) g.drawRoundRect(left + i * (width + gap), top, width, height, 5, 5);
    }

    @Override public void removeNotify() { score.stopFeedback(); super.removeNotify(); }
}

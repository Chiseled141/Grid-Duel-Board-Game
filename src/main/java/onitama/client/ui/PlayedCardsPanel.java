package onitama.client.ui;

import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;
import onitama.config.SkillText;

import java.awt.*;
import java.util.List;
import javax.swing.*;

/** Scrollable public card-play timeline, with the latest revealed card first. */
final class PlayedCardsPanel extends JPanel {
    private final ClientModel model;
    private final DefaultListModel<ClientModel.PlayedCard> rows = new DefaultListModel<>();
    private final JList<ClientModel.PlayedCard> list = new JList<>(rows);
    private final JLabel count = UiKit.label("0 PLAYS", 10f);
    private List<ClientModel.PlayedCard> shown = List.of();
    private String matchId;

    PlayedCardsPanel(ClientModel model) {
        super(new BorderLayout(0, 8));
        this.model = model;
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(12, 10, 10, 10));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = UiKit.label("PLAYED CARDS", 13f);
        title.setFont(Theme.display(13f));
        title.setForeground(Theme.CREAM);
        count.setForeground(Theme.AMBER);
        header.add(title, BorderLayout.WEST);
        header.add(count, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        list.setName("playedCards");
        list.setBackground(Theme.BG);
        list.setFixedCellHeight(124);
        list.setCellRenderer((items, value, index, selected, focus) -> new PlayRow(value));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setMinimumSize(new Dimension(0, 0));
        JPanel content = new JPanel(new CardLayout());
        content.setOpaque(false);
        JTextArea empty = new JTextArea("The first move awaits.\n\nCards played by either side will appear here, newest first.");
        empty.setEditable(false);
        empty.setLineWrap(true);
        empty.setWrapStyleWord(true);
        empty.setFont(Theme.normal(13f));
        empty.setForeground(Theme.MUTED);
        empty.setOpaque(false);
        empty.setBorder(BorderFactory.createEmptyBorder(22, 4, 0, 4));
        content.add(empty, "empty");
        content.add(scroll, "plays");
        add(content, BorderLayout.CENTER);
        JLabel footer = UiKit.label("PUBLIC CARDS · LATEST FIRST", 9f);
        footer.setForeground(Theme.MUTED);
        add(footer, BorderLayout.SOUTH);
        model.addListener(new ClientModelListener() {
            @Override public void onMatchChanged() { refresh(content); }
            @Override public void onScreenChanged(Screen screen) { refresh(content); }
        });
        refresh(content);
    }

    private void refresh(JPanel content) {
        var plays = model.playedCards();
        String current = model.currentSync() == null ? null : model.currentSync().matchId();
        if (!java.util.Objects.equals(current, matchId) || !plays.equals(shown)) {
            shown = plays;
            matchId = current;
            rows.clear();
            for (int i = plays.size() - 1; i >= 0; i--) rows.addElement(plays.get(i));
            if (!rows.isEmpty()) list.ensureIndexIsVisible(0);
        }
        count.setText(plays.size() + " PLAYS");
        ((CardLayout) content.getLayout()).show(content, plays.isEmpty() ? "empty" : "plays");
    }

    private final class PlayRow extends JComponent {
        private final ClientModel.PlayedCard play;
        private final String name;
        private final String detail;
        private final Image art;

        PlayRow(ClientModel.PlayedCard play) {
            this.play = play;
            var rules = model.view().rules();
            if (play.skill()) {
                var def = rules.skill(play.definitionId());
                name = def == null ? play.definitionId() : def.name();
                detail = def == null ? "SKILL" : "SKILL · " + def.manaCost() + " MANA";
                String path = def == null ? "" : def.artworkPath();
                art = AssetStore.optional(path.startsWith("assets/") ? path.substring(7) : path);
                setToolTipText(def == null ? name : SkillText.render(def));
            } else {
                name = rules.movementCards().stream().filter(c -> c.id().equals(play.definitionId()))
                        .map(onitama.core.Card::name).findFirst().orElse(play.definitionId());
                detail = "MOVEMENT";
                String color = play.actor().name().toLowerCase(java.util.Locale.ROOT);
                art = AssetStore.optional("cards/" + color + "/" + play.definitionId() + "-" + color + ".png");
                setToolTipText(name);
            }
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = UiKit.nice(graphics);
            try {
                boolean own = play.actor() == model.myColor();
                Color accent = Theme.playerColor(play.actor());
                g.setColor(Theme.SURFACE);
                g.fillRoundRect(0, 0, getWidth(), 116, 12, 12);
                g.setColor(accent);
                g.fillRoundRect(0, 12, 3, 88, 3, 3);
                g.setColor(Theme.CREAM);
                g.fillRoundRect(10, 10, 64, 96, 6, 6);
                if (art != null) {
                    int w = art.getWidth(null), h = art.getHeight(null);
                    if (play.skill()) {
                        g.drawImage(art, 13, 13, 71, 103, (int)(w*.04), (int)(h*.20),
                                (int)(w*.96), (int)(h*.50), null);
                    } else g.drawImage(art, 12, 12, 60, 92, null);
                }
                int x = 84, width = Math.max(30, getWidth() - x - 8);
                g.setColor(accent);
                g.setFont(Theme.bold(10f));
                g.drawString(own ? "YOU" : "OPPONENT", x, 25);
                g.setColor(Theme.MUTED);
                g.setFont(Theme.normal(10f));
                g.drawString("TURN " + play.turn(), x, 42);
                g.setColor(Theme.CREAM);
                g.setFont(Theme.display(12f));
                String title = name.toUpperCase(java.util.Locale.ROOT);
                while (title.length() > 1 && g.getFontMetrics().stringWidth(title) > width)
                    title = title.substring(0, title.length() - 2) + "…";
                g.drawString(title, x, 65);
                g.setFont(Theme.normal(9f));
                g.setColor(Theme.MUTED);
                g.drawString(detail, x, 85);
            } finally { g.dispose(); }
        }
    }
}

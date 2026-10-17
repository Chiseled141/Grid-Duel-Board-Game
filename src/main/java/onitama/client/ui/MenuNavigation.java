package onitama.client.ui;

import java.awt.event.*;
import java.util.List;
import javax.swing.*;

/** One selection shared by mouse, focus and directional keys on a button-only menu page. */
final class MenuNavigation {
    static final String ACTIVE = "menu.navigationSelected";
    static final String MANAGED = "menu.navigationManaged";
    static final String HOME = "menu.homeAction";
    static final String ROUTE = "menu.routeAction";
    private final JPanel page;
    private final List<JButton> buttons;
    private final int initial;
    private JButton selected;

    MenuNavigation(JPanel page, List<JButton> buttons, int initial) {
        this.page = page; this.buttons = List.copyOf(buttons); this.initial = initial;
        for (JButton button : buttons) {
            button.putClientProperty(MANAGED, Boolean.TRUE);
            button.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { select(button, false); }
                @Override public void mousePressed(MouseEvent event) { select(button, false); }
            });
            button.addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent event) { select(button, false); }
            });
            button.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent event) { select(button, false); }
            });
        }
        bind("previous", () -> move(-1), "UP", "LEFT", "W", "A");
        bind("next", () -> move(1), "DOWN", "RIGHT", "S", "D");
        bind("activate", () -> { refresh(); if (selected != null) selected.doClick(0); }, "ENTER");
        if (!buttons.isEmpty() && buttons.get(0).getText().equals("Back"))
            bind("back", () -> buttons.get(0).doClick(0), "ESCAPE", "BACK_SPACE");
        reset();
    }

    private void bind(String name, Runnable action, String... keys) {
        String id = "menu." + name;
        for (String key : keys) page.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(key), id);
        page.getActionMap().put(id, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    void reset() {
        if (buttons.isEmpty()) { selected = null; return; }
        JButton preferred = buttons.get(Math.min(initial, buttons.size() - 1));
        select(available(preferred) ? preferred : firstAvailable(), false);
    }

    void focusSelection() {
        refresh();
        if (selected != null && page.isShowing()) selected.requestFocusInWindow();
    }

    void refresh() {
        if (!available(selected)) select(firstAvailable(), false);
    }

    private JButton firstAvailable() {
        return buttons.stream().filter(this::available).findFirst().orElse(null);
    }

    private boolean available(JButton button) {
        return button != null && button.isVisible() && button.isEnabled();
    }

    private void move(int step) {
        int start = buttons.indexOf(selected);
        for (int count = 1; count <= buttons.size(); count++) {
            JButton candidate = buttons.get(Math.floorMod(start + step * count, buttons.size()));
            if (available(candidate)) { select(candidate, true); return; }
        }
    }

    private void select(JButton button, boolean focus) {
        if (button != null && !available(button)) return;
        if (selected == button && button != null && Boolean.TRUE.equals(button.getClientProperty(ACTIVE))) {
            if (focus) button.requestFocusInWindow();
            return;
        }
        selected = button;
        for (JButton candidate : buttons) {
            boolean active = candidate == button;
            candidate.putClientProperty(ACTIVE, active);
            if (Boolean.TRUE.equals(candidate.getClientProperty(HOME)))
                candidate.setFont(active ? Theme.display(34f) : Theme.normal(25f));
            else if (Boolean.TRUE.equals(candidate.getClientProperty(ROUTE)))
                candidate.setFont(active ? Theme.bold(18f) : Theme.normal(15f));
            else candidate.setFont(active ? Theme.bold(13f) : Theme.normal(13f));
            candidate.repaint();
        }
        if (focus && button != null) button.requestFocusInWindow();
    }
}

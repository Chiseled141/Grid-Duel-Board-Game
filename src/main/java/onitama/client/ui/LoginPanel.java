package onitama.client.ui;

import onitama.client.ClientSettings;
import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;

import java.awt.BasicStroke;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * Login and registration screen: a cream sticker card centered on the coal
 * background with gold pill buttons. Credentials validate inline (short
 * entries shake the card), the password has a show/hide eye, and a muted
 * hint names the server the client will dial. Failed attempts are shown
 * inline.
 */
public final class LoginPanel extends JPanel {

    private static final char BULLET = '•';
    private static final int MIN_CREDENTIAL_LENGTH = 3;

    private final ClientModel model;
    private final ClientSettings settings;

    private final JTextField usernameField = new JTextField(14);
    private final JPasswordField passwordField = new JPasswordField(14);
    private final JLabel errorLabel = new JLabel(" ");
    private final JPanel card;
    /** The card's resting position; the shake animation returns it here. */
    private int cardHomeX;
    private int cardHomeY;
    private javax.swing.Timer shakeTimer;

    /** Builds the panel and subscribes it to the model. */
    public LoginPanel(ClientModel model, ClientSettings settings) {
        this.model = model;
        this.settings = settings;
        setLayout(new GridBagLayout());
        setBackground(Theme.BG);

        // Connection target comes from the saved settings (CLI overrides are
        // merged by ClientFrame); the form only asks for credentials.
        styleField(usernameField);
        styleField(passwordField);
        passwordField.setEchoChar(BULLET);
        // Enter submits from either field (§16).
        usernameField.addActionListener(event -> submit(true));
        passwordField.addActionListener(event -> submit(true));
        // The login screen is the entry screen: focus the username on show.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent event) {
                SwingUtilities.invokeLater(usernameField::requestFocusInWindow);
            }
        });

        card = UiKit.sticker(26);
        card.setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 6, 5, 6);
        constraints.fill = GridBagConstraints.HORIZONTAL;

        // Title row: starburst flourish + wordmark in the display font.
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        JPanel title = new JPanel(new GridBagLayout());
        title.setOpaque(false);
        title.add(UiKit.flourish(26));
        JLabel wordmark = new JLabel("ONITAMA ONLINE");
        wordmark.setFont(Theme.display(24f));
        wordmark.setForeground(Theme.INK);
        title.add(wordmark);
        card.add(title, constraints);

        constraints.gridwidth = 1;
        addFieldRow(card, constraints, 1, "USERNAME", usernameField);
        // The password row wraps the field and the eye toggle in one cell.
        JPanel passwordRow = new JPanel(new java.awt.BorderLayout(6, 0));
        passwordRow.setOpaque(false);
        passwordRow.add(passwordField, java.awt.BorderLayout.CENTER);
        passwordRow.add(eyeToggle(), java.awt.BorderLayout.EAST);
        constraints.gridx = 0;
        constraints.gridy = 2;
        JLabel passwordLabel = new JLabel("PASSWORD");
        passwordLabel.setFont(Theme.bold(12f));
        passwordLabel.setForeground(Theme.INK);
        card.add(passwordLabel, constraints);
        constraints.gridx = 1;
        card.add(passwordRow, constraints);

        constraints.gridx = 0;
        constraints.gridy = 3;
        JButton register = UiKit.pill("Register", UiKit.Pill.GOLD);
        register.addActionListener(event -> submit(false));
        card.add(register, constraints);
        constraints.gridx = 1;
        JButton login = UiKit.pill("Login", UiKit.Pill.GOLD_OUTLINE);
        login.addActionListener(event -> submit(true));
        card.add(login, constraints);

        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        errorLabel.setForeground(Theme.CORAL);
        errorLabel.setFont(Theme.bold(11f));
        errorLabel.setHorizontalAlignment(JLabel.CENTER);
        card.add(errorLabel, constraints);

        constraints.gridx = 0;
        constraints.gridy = 5;
        JLabel serverHint = new JLabel("SERVER  "
                + settings.host() + ":" + settings.port());
        serverHint.setFont(Theme.normal(10f));
        serverHint.setForeground(Theme.MUTED);
        serverHint.setHorizontalAlignment(JLabel.CENTER);
        card.add(serverHint, constraints);

        add(card, new GridBagConstraints());

        model.addListener(new ClientModelListener() {
            @Override
            public void onLoginFailed(String reason) {
                errorLabel.setText(reason);
            }

            @Override
            public void onScreenChanged(Screen screen) {
                if (screen == Screen.LOGIN) {
                    errorLabel.setText(" ");
                }
            }
        });
    }

    /**
     * The show/hide password eye: drawn icon, toggles the echo character
     * between the bullet and plain text.
     */
    private JToggleButton eyeToggle() {
        JToggleButton eye = new JToggleButton() {
            {
                setSelected(false);
                setToolTipText("Show/Hide password");
                setOpaque(false);
                setContentAreaFilled(false);
                setFocusPainted(false);
                setBorderPainted(false);
                setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
                setPreferredSize(new java.awt.Dimension(26, 26));
                addItemListener(event -> {
                    passwordField.setEchoChar(isSelected() ? 0 : BULLET);
                    repaint();
                });
            }

            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                var g = UiKit.nice(graphics);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g.setColor(Theme.MUTED);
                g.setStroke(new BasicStroke(1.8f));
                g.drawOval(cx - 8, cy - 5, 16, 10);
                g.fillOval(cx - 3, cy - 3, 6, 6);
                if (!isSelected()) {
                    // slash = password hidden (echo on)
                    g.setColor(Theme.MUTED);
                    g.drawLine(cx - 9, cy + 7, cx + 9, cy - 7);
                }
                g.dispose();
            }
        };
        return eye;
    }

    private void addFieldRow(JPanel card, GridBagConstraints constraints,
                             int row, String labelText, JTextField field) {
        constraints.gridx = 0;
        constraints.gridy = row;
        JLabel label = new JLabel(labelText);
        label.setFont(Theme.bold(12f));
        label.setForeground(Theme.INK);
        card.add(label, constraints);
        constraints.gridx = 1;
        card.add(field, constraints);
    }

    private void styleField(JTextField field) {
        field.setBackground(Theme.BOARD_LIGHT);
        field.setForeground(Theme.INK);
        field.setCaretColor(Theme.INK);
        field.setBorder(UiKit.fieldBorder());
        field.setFont(Theme.normal(14f));
    }

    /** Reads the fields, validates inline, then sends the request. */
    private void submit(boolean isLogin) {
        errorLabel.setText(" ");
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.length() < MIN_CREDENTIAL_LENGTH
                || password.length() < MIN_CREDENTIAL_LENGTH) {
            errorLabel.setText("Username and password need at least "
                    + MIN_CREDENTIAL_LENGTH + " characters");
            shakeCard();
            return;
        }
        String host = settings.host();
        int port = settings.port();
        if (isLogin) {
            model.login(host, port, username, password);
        } else {
            model.register(host, port, username, password);
        }
    }

    /**
     * Shakes the card ±4px in six 40ms steps to flag invalid input, then
     * restores its home position. Purely visual, runs on the EDT.
     */
    private void shakeCard() {
        if (shakeTimer != null && shakeTimer.isRunning()) {
            return;
        }
        cardHomeX = card.getX();
        cardHomeY = card.getY();
        int[] offsets = {-4, 4, -4, 4, -2, 0};
        final int[] step = {0};
        shakeTimer = new javax.swing.Timer(40, event -> {
            card.setLocation(cardHomeX + offsets[step[0]], cardHomeY);
            step[0]++;
            if (step[0] >= offsets.length) {
                ((javax.swing.Timer) event.getSource()).stop();
                card.setLocation(cardHomeX, cardHomeY);
            }
        });
        shakeTimer.start();
    }
}

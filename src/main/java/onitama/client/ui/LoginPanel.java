package onitama.client.ui;

import onitama.client.ClientSettings;
import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * Login and registration screen: a cream sticker card centered on the coal
 * background with gold pill buttons. Failed attempts are shown inline.
 */
public final class LoginPanel extends JPanel {

    private final ClientModel model;
    private final ClientSettings settings;

    private final JTextField hostField = new JTextField(14);
    private final JTextField portField = new JTextField(5);
    private final JTextField usernameField = new JTextField(14);
    private final JPasswordField passwordField = new JPasswordField(14);
    private final JLabel errorLabel = new JLabel(" ");

    /** Builds the panel and subscribes it to the model. */
    public LoginPanel(ClientModel model, ClientSettings settings) {
        this.model = model;
        this.settings = settings;
        setLayout(new GridBagLayout());
        setBackground(Theme.BG);

        hostField.setText(settings.host());
        portField.setText(String.valueOf(settings.port()));
        styleField(hostField);
        styleField(portField);
        styleField(usernameField);
        styleField(passwordField);

        JPanel card = UiKit.sticker(26);
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
        addFieldRow(card, constraints, 1, "SERVER HOST", hostField);
        addFieldRow(card, constraints, 2, "SERVER PORT", portField);
        addFieldRow(card, constraints, 3, "USERNAME", usernameField);
        addFieldRow(card, constraints, 4, "PASSWORD", passwordField);

        constraints.gridx = 0;
        constraints.gridy = 5;
        JButton register = UiKit.pill("Register", UiKit.Pill.GOLD);
        register.addActionListener(event -> submit(false));
        card.add(register, constraints);
        constraints.gridx = 1;
        JButton login = UiKit.pill("Login", UiKit.Pill.GOLD_OUTLINE);
        login.addActionListener(event -> submit(true));
        card.add(login, constraints);

        constraints.gridx = 0;
        constraints.gridy = 6;
        constraints.gridwidth = 2;
        errorLabel.setForeground(Theme.CORAL);
        errorLabel.setFont(Theme.bold(13f));
        card.add(errorLabel, constraints);

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

    /** Reads the fields, remembers the connection and sends the request. */
    private void submit(boolean isLogin) {
        errorLabel.setText(" ");
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            errorLabel.setText("Port must be a number");
            return;
        }
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please enter username and password");
            return;
        }
        settings.setHost(host);
        settings.setPort(port);
        if (isLogin) {
            model.login(host, port, username, password);
        } else {
            model.register(host, port, username, password);
        }
    }
}

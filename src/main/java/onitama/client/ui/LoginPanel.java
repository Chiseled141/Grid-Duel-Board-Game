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
 * Login and registration screen: server address, credentials, and two
 * actions. Failed attempts are shown inline in a red label.
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
        setBackground(Theme.BACKGROUND);

        hostField.setText(settings.host());
        portField.setText(String.valueOf(settings.port()));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 6, 6, 6);
        constraints.fill = GridBagConstraints.HORIZONTAL;

        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        JLabel title = new JLabel("Onitama Online");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.ACCENT);
        add(title, constraints);

        constraints.gridwidth = 1;
        addLabel(constraints, 1, "Server host");
        constraints.gridx = 1;
        add(hostField, constraints);
        addLabel(constraints, 2, "Server port");
        constraints.gridx = 1;
        add(portField, constraints);
        addLabel(constraints, 3, "Username");
        constraints.gridx = 1;
        add(usernameField, constraints);
        addLabel(constraints, 4, "Password");
        constraints.gridx = 1;
        add(passwordField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 5;
        JButton login = new JButton("Login");
        login.addActionListener(event -> submit(true));
        add(login, constraints);
        constraints.gridx = 1;
        JButton register = new JButton("Register");
        register.addActionListener(event -> submit(false));
        add(register, constraints);

        constraints.gridx = 0;
        constraints.gridy = 6;
        constraints.gridwidth = 2;
        errorLabel.setForeground(Theme.ERROR);
        add(errorLabel, constraints);

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

    private void addLabel(GridBagConstraints constraints, int row, String text) {
        constraints.gridx = 0;
        constraints.gridy = row;
        JLabel label = new JLabel(text);
        label.setForeground(Theme.FOREGROUND);
        add(label, constraints);
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

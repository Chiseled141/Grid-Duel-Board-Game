package onitama.client.ui;

import onitama.client.ClientSettings;
import onitama.client.MenuTheme;
import onitama.client.state.ClientModel;
import onitama.client.state.ClientModelListener;
import onitama.client.state.Screen;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Plays the menu's authored GIF while visible, with a still frame for reduced motion. */
abstract class ValleyMenuPanel extends JPanel {
    private static final Logger LOG = Logger.getLogger(ValleyMenuPanel.class.getName());
    private static final ConcurrentHashMap<MenuTheme, CompletableFuture<Clip>> CLIPS = new ConcurrentHashMap<>();
    private final ClientModel model;
    private final Screen screen;
    private final ClientSettings settings;
    private MenuTheme requestedTheme;
    private CompletableFuture<Clip> requestedClip;

    ValleyMenuPanel(ClientModel model, Screen screen, ClientSettings settings) {
        this.model = model;
        this.screen = screen;
        this.settings = settings;
        setBackground(Theme.BG);
        model.addListener(new ClientModelListener() {
            @Override public void onScreenChanged(Screen next) { repaint(); }
            @Override public void onMatchChanged() { repaint(); }
        });
        requestClip();
    }

    private void requestClip() {
        MenuTheme theme = settings.menuTheme();
        if (requestedTheme == theme) return;
        requestedTheme = theme;
        requestedClip = CLIPS.computeIfAbsent(theme, value ->
                CompletableFuture.supplyAsync(() -> loadClip(value)));
        requestedClip.thenRun(() -> SwingUtilities.invokeLater(this::repaint));
    }

    boolean animationEnabled() {
        return isShowing() && model.screen() == screen && !model.reducedMotion();
    }

    @Override public boolean imageUpdate(Image image, int flags, int x, int y, int width, int height) {
        // Returning false detaches hidden menus from the native GIF animation observer.
        if (!animationEnabled()) return false;
        var future = CLIPS.get(settings.menuTheme());
        Clip clip = future == null ? null : future.getNow(null);
        if (clip == null || image != clip.animation()) return false;
        return super.imageUpdate(image, flags, x, y, width, height);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        requestClip();
        Clip clip = requestedClip.getNow(null);
        if (clip == null) return;
        Image image = animationEnabled() ? clip.animation() : clip.still();
        paintCover(graphics, image, getWidth(), getHeight(), this);
    }

    static void paintCover(Graphics graphics, Image image, int width, int height, ImageObserver observer) {
        if (image == null || width <= 0 || height <= 0) return;
        int sourceWidth = image.getWidth(observer), sourceHeight = image.getHeight(observer);
        if (sourceWidth <= 0 || sourceHeight <= 0) return;
        double scale = Math.max(width / (double) sourceWidth, height / (double) sourceHeight);
        int drawWidth = (int) Math.ceil(sourceWidth * scale);
        int drawHeight = (int) Math.ceil(sourceHeight * scale);
        graphics.drawImage(image, 0, (height - drawHeight) / 2, drawWidth, drawHeight, observer);
    }

    private static Clip loadClip(MenuTheme theme) {
        var resource = ValleyMenuPanel.class.getResource("/assets/" + theme.clip());
        if (resource != null) {
            try {
                BufferedImage still = ImageIO.read(resource);
                ImageIcon icon = new ImageIcon(resource);
                if (still != null && icon.getIconWidth() > 0) return new Clip(icon.getImage(), still);
            } catch (IOException | RuntimeException exception) {
                LOG.log(Level.WARNING, "Could not load menu animation", exception);
            }
        }
        Image fallback = AssetStore.optional(theme.still());
        return new Clip(fallback, fallback);
    }

    private record Clip(Image animation, Image still) { }
}

package deckduel.client.ui;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * The painted props of the table's effects, read from {@code /assets/vfx/}: iron chain links, a
 * war axe, a flame tongue, a brass hook, a smoke puff, the Spy's eye, the binding seal and the
 * Exchange vortex. The light props are painted on black and drawn additively, so the black adds
 * nothing to the table. JavaFX Application Thread only.
 */
final class VfxSprites {
    private static final List<String> PROPS = List.of("axe", "chain-link-flat", "chain-link-edge", "flame", "hook",
            "smoke-puff", "spy-eye", "binding-sigil", "vortex");
    // Where the axe painting's grip and the middle of its head sit, as fractions of the image.
    private static final double AXE_GRIP_X = .078, AXE_GRIP_Y = .516, AXE_HEAD_X = .807;
    // The centre of the hook's eye ring, where the wire is tied.
    private static final double HOOK_EYE_X = .356, HOOK_EYE_Y = .168;
    private static final Map<String, Image> CACHE = new HashMap<>();

    private VfxSprites() { }

    /** Decodes every prop now, while the table is built, rather than on a Spell's first frame. */
    static void preload() { PROPS.forEach(VfxSprites::image); }

    /** One iron chain link, seen flat ({@code edge} false) or edge-on, its long side horizontal and 22 wide. */
    static ImageView chainLink(boolean edge) { return centred(edge ? "chain-link-edge" : "chain-link-flat", 22); }

    /** A flame tongue {@code height} tall, white-gold at its root and red at its tip, centred on its origin. */
    static ImageView flame(double height) {
        ImageView view = view("flame", height / ratio("flame"), height);
        view.setBlendMode(BlendMode.ADD);
        return view;
    }

    /**
     * A bearded war axe lying along +x, {@code reach} from the grip to the middle of its head, the
     * blade at the far end. The view's origin is the grip, so a rotation about the origin swings it
     * as a hand would.
     */
    static ImageView axe(double reach) {
        double width = reach / (AXE_HEAD_X - AXE_GRIP_X), height = width * ratio("axe");
        ImageView view = view("axe", width, height);
        view.setX(-width * AXE_GRIP_X);
        view.setY(-height * AXE_GRIP_Y);
        return view;
    }

    /** A brass hook {@code height} tall hanging down from its eye ring, which is the view's origin. */
    static ImageView hook(double height) {
        double width = height / ratio("hook");
        ImageView view = view("hook", width, height);
        view.setX(-width * HOOK_EYE_X);
        view.setY(-height * HOOK_EYE_Y);
        return view;
    }

    /** A soft puff of dark smoke {@code size} across, centred on its origin. */
    static ImageView smokePuff(double size) { return centred("smoke-puff", size); }

    /** The Spy's eye of light, {@code width} across and centred on its origin. */
    static ImageView spyEye(double width) { return light("spy-eye", width); }

    /** The binding seal of Disable Tags, {@code size} across and centred on its origin. */
    static ImageView bindingSigil(double size) { return light("binding-sigil", size); }

    /** The swirling vortex of Exchange, {@code size} across and centred on its origin. */
    static ImageView vortex(double size) { return light("vortex", size); }

    private static ImageView light(String name, double width) {
        ImageView view = centred(name, width);
        view.setBlendMode(BlendMode.ADD);
        return view;
    }

    private static ImageView centred(String name, double width) {
        double height = width * ratio(name);
        return view(name, width, height);
    }

    private static double ratio(String name) {
        Image image = image(name);
        return image.getHeight() / image.getWidth();
    }

    /** A view of prop {@code name} shown at {@code w} by {@code h}, centred on its origin. */
    private static ImageView view(String name, double w, double h) {
        ImageView view = new ImageView(image(name));
        view.setFitWidth(w);
        view.setFitHeight(h);
        view.setSmooth(true);
        view.setX(-w / 2);
        view.setY(-h / 2);
        return view;
    }

    private static Image image(String name) {
        return CACHE.computeIfAbsent(name, key -> {
            URL url = VfxSprites.class.getResource("/assets/vfx/" + key + ".png");
            if (url == null) throw new IllegalStateException("Missing effect art /assets/vfx/" + key + ".png");
            Image image = new Image(url.toExternalForm());
            if (image.isError()) throw new IllegalStateException("Could not read effect art " + key, image.getException());
            return image;
        });
    }
}

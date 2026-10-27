package deckduel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.effect.BlendMode;
import org.junit.jupiter.api.Test;

class VfxSpritesTest {
    @Test void everyPaintedPropLoadsAtTheSizeItsEffectAsksFor() throws Exception {
        FxTesting.run(() -> {
            VfxSprites.preload();
            var flat = VfxSprites.chainLink(false);
            var edge = VfxSprites.chainLink(true);
            assertEquals(22, flat.getFitWidth(), 1e-9);
            assertTrue(edge.getFitHeight() < flat.getFitHeight() / 1.5, "an edge-on link is a thin bar");
            assertEquals(40, VfxSprites.flame(40).getFitHeight(), 1e-9);
            assertEquals(30, VfxSprites.smokePuff(30).getFitWidth(), 1e-9);
            for (var light : java.util.List.of(VfxSprites.flame(40), VfxSprites.spyEye(200),
                    VfxSprites.bindingSigil(100), VfxSprites.vortex(100)))
                assertEquals(BlendMode.ADD, light.getBlendMode(), "props painted on black are drawn as light");
        });
    }

    @Test void theAxeSwingsAboutItsGripAndTheHookHangsFromItsEye() throws Exception {
        FxTesting.run(() -> {
            var axe = VfxSprites.axe(170);
            assertTrue(axe.getX() < 0 && -axe.getX() < axe.getFitWidth() * .15, "the grip is near the left end");
            assertEquals(170, axe.getX() + axe.getFitWidth() * .807, 1e-6, "reach is grip to the head's middle");
            var hook = VfxSprites.hook(26);
            assertEquals(26, hook.getFitHeight(), 1e-9);
            assertTrue(hook.getY() < 0 && hook.getY() + hook.getFitHeight() > 20, "the hook hangs below its eye");
        });
    }
}

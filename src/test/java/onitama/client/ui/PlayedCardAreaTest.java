package onitama.client.ui;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Public played cards use the same working hover popup as hand cards. */
class PlayedCardAreaTest {
    private Object field(Object target, String name) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @Test void bothPublicAreasShowAndDismissHoverDetails() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("Public card details regression");
            try {
                frame.setLayout(new GridLayout(1, 2));
                for (String caption : new String[] {"YOU PLAYED", "OPPONENT PLAYED"}) {
                    var area = new PlayedCardArea(caption);
                    area.showCard(new BufferedImage(200, 240, BufferedImage.TYPE_INT_ARGB),
                            "<html>Public card rules: " + caption + "</html>");
                    frame.add(area);
                }
                frame.setSize(420, 340); frame.setVisible(true); frame.validate();
                for (var component : frame.getContentPane().getComponents()) {
                    var area = (PlayedCardArea) component;
                    var details = java.util.Arrays.stream(area.getMouseListeners())
                            .filter(CardDetails.class::isInstance).map(CardDetails.class::cast).findFirst().orElseThrow();
                    details.mouseEntered(new MouseEvent(area, MouseEvent.MOUSE_ENTERED, 1, 0, 40, 50, 0, false));
                    var delay = (Timer) field(details, "delay");
                    assertTrue(delay.isRunning());
                    for (var action : delay.getActionListeners()) action.actionPerformed(new ActionEvent(delay, 0, "hover"));
                    assertNotNull(field(details, "popup"), "hover opens a real popup for " + area.getToolTipText());
                    details.mouseExited(new MouseEvent(area, MouseEvent.MOUSE_EXITED, 2, 0, 0, 0, 0, false));
                    assertNull(field(details, "popup"));
                    assertFalse(delay.isRunning());
                    details.mouseEntered(new MouseEvent(area, MouseEvent.MOUSE_ENTERED, 3, 0, 40, 50, 0, false));
                    for (var action : delay.getActionListeners()) action.actionPerformed(new ActionEvent(delay, 0, "hover"));
                    assertNotNull(field(details, "popup"));
                    area.clear();
                    assertNull(field(details, "popup"), "reset dismisses the previous public card's popup");
                    assertNull(area.getToolTipText());
                }
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            finally { frame.dispose(); }
        });
    }
}

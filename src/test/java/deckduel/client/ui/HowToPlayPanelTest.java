package deckduel.client.ui;

import deckduel.client.state.ClientModel;
import deckduel.client.state.Screen;
import deckduel.client.state.ServerConnection;
import deckduel.net.*;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HowToPlayPanelTest {
    private record Ui(ClientModel model, HowToPlayPanel panel, List<Message> sent) { }

    private static <T> T edt(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work); SwingUtilities.invokeAndWait(task); return task.get();
    }
    private static Ui open() throws Exception {
        return edt(() -> {
            List<Message> sent = new ArrayList<>();
            var model = new ClientModel(new ServerConnection() {
                public void send(Message message) { sent.add(message); }
                public boolean isConnected() { return true; }
                public void ensureConnected(deckduel.client.startup.ServerRoute route) { }
            });
            var panel = new HowToPlayPanel(model);
            model.handleMessage(new LoginResponse(true, null, new UserProfile("learner", 0, 0)));
            sent.clear(); model.showHowToPlay();
            return new Ui(model, panel, sent);
        });
    }
    private static <T extends Component> T named(Container parent, Class<T> type, String name) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child) && name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container container) { T found = named(container, type, name); if (found != null) return found; }
        }
        return null;
    }

    @Test void quickGuideStepsShowATipAndClipEachAndBackLeaves() throws Exception {
        Ui ui = open();
        try {
            edt(() -> {
                assertEquals(GuideSteps.STEPS.get(0).title(), ui.panel.currentTitle());
                assertFalse(named(ui.panel, JButton.class, "guideStepPrevious").isEnabled());
                for (int i = 1; i < GuideSteps.STEPS.size(); i++) {
                    named(ui.panel, JButton.class, "guideStepNext").doClick();
                    assertEquals(GuideSteps.STEPS.get(i).title(), ui.panel.currentTitle());
                }
                assertFalse(named(ui.panel, JButton.class, "guideStepNext").isEnabled());
                named(ui.panel, JButton.class, "guideStep2").doClick();
                assertEquals(GuideSteps.STEPS.get(2).title(), ui.panel.currentTitle());
                for (GuideSteps.Step step : GuideSteps.STEPS)
                    assertNotNull(HowToPlayPanel.class.getResource(step.resource()), step.resource() + " is bundled");
                named(ui.panel, JButton.class, "guideBack").doClick();
                assertEquals(Screen.LOBBY, ui.model.screen());
                assertTrue(ui.sent.isEmpty()); return null;
            });
        } finally { edt(() -> { ui.model.leaveToLobby(); return null; }); }
    }
}

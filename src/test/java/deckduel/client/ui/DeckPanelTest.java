package deckduel.client.ui;

import deckduel.client.state.ClientModel;
import deckduel.client.state.Screen;
import deckduel.net.Message;
import deckduel.client.state.ServerConnection;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class DeckPanelTest {
    @Test void atlasOpensOfflineAndBackReturnsToLobbyWithoutSendingMessages() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<Message> messages = new ArrayList<>();
            ClientModel model = new ClientModel(new ServerConnection() {
                public void send(Message message) { messages.add(message); }
                public boolean isConnected() { return false; }
                public void ensureConnected(String host, int port) { fail("Atlas must stay offline"); }
            });
            model.openDeck(); assertEquals(Screen.DECK, model.screen());
            assertTrue(messages.isEmpty());
            DeckPanel panel = new DeckPanel(model::closeDeck);
            panel.getActionMap().get("back").actionPerformed(null);
            assertEquals(Screen.LOBBY, model.screen()); assertTrue(messages.isEmpty());
        });
    }

    @Test void referenceHasSevenSelectableRegionsAndNoNeighborHitOutsideThem() {
        assertEquals(7, AtlasRegion.ALL.size());
        int[][] centers = {{650,520},{1020,120},{390,160},{190,310},{220,480},{300,620},{1020,565}};
        for (int i = 0; i < centers.length; i++) {
            assertEquals(AtlasRegion.ALL.get(i), AtlasRegion.at(centers[i][0], centers[i][1]));
        }
        assertNull(AtlasRegion.at(1060,850)); // Wallachia is context, not one of the seven factions.
        assertNull(AtlasRegion.at(-1,-1));
    }

    @Test void pointerPickingRemainsAlignedAfterZoomAndDragAndButtonsSelectEveryFaction() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DeckPanel panel = new DeckPanel(() -> {}); panel.setSize(1280,800); layout(panel);
            JComponent map = (JComponent) named(panel,"atlasMap");
            Point hungary = nativeToScreen(map,650,520);
            click(map,hungary); assertEquals("Hungary",((JLabel) named(panel,"atlasCountry")).getText());
            map.dispatchEvent(new MouseWheelEvent(map,MouseEvent.MOUSE_WHEEL,0,0,
                hungary.x,hungary.y,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,1,-3));
            click(map,hungary); assertEquals("Hungary",((JLabel) named(panel,"atlasCountry")).getText());
            map.dispatchEvent(new MouseEvent(map,MouseEvent.MOUSE_PRESSED,0,0,hungary.x,hungary.y,1,false,MouseEvent.BUTTON1));
            map.dispatchEvent(new MouseEvent(map,MouseEvent.MOUSE_DRAGGED,0,MouseEvent.BUTTON1_DOWN_MASK,hungary.x+40,hungary.y,0,false,MouseEvent.NOBUTTON));
            map.dispatchEvent(new MouseEvent(map,MouseEvent.MOUSE_RELEASED,0,0,hungary.x+40,hungary.y,1,false,MouseEvent.BUTTON1));
            ((JButton) named(panel,"atlasPoland")).doClick();
            click(map,new Point(hungary.x+40,hungary.y));
            assertEquals("Hungary",((JLabel) named(panel,"atlasCountry")).getText());
            for (AtlasRegion region : AtlasRegion.ALL) {
                ((JButton) named(panel,"atlas"+region.name())).doClick();
                assertEquals(region.name(),((JLabel) named(panel,"atlasCountry")).getText());
            }
            AtomicInteger back = new AtomicInteger();
            DeckPanel other = new DeckPanel(back::incrementAndGet);
            ((JButton) named(other,"atlasBack")).doClick(); assertEquals(1,back.get());
        });
    }

    @Test void bundledIllustrationLoadsAndLayoutRendersAtDesktopSize() throws Exception {
        DeckPanel[] panel = new DeckPanel[1];
        SwingUtilities.invokeAndWait(() -> { panel[0] = new DeckPanel(() -> {}); panel[0].setSize(1440,900); layout(panel[0]); });
        var field = DeckPanel.class.getDeclaredField("artwork"); field.setAccessible(true);
        long deadline = System.nanoTime()+5_000_000_000L;
        boolean[] loaded = {false};
        while (!loaded[0] && System.nanoTime()<deadline) {
            SwingUtilities.invokeAndWait(() -> {
                try { loaded[0] = field.get(panel[0]) != null; }
                catch (IllegalAccessException exception) { throw new AssertionError(exception); }
            });
            if (!loaded[0]) Thread.sleep(30);
        }
        assertTrue(loaded[0],"Bundled map must decode");
        SwingUtilities.invokeAndWait(() -> {
            layout(panel[0]);
            BufferedImage image = new BufferedImage(1440,900,BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics(); panel[0].printAll(graphics); graphics.dispose();
            assertTrue(named(panel[0],"atlasMap").getWidth()>900);
            assertTrue(((JButton) named(panel[0],"atlasFlagSource")).isEnabled());
            assertTrue(((JLabel) named(panel[0],"atlasFlagTitle")).getText().contains("1440–1444"));
            ((JButton) named(panel[0],"atlasCroatia")).doClick();
            assertTrue(((JLabel) named(panel[0],"atlasFlagTitle")).getText().contains("Talovac"));
            ((JButton) named(panel[0],"atlasHungary")).doClick();
            String screenshot = System.getProperty("atlas.preview");
            if (screenshot!=null) {
                try { ImageIO.write(image,"png",new File(screenshot)); }
                catch (java.io.IOException exception) { throw new AssertionError(exception); }
            }
        });
    }

    private static void click(JComponent component, Point point) {
        component.dispatchEvent(new MouseEvent(component,MouseEvent.MOUSE_PRESSED,0,0,point.x,point.y,1,false,MouseEvent.BUTTON1));
        component.dispatchEvent(new MouseEvent(component,MouseEvent.MOUSE_RELEASED,0,0,point.x,point.y,1,false,MouseEvent.BUTTON1));
    }
    private static Point nativeToScreen(JComponent map,int x,int y) {
        double scale=Math.min(map.getWidth()/1255.,map.getHeight()/1024.);
        return new Point((int)((map.getWidth()-1255*scale)/2+x*scale),(int)((map.getHeight()-1024*scale)/2+y*scale));
    }
    private static void layout(Container parent) {
        parent.doLayout(); for (Component child:parent.getComponents()) if(child instanceof Container c) layout(c);
    }
    private static Component named(Container parent,String name) {
        for(Component child:parent.getComponents()) {
            if(name.equals(child.getName())) return child;
            if(child instanceof Container c) { Component found=find(c,name); if(found!=null) return found; }
        }
        throw new AssertionError("Missing "+name);
    }
    private static Component find(Container parent,String name) {
        for(Component child:parent.getComponents()) {
            if(name.equals(child.getName())) return child;
            if(child instanceof Container c) { Component found=find(c,name); if(found!=null) return found; }
        }
        return null;
    }
}

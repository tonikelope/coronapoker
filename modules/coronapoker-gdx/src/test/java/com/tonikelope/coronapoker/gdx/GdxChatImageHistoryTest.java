package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class GdxChatImageHistoryTest {

    @Test
    void sharesSwingWireFormatAndMovesSentImageToFront() {
        Properties properties = new Properties();
        GdxChatImageHistory.remember(properties, "https://one.test/a.png", true);
        GdxChatImageHistory.remember(properties, "https://two.test/b.gif", true);
        GdxChatImageHistory.remember(properties, "https://one.test/a.png", true);

        assertEquals(java.util.List.of("https://one.test/a.png",
                "https://two.test/b.gif"), GdxChatImageHistory.read(properties));
        assertTrue(properties.getProperty(GdxChatImageHistory.HISTORY_KEY)
                .contains("@"));
    }

    @Test
    void removesOneImageWhileKeepingTheOrderAndReceivePreference() {
        Properties properties = new Properties();
        GdxChatImageHistory.setAutoReceive(properties, false);
        GdxChatImageHistory.remember(properties, "https://one.test/a.png", false);
        GdxChatImageHistory.remember(properties, "https://two.test/b.gif", false);
        GdxChatImageHistory.remember(properties, "https://three.test/c.png", false);

        GdxChatImageHistory.remove(properties, "https://two.test/b.gif");
        GdxChatImageHistory.remove(properties, "https://two.test/b.gif");

        assertEquals(java.util.List.of("https://one.test/a.png",
                "https://three.test/c.png"), GdxChatImageHistory.read(properties));
        assertFalse(GdxChatImageHistory.autoReceive(properties));
    }

    @Test
    void damagedAndNonHttpEntriesStayOutOfGallery() {
        Properties properties = new Properties();
        properties.setProperty(GdxChatImageHistory.HISTORY_KEY,
                "not-base64@ZmlsZTovLy90bXAvYS5wbmc=");

        assertTrue(GdxChatImageHistory.read(properties).isEmpty());
        assertFalse(GdxChatImageHistory.isHttpUrl("file:///tmp/a.png"));
    }

    @Test
    void autoReceiveDefaultsOffAndSavedChoiceIsRespected() {
        Properties properties = new Properties();
        assertFalse(GdxChatImageHistory.autoReceive(properties));
        GdxChatImageHistory.setAutoReceive(properties, true);
        assertTrue(GdxChatImageHistory.autoReceive(properties));
    }
}

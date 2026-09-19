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
    void damagedAndNonHttpEntriesStayOutOfGallery() {
        Properties properties = new Properties();
        properties.setProperty(GdxChatImageHistory.HISTORY_KEY,
                "not-base64@ZmlsZTovLy90bXAvYS5wbmc=");

        assertTrue(GdxChatImageHistory.read(properties).isEmpty());
        assertFalse(GdxChatImageHistory.isHttpUrl("file:///tmp/a.png"));
    }

    @Test
    void autoReceiveDefaultsOnAndCanBeChanged() {
        Properties properties = new Properties();
        assertTrue(GdxChatImageHistory.autoReceive(properties));
        GdxChatImageHistory.setAutoReceive(properties, false);
        assertFalse(GdxChatImageHistory.autoReceive(properties));
    }
}

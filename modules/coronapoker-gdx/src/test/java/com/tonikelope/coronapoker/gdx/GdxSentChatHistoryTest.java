package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxSentChatHistoryTest {

    @Test
    void arrowsRecallSentTextAndRestoreTheUnsentDraft() {
        GdxSentChatHistory history = new GdxSentChatHistory();
        history.remember("primero #1#");
        history.remember("segundo");

        assertEquals("segundo", history.recall("borrador #2#", -1));
        assertEquals("primero #1#", history.recall("segundo", -1));
        assertEquals("primero #1#", history.recall("primero #1#", -1));
        assertEquals("segundo", history.recall("primero #1#", 1));
        assertEquals("borrador #2#", history.recall("segundo", 1));
    }

    @Test
    void historyKeepsTheMostRecentTwoHundredMessagesAndClearsBetweenRooms() {
        GdxSentChatHistory history = new GdxSentChatHistory();
        for (int index = 1; index <= 201; index++) {
            history.remember(Integer.toString(index));
        }
        String draft = "";
        for (int index = 201; index >= 2; index--) {
            draft = history.recall(draft, -1);
            assertEquals(Integer.toString(index), draft);
        }
        assertEquals("2", history.recall(draft, -1));
        history.clear();
        assertTrue(history.isEmpty());
        assertEquals("borrador", history.recall("borrador", -1));
    }
}

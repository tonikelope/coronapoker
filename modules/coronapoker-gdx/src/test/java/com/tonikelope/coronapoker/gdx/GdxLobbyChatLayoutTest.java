package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.LobbyChatMessage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxLobbyChatLayoutTest {

    @Test
    void imagesGetAUsefulConversationSizeAndPresenceRowsStayCompact() {
        assertEquals(280f, GdxFrontendScreen.lobbyMessageHeight(
                LobbyChatMessage.Type.IMAGE));
        assertEquals(70f, GdxFrontendScreen.lobbyMessageHeight(
                LobbyChatMessage.Type.TEXT));
        assertEquals(44f, GdxFrontendScreen.lobbyMessageHeight(
                LobbyChatMessage.Type.PLAYER_JOINED));
        assertEquals(430f, GdxFrontendScreen.lobbyMessageWidth(
                LobbyChatMessage.Type.IMAGE, 0f, 792f));
    }

    @Test
    void bubblesAreContentSizedButNeverEscapeTheConversationColumn() {
        assertEquals(300f, GdxFrontendScreen.lobbyMessageWidth(
                LobbyChatMessage.Type.TEXT, 300f, 792f));
        assertEquals(250f, GdxFrontendScreen.lobbyMessageWidth(
                LobbyChatMessage.Type.TEXT, 40f, 792f));
        assertEquals(792f, GdxFrontendScreen.lobbyMessageWidth(
                LobbyChatMessage.Type.TEXT, 1200f, 792f));
        assertEquals(180f, GdxFrontendScreen.lobbyMessageWidth(
                LobbyChatMessage.Type.IMAGE, 0f, 180f));
    }

    @Test
    void newestMixedMessagesFitWithoutClippingTheBottomOne() {
        List<LobbyChatMessage> messages = List.of(
                joined(1), joined(2), joined(3), joined(4), image(5));

        int first = GdxFrontendScreen.lobbyMessageStartIndex(messages, 420f);
        assertEquals(2, first);
        float used = 0f;
        for (int i = first; i < messages.size(); i++) {
            if (i > first) used += 10f;
            used += GdxFrontendScreen.lobbyMessageHeight(
                    messages.get(i).type());
        }
        assertTrue(used <= 420f);
    }

    @Test
    void scrollingUpAnchorsTheConversationBeforeTheNewestMessages() {
        List<LobbyChatMessage> messages = List.of(
                joined(1), joined(2), joined(3), joined(4), joined(5));

        assertEquals(0, GdxFrontendScreen.lobbyMessageStartIndex(
                messages, 420f, 3));
        assertEquals(2, GdxFrontendScreen.lobbyMessageStartIndex(
                messages, 150f, 4));
    }

    private static LobbyChatMessage joined(long sequence) {
        return new LobbyChatMessage(sequence, Instant.EPOCH,
                "CoronaBot$" + sequence,
                LobbyChatMessage.Type.PLAYER_JOINED, "");
    }

    private static LobbyChatMessage image(long sequence) {
        return new LobbyChatMessage(sequence, Instant.EPOCH, "server",
                LobbyChatMessage.Type.IMAGE,
                "https://example.invalid/image.gif");
    }
}

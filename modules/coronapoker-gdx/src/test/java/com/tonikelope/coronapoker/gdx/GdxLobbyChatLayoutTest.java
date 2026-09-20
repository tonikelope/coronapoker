package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
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
    void wrappedTextGrowsInsideTheConversationInsteadOfOverlapping() {
        assertEquals(70f, GdxFrontendScreen.lobbyMessageHeight(
                LobbyChatMessage.Type.TEXT, 1));
        assertEquals(130f, GdxFrontendScreen.lobbyMessageHeight(
                LobbyChatMessage.Type.TEXT, 3));
        assertEquals(280f, GdxFrontendScreen.lobbyMessageHeight(
                LobbyChatMessage.Type.TEXT, 8));
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

    @Test
    void variableHeightBubblesAreAccountedForByTheScrollAnchor() {
        assertEquals(2, GdxFrontendScreen.lobbyMessageStartIndexForHeights(
                List.of(44f, 130f, 280f), 419f, 3));
        assertEquals(1, GdxFrontendScreen.lobbyMessageStartIndexForHeights(
                List.of(44f, 130f, 280f), 430f, 3));
    }

    @Test
    void chatScrollUsesRealBubbleHeightAndNeverScrollsIntoBlankSpace() {
        List<Float> heights = List.of(44f, 130f, 280f);
        assertEquals(1, GdxFrontendScreen.lobbyMaximumScrollOffset(
                heights, 420f));
        assertEquals(0, GdxFrontendScreen.lobbyMaximumScrollOffset(
                List.of(44f, 44f), 420f));
        assertEquals(0f, GdxFrontendScreen.lobbyScrollProgress(
                heights, 0, 1));
        assertEquals(1f, GdxFrontendScreen.lobbyScrollProgress(
                heights, 1, 1));
        assertEquals(1, GdxFrontendScreen.lobbyScrollOffsetForProgress(
                heights, 0.75f, 1));

        List<Float> tallerHistory = List.of(44f, 70f, 280f, 100f, 70f);
        int maximum = GdxFrontendScreen.lobbyMaximumScrollOffset(
                tallerHistory, 300f);
        for (int offset = 0; offset <= maximum; offset++) {
            float progress = GdxFrontendScreen.lobbyScrollProgress(
                    tallerHistory, offset, maximum);
            assertEquals(offset,
                    GdxFrontendScreen.lobbyScrollOffsetForProgress(
                            tallerHistory, progress, maximum));
        }
    }

    @Test
    void inTableGalleryKeepsEightLargeThumbnailsInsideItsPanel() {
        Rectangle panel = new Rectangle(80f, 120f, 1072f, 500f);
        Rectangle[] cells = new Rectangle[8];
        for (int index = 0; index < cells.length; index++) {
            cells[index] = CoronaPokerGdxTable.tableGalleryCellBounds(index,
                    panel.x, panel.y, panel.width, panel.height);
            assertTrue(panel.contains(cells[index]));
            assertTrue(cells[index].width > 240f);
            assertTrue(cells[index].height > 220f);
            for (int previous = 0; previous < index; previous++) {
                assertTrue(!cells[index].overlaps(cells[previous]));
            }
        }
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

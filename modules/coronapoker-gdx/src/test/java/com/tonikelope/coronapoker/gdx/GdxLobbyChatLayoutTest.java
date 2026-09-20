package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import com.tonikelope.coronapoker.core.LobbyChatMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxLobbyChatLayoutTest {

    @Test
    void textComposerPreservesTheSwingHalfSecondAntiFloodContract() {
        assertTrue(GdxFrontendScreen.lobbyTextSendReady(
                10.5f, 10.5f, " hola "));
        assertTrue(!GdxFrontendScreen.lobbyTextSendReady(
                10.49f, 10.5f, "hola"));
        assertTrue(!GdxFrontendScreen.lobbyTextSendReady(
                11f, 10.5f, "   "));
    }

    @Test
    void imagesGetAUsefulConversationSizeAndPresenceRowsStayCompact() {
        assertEquals(280f, GdxFrontendScreen.lobbyMessageHeight(
                LobbyChatMessage.Type.IMAGE));
        assertEquals(82f, GdxFrontendScreen.lobbyMessageHeight(
                LobbyChatMessage.Type.TEXT));
        assertEquals(44f, GdxFrontendScreen.lobbyMessageHeight(
                LobbyChatMessage.Type.PLAYER_JOINED));
        assertEquals(430f, GdxFrontendScreen.lobbyMessageWidth(
                LobbyChatMessage.Type.IMAGE, 0f, 792f));
    }

    @Test
    void wrappedTextGrowsInsideTheConversationInsteadOfOverlapping() {
        assertEquals(82f, GdxFrontendScreen.lobbyMessageHeight(
                LobbyChatMessage.Type.TEXT, 1));
        assertEquals(146f, GdxFrontendScreen.lobbyMessageHeight(
                LobbyChatMessage.Type.TEXT, 3));
        assertEquals(306f, GdxFrontendScreen.lobbyMessageHeight(
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
    void chatScrollMovesContinuouslyInPixelsAcrossVariableHeightMessages() {
        List<Float> heights = List.of(44f, 82f, 280f, 146f);
        float content = GdxFrontendScreen.lobbyChatContentHeight(heights);
        assertEquals(582f, content);
        assertEquals(162f, GdxFrontendScreen.lobbyMaximumPixelScroll(
                content, 420f));
        assertEquals(48f, GdxFrontendScreen.lobbyPixelScrollAfterWheel(
                0f, 162f, -1f));
        assertEquals(96f, GdxFrontendScreen.lobbyPixelScrollAfterWheel(
                48f, 162f, -1f));
        assertEquals(0f, GdxFrontendScreen.lobbyPixelScrollAfterWheel(
                20f, 162f, 1f));
        assertEquals(162f, GdxFrontendScreen.lobbyPixelScrollAfterWheel(
                150f, 162f, -1f));
    }

    @Test
    void historyLayerNeverCoversTheImageOrEmojiDialogs() {
        assertTrue(GdxFrontendScreen.shouldDrawLobbyChatLayer(
                true, false, false, 806f, 420f));
        assertFalse(GdxFrontendScreen.shouldDrawLobbyChatLayer(
                true, true, false, 806f, 420f));
        assertFalse(GdxFrontendScreen.shouldDrawLobbyChatLayer(
                true, false, true, 806f, 420f));
        assertFalse(GdxFrontendScreen.shouldDrawLobbyChatLayer(
                true, false, false, 0f, 420f));
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

}

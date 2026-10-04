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
    void lobbyEmojiPickerPreservesBreathingRoomAroundNativeArtwork() {
        assertEquals(54f, GdxFrontendScreen.LOBBY_EMOJI_PICKER_CELL_SIZE);
        assertEquals(32f, GdxFrontendScreen.LOBBY_EMOJI_PICKER_IMAGE_SIZE);
        assertTrue(GdxFrontendScreen.LOBBY_EMOJI_PICKER_IMAGE_SIZE
                < GdxFrontendScreen.LOBBY_EMOJI_PICKER_CELL_SIZE);
        assertEquals(11f, (GdxFrontendScreen.LOBBY_EMOJI_PICKER_CELL_SIZE
                - GdxFrontendScreen.LOBBY_EMOJI_PICKER_IMAGE_SIZE) / 2f);
    }

    @Test
    void lobbyImageGalleryHasAStandardTopRightCloseControl() {
        Rectangle close = GdxFrontendScreen.lobbyImageGalleryCloseBounds(
                525f, 315f, 840f, 430f);
        assertEquals(1297f, close.x);
        assertEquals(677f, close.y);
        assertEquals(44f, close.width);
        assertEquals(48f, close.height);
        assertEquals(1,
                GdxFrontendScreen.LOBBY_IMAGE_GALLERY_CONTENT_DELAY_FRAMES);
    }

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
    void hostConnectionGameInfoAndActionsNeverOverlap() {
        float connectionBottom = GdxFrontendScreen.LOBBY_CONNECTION_Y;
        float passwordTop = GdxFrontendScreen.LOBBY_PASSWORD_Y
                + GdxFrontendScreen.LOBBY_PASSWORD_HEIGHT;
        float infoTop = GdxFrontendScreen.LOBBY_GAME_INFO_Y
                + GdxFrontendScreen.LOBBY_GAME_INFO_HEIGHT;
        float botTop = GdxFrontendScreen.LOBBY_BOT_BUTTON_Y
                + GdxFrontendScreen.LOBBY_BOT_BUTTON_HEIGHT;
        float kickTop = GdxFrontendScreen.LOBBY_KICK_BUTTON_Y
                + GdxFrontendScreen.LOBBY_KICK_BUTTON_HEIGHT;
        float playTop = GdxFrontendScreen.LOBBY_PLAY_BUTTON_Y
                + GdxFrontendScreen.LOBBY_PLAY_BUTTON_HEIGHT;

        assertTrue(passwordTop < connectionBottom);
        assertTrue(infoTop < GdxFrontendScreen.LOBBY_PASSWORD_Y);
        assertTrue(botTop < GdxFrontendScreen.LOBBY_GAME_INFO_Y);
        assertTrue(kickTop < GdxFrontendScreen.LOBBY_BOT_BUTTON_Y);
        assertTrue(playTop < GdxFrontendScreen.LOBBY_KICK_BUTTON_Y);
    }

    @Test
    void lobbyInformationFramesAlignWithTheActionButtons() {
        assertEquals(GdxFrontendScreen.LOBBY_LEFT_ACTION_X,
                GdxFrontendScreen.LOBBY_LEFT_CONTENT_X);
        assertEquals(GdxFrontendScreen.LOBBY_LEFT_ACTION_WIDTH,
                GdxFrontendScreen.LOBBY_LEFT_CONTENT_WIDTH);
    }

    @Test
    void participantCountHasOpticalAlignmentAndComfortableRightMargin() {
        assertEquals(GdxFrontendScreen.LOBBY_ROSTER_TITLE_BASELINE + 4f,
                GdxFrontendScreen.LOBBY_ROSTER_COUNT_BASELINE);
        float rosterPanelRight = 1425f + 460f;
        float countRight = GdxFrontendScreen.LOBBY_ROSTER_COUNT_X
                + GdxFrontendScreen.LOBBY_ROSTER_COUNT_WIDTH / 2f;
        assertEquals(21f, rosterPanelRight - countRight);
    }

    @Test
    void everyLobbyInformationRowStaysInsideItsFrameAtMaximumOccupancy() {
        int maximumVisibleRows = 3;
        float bottom = GdxFrontendScreen.LOBBY_GAME_INFO_Y;
        float top = bottom + GdxFrontendScreen.LOBBY_GAME_INFO_HEIGHT;
        float previous = Float.POSITIVE_INFINITY;
        for (int index = 0; index < maximumVisibleRows; index++) {
            float baseline = GdxFrontendScreen.lobbyInfoRowBaseline(bottom,
                    GdxFrontendScreen.LOBBY_GAME_INFO_HEIGHT,
                    maximumVisibleRows, index);
            assertTrue(baseline > bottom + 14f);
            assertTrue(baseline < top - 14f);
            assertTrue(baseline < previous);
            previous = baseline;
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

}

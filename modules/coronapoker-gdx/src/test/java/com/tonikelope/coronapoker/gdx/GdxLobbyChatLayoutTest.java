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
        assertEquals(72, GdxFrontendScreen.EMOJI_PAGE_SIZE);
        assertEquals(40f, GdxFrontendScreen.LOBBY_EMOJI_PICKER_CELL_SIZE);
        assertEquals(38f, GdxFrontendScreen.LOBBY_EMOJI_PICKER_IMAGE_SIZE);
        assertTrue(GdxFrontendScreen.LOBBY_EMOJI_PICKER_IMAGE_SIZE
                < GdxFrontendScreen.LOBBY_EMOJI_PICKER_CELL_SIZE);
        assertEquals(1f, (GdxFrontendScreen.LOBBY_EMOJI_PICKER_CELL_SIZE
                - GdxFrontendScreen.LOBBY_EMOJI_PICKER_IMAGE_SIZE) / 2f);
    }

    @Test
    void sharedGalleryOpensLargeWithoutCoveringTheLobbyComposer() {
        Rectangle panel = GdxImageGalleryWindow.initial(1920f, 1080f);
        Rectangle web = GdxImageGalleryWindow.webBounds(panel);
        Rectangle drag = GdxImageGalleryWindow.titleDragBounds(panel);
        assertTrue(panel.width > 1140f);
        assertTrue(panel.height > 720f);
        assertTrue(panel.contains(web));
        assertTrue(panel.contains(drag));
        assertFalse(web.overlaps(drag));
        assertTrue(drag.width < web.width);
        assertFalse(panel.overlaps(GdxFrontendScreen.lobbyComposerInputBounds()));
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
        assertEquals(40f, GdxFrontendScreen.lobbyMessageWidth(
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
    void historyLayerRemainsVisibleWhenThereIsRoomAndTheEmojiPickerIsClosed() {
        assertTrue(GdxFrontendScreen.shouldDrawLobbyChatLayer(
                true, false, 806f, 420f));
        assertFalse(GdxFrontendScreen.shouldDrawLobbyChatLayer(
                false, false, 806f, 420f));
        assertFalse(GdxFrontendScreen.shouldDrawLobbyChatLayer(
                true, true, 806f, 420f));
        assertFalse(GdxFrontendScreen.shouldDrawLobbyChatLayer(
                true, false, 0f, 420f));
    }

    @Test
    void hostConnectionGameInfoAndActionsNeverOverlap() {
        float connectionBottom = GdxFrontendScreen.LOBBY_CONNECTION_Y;
        float passwordTop = GdxFrontendScreen.LOBBY_PASSWORD_Y
                + GdxFrontendScreen.LOBBY_PASSWORD_HEIGHT;
        float infoTop = GdxFrontendScreen.LOBBY_GAME_INFO_Y
                + GdxFrontendScreen.LOBBY_GAME_INFO_HEIGHT;
        float addBotTop = GdxFrontendScreen.LOBBY_ADD_BOT_Y
                + GdxFrontendScreen.LOBBY_ADD_BOT_HEIGHT;
        float playTop = GdxFrontendScreen.LOBBY_PLAY_BUTTON_Y
                + GdxFrontendScreen.LOBBY_PLAY_BUTTON_HEIGHT;

        assertTrue(passwordTop < connectionBottom);
        assertTrue(infoTop < GdxFrontendScreen.LOBBY_PASSWORD_Y);
        assertTrue(addBotTop < GdxFrontendScreen.LOBBY_GAME_INFO_Y);
        assertTrue(playTop < GdxFrontendScreen.LOBBY_ADD_BOT_Y);
        assertTrue(GdxFrontendScreen.LOBBY_PUBLIC_ADDRESS_BASELINE
                - connectionBottom >= 28f,
                "the public IP needs breathing room above the panel edge");
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
    void lobbyVoiceButtonOwnsOneStablePressAndHoldTarget() {
        assertEquals(new Rectangle(1205f, 198f, 55f, 55f),
                GdxFrontendScreen.lobbyVoiceButtonBounds());
    }

    @Test
    void iconComposerGivesTheMessageFieldTheRecoveredHorizontalSpace() {
        Rectangle textInput = GdxFrontendScreen
                .lobbyComposerInputBounds();
        Rectangle emoji = GdxFrontendScreen
                .lobbyComposerEmojiBounds();
        Rectangle image = GdxFrontendScreen
                .lobbyComposerImageBounds();
        Rectangle voice = GdxFrontendScreen.lobbyVoiceButtonBounds();

        assertEquals(540f, textInput.width);
        assertEquals(10f, emoji.x - (textInput.x + textInput.width));
        assertEquals(10f, image.x - (emoji.x + emoji.width));
        assertEquals(10f, voice.x - (image.x + image.width));
        assertEquals(55f, emoji.width);
        assertEquals(55f, image.width);
        assertEquals(55f, voice.width);
        for (Rectangle control : List.of(textInput, emoji, image,
                voice)) {
            assertEquals(GdxFrontendScreen.LOBBY_PLAY_BUTTON_Y, control.y);
            assertEquals(GdxFrontendScreen.LOBBY_PLAY_BUTTON_HEIGHT,
                    control.height);
        }
    }

    @Test
    void lobbyInformationRowsFillTheBodyWithEqualPaddingWithOrWithoutBlindCap() {
        int maximumRows = GdxLobbyGameSummary.MAX_ROWS;
        for (int rowCount : new int[]{maximumRows - 1, maximumRows}) {
            float rowHeight = GdxFrontendScreen.lobbyInfoRowHeight(rowCount);
            assertTrue(rowHeight >= GdxFrontendScreen.LOBBY_GAME_INFO_MIN_ROW_HEIGHT);
            assertEquals(GdxFrontendScreen.LOBBY_GAME_INFO_HEIGHT,
                    GdxFrontendScreen.LOBBY_GAME_INFO_HEADER_HEIGHT
                    + rowHeight * rowCount
                    + 2f * GdxFrontendScreen.LOBBY_GAME_INFO_BODY_PADDING,
                    0.01f);
        }
    }

}

package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Rectangle;
import com.tonikelope.coronapoker.core.LobbyChatMessage;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxTableChatSessionTest {

    @Test
    void doesNotReplayLobbyHistoryAsTableNotifications() {
        LobbySession lobby = lobby();
        GdxTableChatSession chat = new GdxTableChatSession(lobby);

        assertTrue(chat.drainIncoming().isEmpty());
        lobby.publish(snapshot(List.of(message(0, "antes"), message(1, "ahora"))));

        assertEquals(List.of(message(1, "ahora")), chat.drainIncoming());
        chat.close();
    }

    @Test
    void sendsThroughTheCanonicalLobbyCommandSinkWhileInGame() {
        AtomicReference<LobbyCommand> sent = new AtomicReference<>();
        LobbySession lobby = new LobbySession(snapshot(List.of()), command -> {
            sent.set(command);
            return CompletableFuture.completedFuture(null);
        });
        GdxTableChatSession chat = new GdxTableChatSession(lobby);

        chat.sendText("hola #12#").toCompletableFuture().join();

        assertEquals(new LobbyCommand.SendText("hola #12#"), sent.get());
        chat.close();
    }

    @Test
    void deliversEveryConsecutiveMessageFromTheSameLocalPlayer() {
        LobbySession lobby = lobby();
        GdxTableChatSession chat = new GdxTableChatSession(lobby);

        LobbyChatMessage first = message(1, "uno");
        LobbyChatMessage second = message(2, "dos");
        LobbyChatMessage third = message(3, "tres");
        lobby.publish(snapshot(List.of(first)));
        assertEquals(List.of(first), chat.drainIncoming());
        lobby.publish(snapshot(List.of(first, second)));
        assertEquals(List.of(second), chat.drainIncoming());
        lobby.publish(snapshot(List.of(first, second, third)));
        assertEquals(List.of(third), chat.drainIncoming());

        chat.close();
    }

    @Test
    void sendsImageAndGifUrlsThroughTheCanonicalLobbyCommandSink() {
        AtomicReference<LobbyCommand> sent = new AtomicReference<>();
        LobbySession lobby = new LobbySession(snapshot(List.of()), command -> {
            sent.set(command);
            return CompletableFuture.completedFuture(null);
        });
        GdxTableChatSession chat = new GdxTableChatSession(lobby);

        chat.sendImage("https://example.invalid/reaccion.gif")
                .toCompletableFuture().join();

        assertEquals(new LobbyCommand.SendImage(
                "https://example.invalid/reaccion.gif"), sent.get());
        chat.close();
    }

    @Test
    void sendsVoiceThroughTheCanonicalLobbyCommandSink() {
        AtomicReference<LobbyCommand> sent = new AtomicReference<>();
        LobbySession lobby = new LobbySession(snapshot(List.of()), command -> {
            sent.set(command);
            return CompletableFuture.completedFuture(null);
        });
        GdxTableChatSession chat = new GdxTableChatSession(lobby);
        byte[] wav = {0x52, 0x49, 0x46, 0x46, 7, 9};

        chat.sendVoice(wav).toCompletableFuture().join();

        LobbyCommand.SendVoice voice = assertInstanceOf(
                LobbyCommand.SendVoice.class, sent.get());
        assertArrayEquals(wav, voice.wav());
        chat.close();
    }

    @Test
    void quickChatSummarisesMediaWithoutRenderingItInsideThePopup() {
        LobbyChatMessage image = new LobbyChatMessage(1L, Instant.EPOCH,
                "Ana", LobbyChatMessage.Type.IMAGE,
                "https://example.invalid/reaccion.gif");
        LobbyChatMessage voice = new LobbyChatMessage(2L, Instant.EPOCH,
                "Luis", LobbyChatMessage.Type.VOICE, "UklGRg==");

        assertEquals("envió una imagen", CoronaPokerGdxTable
                .quickChatMessageBody(image, new GdxGameText("es")));
        assertEquals("[NOTA DE VOZ]", CoronaPokerGdxTable
                .quickChatMessageBody(voice, new GdxGameText("es")));
        assertEquals("sent an image", CoronaPokerGdxTable
                .quickChatMessageBody(image, new GdxGameText("en")));
        assertEquals("[VOICE MESSAGE]", CoronaPokerGdxTable
                .quickChatMessageBody(voice, new GdxGameText("en")));
    }

    @Test
    void quickChatAutoCloseSwitchFollowsItsLabelAndStaysInsideThePanel() {
        assertEquals(250f, CoronaPokerGdxTable.quickChatAutoCloseSwitchX(
                100f, 720f, 120f), 0.001f);
        assertEquals(762f, CoronaPokerGdxTable.quickChatAutoCloseSwitchX(
                100f, 720f, 2_000f), 0.001f);
    }

    @Test
    void mediaConfirmationPreservesItsAspectRatioInsideAnyTarget() {
        com.badlogic.gdx.math.Rectangle target =
                new com.badlogic.gdx.math.Rectangle(100f, 200f, 286f, 120f);

        com.badlogic.gdx.math.Rectangle wide = CoronaPokerGdxTable
                .fitSeatChatNoticeBounds(target, 800f, 200f);
        com.badlogic.gdx.math.Rectangle tall = CoronaPokerGdxTable
                .fitSeatChatNoticeBounds(target, 200f, 600f);

        assertTrue(target.contains(wide));
        assertTrue(target.contains(tall));
        assertEquals(274f, wide.width, 0.001f);
        assertEquals(108f, tall.height, 0.001f);
    }

    @Test
    void remoteMediaUsesTheUpperCardCrossingAsItsScreenSafeCeiling() {
        float aspect = 1242f / 923f;
        float podX = 900f;
        float seatY = 920f;
        float podY = seatY - 120f - 42f;
        com.badlogic.gdx.math.Rectangle target = CoronaPokerGdxTable
                .remoteSeatChatNoticeTarget(podX, seatY, podY,
                        1920f, 1080f, aspect);

        float crossingTop = seatY + CoronaPokerGdxTable
                .rivalHandUpperCrossingY(aspect);
        assertEquals(crossingTop, target.y + target.height, 0.001f);
        assertTrue(target.y >= podY + 120f,
                "the notice must stop before the nickname panel");
        assertTrue(target.y + target.height <= 1080f - 6f,
                "the notice must stay inside the viewport");

        com.badlogic.gdx.math.Rectangle clamped = CoronaPokerGdxTable
                .remoteSeatChatNoticeTarget(podX, 1080f, podY,
                        1920f, 1080f, aspect);
        assertEquals(1080f - 6f, clamped.y + clamped.height, 0.001f);
    }

    @Test
    void remoteChatImageSpansTheSeatFromThePodBaseToTheCardCrossing() {
        float aspect = 1242f / 923f;
        Rectangle target = CoronaPokerGdxTable.remoteSeatChatImageTarget(
                900f, 920f, 758f, 1920f, 1080f, aspect);

        assertEquals(900f, target.x, 0.001f);
        assertEquals(758f, target.y, 0.001f);
        assertEquals(286f, target.width, 0.001f);
        assertEquals(920f + CoronaPokerGdxTable.rivalHandUpperCrossingY(aspect),
                target.y + target.height, 0.001f);

        Rectangle clamped = CoronaPokerGdxTable.remoteSeatChatImageTarget(
                1720f, 1080f, 918f, 1920f, 1080f, aspect);
        assertEquals(1914f, clamped.x + clamped.width, 0.001f);
        assertEquals(1074f, clamped.y + clamped.height, 0.001f);
    }

    @Test
    void squareAndWideChatImagesUseTheAvailableHeightOrWidth() {
        Rectangle target = new Rectangle(100f, 200f, 286f, 216f);
        var square = CoronaPokerGdxTable.layoutSeatChatImage(target, 400f, 400f, true);
        var wide = CoronaPokerGdxTable.layoutSeatChatImage(target, 800f, 200f, true);

        assertEquals(new Rectangle(135f, 200f, 216f, 216f), square.bounds());
        assertEquals(0f, square.verticalInset(), 0.001f);
        assertEquals(286f, wide.bounds().width, 0.001f);
        assertEquals(71.5f, wide.bounds().height, 0.001f);
        assertEquals(0f, wide.verticalInset(), 0.001f);
    }

    @Test
    void portraitChatImageSpansTheSeatWidthWithAnUndistortedCenterCrop() {
        Rectangle target = new Rectangle(100f, 200f, 286f, 216f);
        var portrait = CoronaPokerGdxTable.layoutSeatChatImage(target, 200f, 600f, true);

        assertEquals(target.x, portrait.bounds().x, 0.001f);
        assertEquals(target.y, portrait.bounds().y, 0.001f);
        assertEquals(target.width, portrait.bounds().width, 0.001f);
        assertEquals(target.height, portrait.bounds().height, 0.001f);
        float visibleSourceHeight = 600f * (1f - 2f * portrait.verticalInset());
        assertEquals(target.height / target.width,
                visibleSourceHeight / 200f, 0.001f);
    }

    @Test
    void localChatImagesStayInTheCompactAreaAboveTheAvatarBase() {
        Rectangle target = CoronaPokerGdxTable.localSeatChatImageTarget(960f, 200f);
        assertEquals(new Rectangle(814f, 148f, 324f, 166f), target);

        var square = CoronaPokerGdxTable.layoutSeatChatImage(target, 400f, 400f, false);
        assertEquals(new Rectangle(893f, 148f, 166f, 166f), square.bounds());

        var portrait = CoronaPokerGdxTable.layoutSeatChatImage(target, 200f, 600f, false);
        assertEquals(166f, portrait.bounds().height, 0.001f);
        assertEquals(166f / 3f, portrait.bounds().width, 0.001f);
        assertEquals(0f, portrait.verticalInset(), 0.001f);

        var wide = CoronaPokerGdxTable.layoutSeatChatImage(target, 800f, 200f, false);
        assertEquals(324f, wide.bounds().width, 0.001f);
        assertEquals(81f, wide.bounds().height, 0.001f);
        assertEquals(0f, wide.verticalInset(), 0.001f);
    }

    @Test
    void audioSeatIndicatorKeepsAQuarterSecondIdentificationTail() {
        assertEquals(0.25f,
                CoronaPokerGdxTable.SEAT_CHAT_AUDIO_TAIL_SECONDS);
        assertEquals(0.25f, CoronaPokerGdxTable.seatChatFadeSeconds(true));
        assertEquals(0.35f, CoronaPokerGdxTable.seatChatFadeSeconds(false));
    }

    @Test
    void voiceSeatIconFollowsSwingPlaybackGuards() {
        assertTrue(CoronaPokerGdxTable.shouldShowVoiceSeatNotice(
                true, false, false, true));
        assertTrue(CoronaPokerGdxTable.shouldShowVoiceSeatNotice(
                true, false, true, true));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowVoiceSeatNotice(
                        false, false, false, true));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowVoiceSeatNotice(
                        true, true, false, true));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowVoiceSeatNotice(
                        true, false, true, false));
    }

    @Test
    void seatNoticesFollowTheirMediaSettingAndSenderBlock() {
        assertTrue(CoronaPokerGdxTable.shouldShowSeatNotice(
                LobbyChatMessage.Type.IMAGE, true, false, false));
        assertFalse(CoronaPokerGdxTable.shouldShowSeatNotice(
                LobbyChatMessage.Type.IMAGE, false, true, false));
        assertTrue(CoronaPokerGdxTable.shouldShowSeatNotice(
                LobbyChatMessage.Type.VOICE, false, true, false));
        assertFalse(CoronaPokerGdxTable.shouldShowSeatNotice(
                LobbyChatMessage.Type.VOICE, true, false, false));
        assertFalse(CoronaPokerGdxTable.shouldShowSeatNotice(
                LobbyChatMessage.Type.IMAGE, true, true, true));
        assertFalse(CoronaPokerGdxTable.shouldShowSeatNotice(
                LobbyChatMessage.Type.VOICE, true, true, true));
        assertFalse(CoronaPokerGdxTable.shouldShowSeatNotice(
                LobbyChatMessage.Type.TEXT, true, true, false));
        assertFalse(CoronaPokerGdxTable.shouldShowSeatNotice(
                LobbyChatMessage.Type.PLAYER_JOINED, true, true, false));
    }

    private static LobbySession lobby() {
        return new LobbySession(snapshot(List.of(message(0, "antes"))),
                command -> CompletableFuture.completedFuture(null));
    }

    private static LobbySnapshot snapshot(List<LobbyChatMessage> messages) {
        return new LobbySnapshot("server", "server", "localhost:2345", true,
                LobbySnapshot.Phase.IN_GAME, "",
                List.of(new LobbyParticipant("server", null, true, true,
                        false, true, false, true,
                        LobbyParticipant.NO_LATENCY,
                        LobbyParticipant.NO_LATENCY)),
                messages, null, false);
    }

    private static LobbyChatMessage message(long sequence, String text) {
        return new LobbyChatMessage(sequence, Instant.EPOCH, "server",
                LobbyChatMessage.Type.TEXT, text);
    }
}

package com.tonikelope.coronapoker.gdx;

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

        assertEquals("Ana: [IMAGEN]", CoronaPokerGdxTable
                .quickChatHistoryText(image, new GdxGameText("es")));
        assertEquals("Luis: [NOTA DE VOZ]", CoronaPokerGdxTable
                .quickChatHistoryText(voice, new GdxGameText("es")));
        assertEquals("Ana: [IMAGE]", CoronaPokerGdxTable
                .quickChatHistoryText(image, new GdxGameText("en")));
        assertEquals("Luis: [VOICE MESSAGE]", CoronaPokerGdxTable
                .quickChatHistoryText(voice, new GdxGameText("en")));
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
    void textNoticeUsesTheSwingThreeSecondMinimumAndVoiceTracksPlayback() {
        assertEquals(3f, CoronaPokerGdxTable.seatChatNoticeDuration(
                LobbyChatMessage.Type.TEXT, "#12#"));
        assertEquals(4f, CoronaPokerGdxTable.seatChatNoticeDuration(
                LobbyChatMessage.Type.TEXT, "x".repeat(76)));
        assertEquals(3f, CoronaPokerGdxTable.seatChatNoticeDuration(
                LobbyChatMessage.Type.TEXT, "#12# ".repeat(30)));
        assertEquals(60f, CoronaPokerGdxTable.seatChatNoticeDuration(
                LobbyChatMessage.Type.VOICE, "UklGRg=="));
    }

    @Test
    void textIndicatorNeverInheritsTheLongAudioWatchdog() {
        assertEquals(3f, CoronaPokerGdxTable.seatChatPlaybackWatchdog(
                LobbyChatMessage.Type.TEXT, "hola"));
        assertEquals(4f, CoronaPokerGdxTable.seatChatPlaybackWatchdog(
                LobbyChatMessage.Type.TEXT, "x".repeat(76)));
        assertEquals(16f, CoronaPokerGdxTable.seatChatPlaybackWatchdog(
                LobbyChatMessage.Type.VOICE, "UklGRg=="));
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
    void textSeatIconOnlyExistsWhileTtsActuallyPlays() {
        assertTrue(CoronaPokerGdxTable.shouldDisplaySeatNotice(
                LobbyChatMessage.Type.TEXT, true, true));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldDisplaySeatNotice(
                        LobbyChatMessage.Type.TEXT, true, false));
        assertTrue(CoronaPokerGdxTable.shouldDisplaySeatNotice(
                LobbyChatMessage.Type.VOICE, true, false));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldDisplaySeatNotice(
                        LobbyChatMessage.Type.TEXT, false, true));
    }

    @Test
    void mutedOrBlockedTextUsesSilentNoticeInsteadOfTalkIcon() {
        assertTrue(CoronaPokerGdxTable.shouldShowSilentTextNotice(
                LobbyChatMessage.Type.TEXT, true, false));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowSilentTextNotice(
                        LobbyChatMessage.Type.TEXT, true, true));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowSilentTextNotice(
                        LobbyChatMessage.Type.VOICE, true, false));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowSilentTextNotice(
                        LobbyChatMessage.Type.TEXT, false, false));
    }

    @Test
    void seatNoticesRespectTheSwingChatImagePreference() {
        assertTrue(CoronaPokerGdxTable.shouldShowSeatNotice(
                LobbyChatMessage.Type.TEXT, true, false, false, true,
                false));
        assertTrue(CoronaPokerGdxTable.shouldShowSeatNotice(
                LobbyChatMessage.Type.IMAGE, true, true, false, false,
                false));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowSeatNotice(
                        LobbyChatMessage.Type.IMAGE, true, false, false,
                        false, false));
        assertTrue(CoronaPokerGdxTable.shouldShowSeatNotice(
                LobbyChatMessage.Type.VOICE, true, false, true, false,
                false));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowSeatNotice(
                        LobbyChatMessage.Type.VOICE, true, true, false,
                        false, false));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowSeatNotice(
                        LobbyChatMessage.Type.TEXT, false, true, true,
                        false, false));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowSeatNotice(
                        LobbyChatMessage.Type.PLAYER_JOINED,
                        true, true, true, false, false));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowSeatNotice(
                        LobbyChatMessage.Type.IMAGE,
                        true, true, true, true, false));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowSeatNotice(
                        LobbyChatMessage.Type.VOICE,
                        true, true, true, true, false));
        assertTrue(CoronaPokerGdxTable.shouldShowSeatNotice(
                LobbyChatMessage.Type.IMAGE,
                false, true, false, false, true),
                "Swing always shows the sender's own image as confirmation");
        assertTrue(CoronaPokerGdxTable.shouldShowSeatNotice(
                LobbyChatMessage.Type.VOICE,
                false, true, true, false, true),
                "Playing an own voice note is independent from remote chat notifications");
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowSeatNotice(
                        LobbyChatMessage.Type.IMAGE,
                        false, true, false, false, false));
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowSeatNotice(
                        LobbyChatMessage.Type.VOICE,
                        false, true, true, false, false));
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
                messages, null, false, true);
    }

    private static LobbyChatMessage message(long sequence, String text) {
        return new LobbyChatMessage(sequence, Instant.EPOCH, "server",
                LobbyChatMessage.Type.TEXT, text);
    }
}

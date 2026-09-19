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

        assertEquals("Ana: [IMAGEN]",
                CoronaPokerGdxTable.quickChatHistoryText(image));
        assertEquals("Luis: [NOTA DE VOZ]",
                CoronaPokerGdxTable.quickChatHistoryText(voice));
    }

    @Test
    void localMediaConfirmationUsesHalfCardHeightLikeSwing() {
        assertEquals(90f,
                CoronaPokerGdxTable.seatChatNoticeMaxHeight(true, 180f));
        assertEquals(180f,
                CoronaPokerGdxTable.seatChatNoticeMaxHeight(false, 180f));
    }

    @Test
    void textNoticeUsesTheSwingThreeSecondMinimumAndVoiceTracksPlayback() {
        assertEquals(3f, CoronaPokerGdxTable.seatChatNoticeDuration(
                LobbyChatMessage.Type.TEXT, "#12#"));
        assertEquals(4f, CoronaPokerGdxTable.seatChatNoticeDuration(
                LobbyChatMessage.Type.TEXT, "x".repeat(76)));
        assertEquals(60f, CoronaPokerGdxTable.seatChatNoticeDuration(
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
        org.junit.jupiter.api.Assertions.assertFalse(
                CoronaPokerGdxTable.shouldShowSeatNotice(
                        LobbyChatMessage.Type.IMAGE,
                        false, true, false, false, false));
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

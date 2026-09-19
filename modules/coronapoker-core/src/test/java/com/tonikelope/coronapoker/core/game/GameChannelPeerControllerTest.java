package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.network.ConfirmationTracker;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class GameChannelPeerControllerTest {

    @Test
    void stripsCanonicalEnvelopeAndConfirmsOnlyAfterDeliveryAck() {
        RecordingChannel channel = new RecordingChannel();
        ConfirmationTracker confirmations = new ConfirmationTracker();
        ConfirmationTracker.Request request = confirmations.register(42,
                List.of("client"));
        byte[] key = {1, 2, 3};
        GameChannelPeerController peer = new GameChannelPeerController(
                "client", channel, confirmations, key, 17, 21);

        assertFalse(peer.writeGameCommandFromServer(
                "GAME#41#ACTION#payload", new byte[16]));
        assertEquals("client", channel.nickname);
        assertEquals("ACTION#payload", channel.command);
        assertFalse(confirmations.isComplete(request));

        channel.delivery.complete(null);
        assertTrue(confirmations.isComplete(request));
        key[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, peer.getIdentity_pubkey());
    }

    @Test
    void malformedEnvelopeFailsClosedWithoutWriting() {
        RecordingChannel channel = new RecordingChannel();
        GameChannelPeerController peer = new GameChannelPeerController(
                "client", channel, new ConfirmationTracker(),
                new byte[]{1}, 0, 0);

        assertTrue(peer.writeGameCommandFromServer("ACTION#payload",
                new byte[16]));
        assertTrue(peer.isExit());
        assertEquals(null, channel.command);
    }

    @Test
    void failedAuthenticatedDeliveryMarksPeerExited() {
        RecordingChannel channel = new RecordingChannel();
        GameChannelPeerController peer = new GameChannelPeerController(
                "client", channel, new ConfirmationTracker(),
                new byte[]{1}, 0, 0);

        assertFalse(peer.writeGameCommandFromServer("GAME#7#PING",
                new byte[16]));
        channel.delivery.completeExceptionally(new IOException("closed"));
        assertTrue(peer.isExit());
    }

    private static final class RecordingChannel implements GameChannel {
        private final CompletableFuture<Void> delivery = new CompletableFuture<>();
        private String nickname;
        private String command;

        @Override public AutoCloseable subscribe(Consumer<Inbound> listener) {
            return () -> { };
        }
        @Override public CompletionStage<Void> sendToHost(String value) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<Void> broadcastFromHost(String value,
                String skipNickname) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<Void> sendFromHost(String target,
                String value) {
            nickname = target;
            command = value;
            return delivery;
        }
        @Override public void close() { }
    }
}

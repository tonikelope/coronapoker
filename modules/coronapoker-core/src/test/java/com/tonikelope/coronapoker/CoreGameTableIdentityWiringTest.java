/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.game.GameChannel;
import com.tonikelope.coronapoker.core.game.GameChannelPeerController;
import com.tonikelope.coronapoker.core.game.GamePeerController;
import com.tonikelope.coronapoker.core.network.ConfirmationTracker;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class CoreGameTableIdentityWiringTest {

    @Test
    void carriesLobbyIdentityAndLatencyIntoAuthenticatedGamePeer() {
        byte[] remoteIdentity = {11, 22, 33, 44};
        LobbyParticipant local = new LobbyParticipant("host", null, true,
                true, false, true, false, true, 3, 4,
                new byte[]{1}, null);
        LobbyParticipant remote = new LobbyParticipant("guest", null, false,
                false, false, true, false, true, 37, 42,
                remoteIdentity, new byte[32]);
        LobbySnapshot lobby = new LobbySnapshot("host", "host",
                "localhost:2345", true,
                LobbySnapshot.Phase.WAITING_FOR_PLAYERS, "",
                List.of(local, remote), List.of(), null, false, true);

        Map<String, GamePeerController> peers =
                CoreGameTableFactory.createPeers(lobby,
                        new NoOpGameChannel(), new ConfirmationTracker());

        assertNull(peers.get("host"));
        GameChannelPeerController gamePeer = assertInstanceOf(
                GameChannelPeerController.class, peers.get("guest"));
        assertArrayEquals(remoteIdentity, gamePeer.getIdentity_pubkey());
        assertEquals(37, gamePeer.getLatency());
        assertEquals(42, gamePeer.getLatency2());

        remoteIdentity[0] = 99;
        assertArrayEquals(new byte[]{11, 22, 33, 44},
                gamePeer.getIdentity_pubkey());
    }

    private static final class NoOpGameChannel implements GameChannel {

        @Override
        public AutoCloseable subscribe(Consumer<Inbound> listener) {
            return () -> { };
        }

        @Override
        public CompletionStage<Void> sendToHost(String command) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> broadcastFromHost(String command,
                String skipNickname) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> sendFromHost(String nickname,
                String command) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
        }
    }
}

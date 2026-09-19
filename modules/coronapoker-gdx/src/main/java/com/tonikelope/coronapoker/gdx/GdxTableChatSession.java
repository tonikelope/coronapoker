package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.LobbyChatMessage;
import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Table-facing projection of the existing lobby chat channel. It adds no
 * transport or protocol: both frontends continue to use the ordered
 * {@link LobbySession} owned by the game session.
 */
final class GdxTableChatSession implements AutoCloseable {

    private final LobbySession lobby;
    private final AtomicReference<LobbySnapshot> snapshot;
    private final ConcurrentLinkedQueue<LobbyChatMessage> incoming =
            new ConcurrentLinkedQueue<>();
    private final AutoCloseable subscription;
    private volatile long deliveredSequence;

    GdxTableChatSession(LobbySession lobby) {
        this.lobby = Objects.requireNonNull(lobby, "lobby");
        LobbySnapshot initial = lobby.snapshot();
        snapshot = new AtomicReference<>(initial);
        deliveredSequence = maximumSequence(initial.chat());
        subscription = lobby.subscribe(this::accept);
    }

    LobbySnapshot snapshot() {
        return snapshot.get();
    }

    List<LobbyChatMessage> history() {
        return snapshot.get().chat();
    }

    List<LobbyChatMessage> drainIncoming() {
        ArrayList<LobbyChatMessage> result = new ArrayList<>();
        for (LobbyChatMessage message; (message = incoming.poll()) != null;) {
            result.add(message);
        }
        return List.copyOf(result);
    }

    CompletionStage<Void> sendText(String text) {
        return lobby.submit(new LobbyCommand.SendText(text));
    }

    CompletionStage<Void> sendImage(String url) {
        return lobby.submit(new LobbyCommand.SendImage(url));
    }

    CompletionStage<Void> sendVoice(byte[] wav) {
        return lobby.submit(new LobbyCommand.SendVoice(wav));
    }

    private synchronized void accept(LobbySnapshot next) {
        snapshot.set(next);
        for (LobbyChatMessage message : next.chat()) {
            if (message.sequence() > deliveredSequence) {
                incoming.add(message);
                deliveredSequence = message.sequence();
            }
        }
    }

    private static long maximumSequence(List<LobbyChatMessage> messages) {
        long result = -1L;
        for (LobbyChatMessage message : messages) {
            result = Math.max(result, message.sequence());
        }
        return result;
    }

    @Override
    public void close() {
        try {
            subscription.close();
        } catch (Exception ignored) {
            // Removing an in-process listener is best effort during teardown.
        }
        incoming.clear();
    }
}

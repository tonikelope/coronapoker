package com.tonikelope.coronapoker.core;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Thread-safe shared waiting-room state and command boundary. */
public final class LobbySession implements AutoCloseable {

    private final AtomicReference<LobbySnapshot> snapshot;
    private final LobbyCommandSink commands;
    private final CopyOnWriteArrayList<Consumer<LobbySnapshot>> listeners
            = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public LobbySession(LobbySnapshot initialSnapshot, LobbyCommandSink commands) {
        snapshot = new AtomicReference<>(Objects.requireNonNull(initialSnapshot,
                "initialSnapshot"));
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    public LobbySnapshot snapshot() {
        return snapshot.get();
    }

    /** Called by the network adapter after it has validated and ordered input. */
    public void publish(LobbySnapshot next) {
        ensureOpen();
        Objects.requireNonNull(next, "next");
        LobbySnapshot previous = snapshot.get();
        if (!previous.localNickname().equals(next.localNickname())
                || previous.host() != next.host()
                || !previous.serverAddress().equals(next.serverAddress())) {
            throw new IllegalArgumentException("A lobby snapshot cannot change session identity");
        }
        snapshot.set(next);
        listeners.forEach(listener -> listener.accept(next));
    }

    /** Subscribes and immediately supplies the current immutable snapshot. */
    public AutoCloseable subscribe(Consumer<LobbySnapshot> listener) {
        ensureOpen();
        Consumer<LobbySnapshot> checked = Objects.requireNonNull(listener, "listener");
        listeners.add(checked);
        checked.accept(snapshot.get());
        return () -> listeners.remove(checked);
    }

    public CompletionStage<Void> submit(LobbyCommand command) {
        ensureOpen();
        LobbyCommand checked = Objects.requireNonNull(command, "command");
        validate(checked, snapshot.get());
        return Objects.requireNonNull(commands.submit(checked), "command result");
    }

    private static void validate(LobbyCommand command, LobbySnapshot state) {
        boolean hostOnly = command instanceof LobbyCommand.AddBot
                || command instanceof LobbyCommand.Kick
                || command instanceof LobbyCommand.StartGame
                || command instanceof LobbyCommand.ChangePassword;
        if (hostOnly && !state.host()) {
            throw new IllegalStateException("Only the host can execute this lobby command");
        }
        if (state.startingOrStarted() && hostOnly) {
            throw new IllegalStateException("Lobby controls are locked while the game starts");
        }
        if (command instanceof LobbyCommand.AddBot
                && state.participants().size() >= LobbySnapshot.MAX_PARTICIPANTS) {
            throw new IllegalStateException("The lobby is full");
        }
        if (command instanceof LobbyCommand.StartGame
                && state.participants().size() < 2) {
            throw new IllegalStateException("At least two participants are required");
        }
        if (command instanceof LobbyCommand.Kick kick) {
            LobbyParticipant target = state.participants().stream()
                    .filter(participant -> participant.nickname().equals(kick.nickname()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown participant"));
            if (target.local() || target.host()) {
                throw new IllegalArgumentException("The host cannot kick the local host row");
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Lobby session is closed");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            listeners.clear();
        }
    }
}

/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.concurrent.atomic.AtomicLong;

/** Renderer-neutral ownership of one running poker game. */
public final class GameSession implements AutoCloseable {

    public enum Phase { CREATED, RUNNING, FINISHED, CLOSED }

    private final String localNickname;
    private final boolean host;
    private final TableState table;
    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.CREATED);
    private final AtomicReference<GameConfigCodecV1.Configuration> configuration
            = new AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicBoolean recovering
            = new java.util.concurrent.atomic.AtomicBoolean();
    private final AtomicLong playTimeSeconds = new AtomicLong();

    public GameSession(String localNickname, boolean host) {
        String normalized = Objects.requireNonNull(localNickname, "localNickname").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("localNickname is required");
        }
        this.localNickname = normalized;
        this.host = host;
        this.table = new TableState(normalized);
    }

    public GameSession(String localNickname, boolean host,
            GameConfigCodecV1.Configuration configuration) {
        this(localNickname, host);
        GameConfigCodecV1.Configuration valid
                = GameConfigCodecV1.requireValid(configuration);
        this.configuration.set(valid);
        this.recovering.set(valid.recover());
    }

    public String localNickname() { return localNickname; }
    public boolean isHost() { return host; }
    public TableState table() { return table; }
    public Phase phase() { return phase.get(); }
    public boolean isPaused() { return table.paused(); }
    public long playTimeSeconds() { return playTimeSeconds.get(); }

    public GameConfigCodecV1.Configuration configuration() {
        GameConfigCodecV1.Configuration current = configuration.get();
        if (current == null) {
            throw new IllegalStateException("Game session has no validated configuration");
        }
        return current;
    }

    public boolean hasConfiguration() {
        return configuration.get() != null;
    }

    public boolean isRecovering() {
        return recovering.get();
    }

    public void setRecovering(boolean value) {
        if (phase.get() == Phase.CLOSED) {
            throw new IllegalStateException("Game session is closed");
        }
        recovering.set(value);
    }

    public void updateConfiguration(GameConfigCodecV1.Configuration next) {
        if (phase.get() == Phase.CLOSED) {
            throw new IllegalStateException("Game session is closed");
        }
        GameConfigCodecV1.Configuration valid = GameConfigCodecV1.requireValid(next);
        GameConfigCodecV1.Configuration previous = configuration.getAndSet(valid);
        if (previous == null) {
            recovering.set(valid.recover());
        }
    }

    public void mutateConfiguration(UnaryOperator<GameConfigCodecV1.Configuration> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (phase.get() == Phase.CLOSED) {
            throw new IllegalStateException("Game session is closed");
        }
        configuration.updateAndGet(current -> {
            if (current == null) {
                throw new IllegalStateException("Game session has no validated configuration");
            }
            return GameConfigCodecV1.requireValid(mutation.apply(current));
        });
    }

    public void setIwtsth(boolean value) {
        mutateConfiguration(current -> current.withIwtsth(value));
    }

    public void setRunItTwice(boolean value) {
        mutateConfiguration(current -> current.withRunItTwice(value));
    }

    public void setRabbitHunting(int value) {
        mutateConfiguration(current -> current.withRabbitHunting(value));
    }

    public void setBotRebuy(boolean value) {
        mutateConfiguration(current -> current.withBotRebuy(value));
    }

    public void setBotBalanceToHumans(boolean value) {
        mutateConfiguration(current -> current.withBotBalanceToHumans(value));
    }

    public void applyBlindUpdate(GameConfigCodecV1.Configuration update) {
        GameConfigCodecV1.requireValid(update);
        mutateConfiguration(current -> current.withBlindUpdate(update));
    }

    public void setHands(int value) {
        mutateConfiguration(current -> current.withHands(value));
    }

    public void applyRecoveredBuyin(int buyin, boolean rebuy) {
        mutateConfiguration(current -> current.withRecoveredBuyin(buyin, rebuy));
    }

    public void setPlayTimeSeconds(long seconds) {
        if (seconds < 0L) throw new IllegalArgumentException("Play time cannot be negative");
        playTimeSeconds.set(seconds);
    }

    public long incrementPlayTimeSecond() {
        return playTimeSeconds.incrementAndGet();
    }

    public void start() {
        if (!phase.compareAndSet(Phase.CREATED, Phase.RUNNING)) {
            throw new IllegalStateException("Game session cannot start from " + phase.get());
        }
    }

    public void finish() {
        Phase current;
        do {
            current = phase.get();
            if (current == Phase.FINISHED || current == Phase.CLOSED) return;
        } while (!phase.compareAndSet(current, Phase.FINISHED));
        table.setFinished(true);
    }

    public void setPaused(boolean paused) {
        if (phase.get() == Phase.CLOSED) {
            throw new IllegalStateException("Game session is closed");
        }
        table.setPaused(paused);
    }

    @Override
    public void close() {
        phase.set(Phase.CLOSED);
        table.setFinished(true);
    }
}

/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.table;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renderer-neutral ownership handoff from a lobby to one canonical table.
 * The engine is not started until the renderer has opened its initial state,
 * so the first real event can never race the GDX scene transition.
 */
public final class TableSession implements AutoCloseable {

    @FunctionalInterface
    public interface Starter {
        CompletionStage<Void> start();
    }

    private final TableSnapshot initialState;
    private final TableCommandSink commands;
    private final TableEventBridge events;
    private final Starter starter;
    private final AutoCloseable resource;
    private final AtomicBoolean attached = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public TableSession(TableSnapshot initialState, TableCommandSink commands,
            TableEventBridge events, Starter starter) {
        this(initialState, commands, events, starter, () -> { });
    }

    public TableSession(TableSnapshot initialState, TableCommandSink commands,
            TableEventBridge events, Starter starter, AutoCloseable resource) {
        this.initialState = Objects.requireNonNull(initialState, "initialState");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.events = Objects.requireNonNull(events, "events");
        this.starter = Objects.requireNonNull(starter, "starter");
        this.resource = Objects.requireNonNull(resource, "resource");
    }

    public TableSnapshot initialState() {
        return initialState;
    }

    public TableCommandSink commands() {
        return commands;
    }

    public TableEventBridge events() {
        return events;
    }

    public CompletionStage<Void> attach(TableRenderer renderer) {
        Objects.requireNonNull(renderer, "renderer");
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Table session is closed"));
        }
        if (!attached.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Table session already has a renderer"));
        }
        CompletionStage<Void> opening;
        try {
            opening = events.attach(renderer, initialState);
        } catch (Throwable failure) {
            attached.set(false);
            return CompletableFuture.failedFuture(failure);
        }
        return opening.thenCompose(ignored -> {
            if (closed.get()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Table session closed while opening"));
            }
            try {
                return Objects.requireNonNull(starter.start(), "table start result");
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }).whenComplete((ignored, failure) -> {
            if (failure != null) close();
        });
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        events.close();
        try {
            resource.close();
        } catch (Exception ignored) {
            // The table is closed even if its transport/controller cleanup failed.
        }
    }
}

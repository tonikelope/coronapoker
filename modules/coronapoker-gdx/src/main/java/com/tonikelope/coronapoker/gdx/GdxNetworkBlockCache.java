/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.NetworkBlock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Low-frequency immutable view of host-side connection quarantines.
 *
 * <p>The authoritative guard prunes and sorts under a lock.  Rendering only
 * needs eventual UI visibility, so rebuilding that view on every frame wastes
 * both the render thread and the network guard.  Explicit mutations refresh
 * immediately; passive expiry/hotplug-style changes are observed within one
 * second.</p>
 */
final class GdxNetworkBlockCache {

    static final float REFRESH_SECONDS = 1f;
    private static final ExecutorService REFRESH_EXECUTOR =
            Executors.newSingleThreadExecutor(task -> {
                Thread worker = new Thread(task,
                        "CoronaPoker-GDX-network-blocks");
                worker.setDaemon(true);
                return worker;
            });

    private final Supplier<List<NetworkBlock>> source;
    private final Executor executor;
    private List<NetworkBlock> snapshot = List.of();
    private CompletableFuture<RefreshResult> pending;
    private float elapsed = REFRESH_SECONDS;
    private boolean initialized;
    private boolean refreshAfterPending;

    GdxNetworkBlockCache(Supplier<List<NetworkBlock>> source) {
        this(source, REFRESH_EXECUTOR);
    }

    GdxNetworkBlockCache(Supplier<List<NetworkBlock>> source,
            Executor executor) {
        this.source = Objects.requireNonNull(source, "source");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    void update(float deltaSeconds, boolean enabled) {
        if (!enabled) {
            clear();
            return;
        }
        consumeCompleted();
        elapsed += Math.max(0f, deltaSeconds);
        if (pending == null
                && (!initialized || elapsed >= REFRESH_SECONDS)) {
            scheduleRefresh();
            consumeCompleted();
        }
    }

    void refreshNow(boolean enabled) {
        if (!enabled) {
            clear();
            return;
        }
        elapsed = 0f;
        if (pending == null) {
            scheduleRefresh();
            consumeCompleted();
        } else {
            refreshAfterPending = true;
        }
    }

    List<NetworkBlock> current() {
        return snapshot;
    }

    void removeLocally(String address) {
        Objects.requireNonNull(address, "address");
        snapshot = snapshot.stream()
                .filter(block -> !address.equals(block.address()))
                .toList();
    }

    void clear() {
        if (pending != null) pending.cancel(false);
        pending = null;
        initialized = false;
        refreshAfterPending = false;
        elapsed = REFRESH_SECONDS;
        snapshot = List.of();
    }

    private void scheduleRefresh() {
        elapsed = 0f;
        pending = CompletableFuture.supplyAsync(this::loadSnapshot, executor);
    }

    private RefreshResult loadSnapshot() {
        try {
            List<NetworkBlock> current = source.get();
            return new RefreshResult(current == null
                    ? List.of() : List.copyOf(current), true);
        } catch (RuntimeException unavailable) {
            return new RefreshResult(List.of(), false);
        }
    }

    private void consumeCompleted() {
        CompletableFuture<RefreshResult> completed = pending;
        if (completed == null || !completed.isDone()) return;
        pending = null;
        if (refreshAfterPending) {
            refreshAfterPending = false;
            scheduleRefresh();
            return;
        }
        RefreshResult result = completed.join();
        if (result.success()) snapshot = result.blocks();
        initialized = true;
    }

    private record RefreshResult(List<NetworkBlock> blocks,
            boolean success) { }
}

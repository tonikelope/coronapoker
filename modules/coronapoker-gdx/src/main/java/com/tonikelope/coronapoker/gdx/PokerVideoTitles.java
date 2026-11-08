/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Resolves missing metadata for the current video without blocking rendering or playback. */
final class PokerVideoTitles implements AutoCloseable {
    private static final int CACHE_LIMIT = 128;
    private static final long RETRY_DELAY = TimeUnit.SECONDS.toNanos(30);
    private final LinkedHashMap<String, Title> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "CoronaPoker-video-title");
        thread.setDaemon(true);
        return thread;
    });
    private String pendingId;
    private Future<String> pending;

    String title(PokerVideoSearch.Video video) {
        Title cached = video.hasTitle() ? null : cache.get(video.id());
        return cached != null && cached.text() != null ? cached.text() : video.title();
    }

    void update(PokerVideoSearch.Video video) {
        if (pending != null && pending.isDone()) {
            try {
                cache.put(pendingId, new Title(pending.get(), 0L));
            } catch (ExecutionException failure) {
                cache.put(pendingId, new Title(null, System.nanoTime() + RETRY_DELAY));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                pending = null;
                pendingId = null;
            }
            if (cache.size() > CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
        }
        String id = video == null || video.hasTitle() ? null : video.id();
        if (Objects.equals(id, pendingId)) return;
        cancel();
        if (id == null) return;
        Title cached = cache.get(id);
        if (cached != null && (cached.text() != null
                || System.nanoTime() - cached.retryAt() < 0L)) return;
        pendingId = id;
        pending = worker.submit(() -> PokerVideoSearch.fetchTitle(video));
    }

    private void cancel() {
        if (pending != null) pending.cancel(true);
        pending = null;
        pendingId = null;
    }

    @Override
    public void close() {
        cancel();
        worker.shutdownNow();
    }

    private record Title(String text, long retryAt) { }
}

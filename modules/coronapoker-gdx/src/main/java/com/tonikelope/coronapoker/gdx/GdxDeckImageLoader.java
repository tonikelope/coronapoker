/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Decodes one outstanding card at a time. Ownership of each Pixmap is handed back to GDX. */
final class GdxDeckImageLoader implements AutoCloseable {

    record Card(String deck, String filename) {}
    record Image(Card card, FileHandle file, Pixmap pixels) {}

    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "coronapoker-deck-images");
        thread.setDaemon(true);
        return thread;
    });
    private final BiFunction<String, String, FileHandle> source;
    private CompletableFuture<Image> decoding;
    private Card pending;

    GdxDeckImageLoader(BiFunction<String, String, FileHandle> source) {
        this.source = source;
    }

    void update(Set<Card> wanted, Consumer<Image> upload, Consumer<Card> failure) {
        if (decoding != null && !decoding.isDone()) return;
        if (decoding != null) {
            boolean relevant = wanted.remove(pending);
            if (decoding.isCompletedExceptionally()) {
                failure.accept(pending);
            } else {
                Image image = decoding.join();
                if (relevant) upload.accept(image);
                else image.pixels().dispose();
            }
            decoding = null;
            pending = null;
        }
        if (wanted.isEmpty()) return;
        Card next = wanted.iterator().next();
        pending = next;
        decoding = CompletableFuture.supplyAsync(() -> {
            FileHandle file = source.apply(next.deck(), next.filename());
            return new Image(next, file, new Pixmap(file));
        }, worker);
    }

    @Override
    public void close() {
        if (decoding != null) {
            decoding.thenAccept(image -> image.pixels().dispose());
            decoding = null;
        }
        pending = null;
        worker.shutdown();
    }
}

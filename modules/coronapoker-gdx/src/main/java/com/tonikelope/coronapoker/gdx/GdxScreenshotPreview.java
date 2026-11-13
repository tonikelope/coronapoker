package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Prepares only the latest requested capture and uploads it on the render thread. */
final class GdxScreenshotPreview implements AutoCloseable {

    private static final Executor DECODER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "coronapoker-screenshot-preview");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private Path requested;
    private Path decoding;
    private CompletableFuture<Pixmap> pending;

    void request(Path file) {
        requested = file;
    }

    boolean loading() {
        return requested != null;
    }

    Texture poll() {
        if (pending != null && pending.isDone()) {
            var result = pending;
            Path finished = decoding;
            pending = null;
            decoding = null;
            if (!finished.equals(requested)) {
                result.thenAccept(Pixmap::dispose);
            } else {
                requested = null;
                Pixmap pixels = result.join();
                try {
                    Texture texture = new Texture(pixels, true);
                    texture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
                    return texture;
                } finally {
                    pixels.dispose();
                }
            }
        }
        if (requested != null && pending == null) {
            decoding = requested;
            GdxImageClipboard.prepare(decoding);
            FileHandle source = new FileHandle(decoding.toFile());
            pending = CompletableFuture.supplyAsync(() -> new Pixmap(source), DECODER);
        }
        return null;
    }

    @Override
    public void close() {
        requested = null;
        decoding = null;
        if (pending != null) {
            pending.thenAccept(Pixmap::dispose);
            pending = null;
        }
    }
}

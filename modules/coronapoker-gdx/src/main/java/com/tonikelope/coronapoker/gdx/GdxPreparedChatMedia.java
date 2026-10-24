/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.utils.Disposable;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CPU-side chat media prepared away from the render thread.
 *
 * <p>The render thread only uploads a ready static pixmap or starts the
 * already-inspected streaming GIF. Ownership transfers exactly once.</p>
 */
final class GdxPreparedChatMedia implements Disposable {

    private static final AtomicInteger WORKER_SEQUENCE = new AtomicInteger();
    private static final ExecutorService PREPARATION_EXECUTOR =
            Executors.newFixedThreadPool(2, task -> {
                Thread worker = new Thread(task,
                        "coronapoker-chat-media-"
                                + WORKER_SEQUENCE.incrementAndGet());
                worker.setDaemon(true);
                worker.setPriority(Thread.NORM_PRIORITY - 1);
                return worker;
            });

    private Pixmap image;
    private StreamingGifTextureAnimation.PreparedLooping gif;

    private GdxPreparedChatMedia(Pixmap image,
            StreamingGifTextureAnimation.PreparedLooping gif) {
        this.image = image;
        this.gif = gif;
    }

    static GdxPreparedChatMedia prepare(byte[] data, String label,
            int gifMaxWidth) throws IOException {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(label, "label");
        if (data.length == 0) throw new IOException("Empty chat image");
        if (GdxChatImageLoader.isGif(data)) {
            return new GdxPreparedChatMedia(null,
                    StreamingGifTextureAnimation.prepareLooping(data, label,
                            gifMaxWidth));
        }
        return new GdxPreparedChatMedia(new Pixmap(data, 0, data.length),
                null);
    }

    static CompletableFuture<GdxPreparedChatMedia> prepareAsync(String url,
            String label, int gifMaxWidth) {
        return CompletableFuture.supplyAsync(() -> {
            byte[] data = GdxChatImageLoader.download(url);
            try {
                return prepare(data, label, gifMaxWidth);
            } catch (IOException | RuntimeException invalid) {
                throw new CompletionException(invalid);
            }
        }, PREPARATION_EXECUTOR);
    }

    boolean animated() {
        return gif != null;
    }

    Texture uploadImage() {
        if (image == null) {
            throw new IllegalStateException("No prepared static image");
        }
        Pixmap ready = image;
        image = null;
        try {
            Texture texture = new Texture(ready, true);
            texture.setFilter(TextureFilter.MipMapLinearLinear,
                    TextureFilter.Linear);
            return texture;
        } finally {
            ready.dispose();
        }
    }

    StreamingGifTextureAnimation startGif() {
        if (gif == null) {
            throw new IllegalStateException("No prepared GIF");
        }
        StreamingGifTextureAnimation.PreparedLooping ready = gif;
        gif = null;
        return StreamingGifTextureAnimation.loadLoopingMipmapped(ready);
    }

    @Override
    public void dispose() {
        if (image != null) {
            image.dispose();
            image = null;
        }
        gif = null;
    }
}

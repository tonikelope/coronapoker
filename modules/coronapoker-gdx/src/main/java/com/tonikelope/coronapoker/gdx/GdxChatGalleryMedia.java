/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * GPU thumbnail cache shared by the lobby and in-table image galleries.
 * Downloading stays off the render thread; texture creation and disposal stay
 * on it. The gallery owns every texture it exposes.
 */
final class GdxChatGalleryMedia {

    private final Map<String, Entry> entries = new HashMap<>();
    private boolean disposed;

    Entry get(String url) {
        return entries.get(url);
    }

    void refresh(List<String> urls, int limit, String debugPrefix) {
        if (disposed) return;
        Set<String> retained = new HashSet<>(urls);
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Entry> item = iterator.next();
            if (!retained.contains(item.getKey())) {
                item.getValue().dispose();
                iterator.remove();
            }
        }
        for (int index = 0; index < Math.min(limit, urls.size()); index++) {
            load(urls.get(index), debugPrefix);
        }
    }

    void clear() {
        for (Entry entry : entries.values()) entry.dispose();
        entries.clear();
    }

    void dispose() {
        disposed = true;
        clear();
    }

    private void load(String url, String debugPrefix) {
        if (entries.containsKey(url)) return;
        Entry entry = new Entry();
        entries.put(url, entry);
        CompletableFuture.supplyAsync(() -> GdxChatImageLoader.download(url))
                .whenComplete((data, failure) -> Gdx.app.postRunnable(() -> {
                    if (disposed || entries.get(url) != entry) return;
                    entry.loading = false;
                    if (failure != null || data == null || data.length == 0) {
                        entry.failed = true;
                        return;
                    }
                    try {
                        if (GdxChatImageLoader.isGif(data)) {
                            entry.gif = GifTextureAnimation.load(data,
                                    debugPrefix + ":" + url.hashCode(), 320);
                        } else {
                            Pixmap pixmap = new Pixmap(data, 0, data.length);
                            try {
                                entry.image = new Texture(pixmap, true);
                                entry.image.setFilter(
                                        TextureFilter.MipMapLinearLinear,
                                        TextureFilter.Linear);
                            } finally {
                                pixmap.dispose();
                            }
                        }
                    } catch (RuntimeException | IOException invalid) {
                        entry.failed = true;
                    }
                }));
    }

    static final class Entry {

        private boolean loading = true;
        private boolean failed;
        private Texture image;
        private GifTextureAnimation gif;

        Texture frameAt(float elapsed) {
            return gif == null ? image : gif.frameAt(elapsed, true);
        }

        boolean loading() {
            return loading;
        }

        boolean failed() {
            return failed;
        }

        private void dispose() {
            if (image != null) image.dispose();
            if (gif != null) gif.dispose();
            image = null;
            gif = null;
        }
    }
}

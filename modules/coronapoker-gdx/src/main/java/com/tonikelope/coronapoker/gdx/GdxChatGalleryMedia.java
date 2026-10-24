/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        GdxPreparedChatMedia.prepareAsync(url,
                debugPrefix + ":" + url.hashCode(), 320)
                .whenComplete((prepared, failure) -> {
                    if (Gdx.app == null) {
                        if (prepared != null) prepared.dispose();
                        return;
                    }
                    Gdx.app.postRunnable(() -> {
                        if (disposed || entries.get(url) != entry) {
                            if (prepared != null) prepared.dispose();
                            return;
                        }
                        entry.loading = false;
                        if (failure != null || prepared == null) {
                            entry.failed = true;
                            return;
                        }
                        try {
                            if (prepared.animated()) {
                                entry.gif = prepared.startGif();
                            } else {
                                entry.image = prepared.uploadImage();
                            }
                        } catch (RuntimeException invalid) {
                            entry.failed = true;
                        } finally {
                            prepared.dispose();
                        }
                    });
                });
    }

    static final class Entry {

        private boolean loading = true;
        private boolean failed;
        private Texture image;
        private StreamingGifTextureAnimation gif;
        private float gifStartedAt = Float.NaN;

        Texture frameAt(float elapsed) {
            if (gif == null) return image;
            if (gif.failed()) {
                failed = true;
                return null;
            }
            if (Float.isNaN(gifStartedAt)) gifStartedAt = elapsed;
            return gif.frameAt(Math.max(0f, elapsed - gifStartedAt));
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
            gifStartedAt = Float.NaN;
        }
    }
}

package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Owns the selected screenshot and its immediate neighbors for an open viewer. */
final class GdxScreenshotPreview implements AutoCloseable {

    private static final Rectangle IMAGE_AREA = CoronaPokerGdxTable.screenshotViewerImageArea(0f, 0f);
    private static final float MIN_UNSCALED_RATIO = 0.8f;
    private static final double MAX_PREFETCH_PIXELS = 32_000_000d;
    private static final Executor DECODER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "coronapoker-screenshot-preview");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    record Image(Texture texture, int originalWidth, int originalHeight) {
    }

    private record Decoded(Pixmap pixels, int originalWidth, int originalHeight) {
        void dispose() {
            pixels.dispose();
        }
    }

    private record DecodeJob(Path file, float density, CompletableFuture<Decoded> result) {
    }

    private final Map<Path, Image> textures = new HashMap<>();
    private final Set<Path> failed = new HashSet<>();
    private List<Path> wanted = List.of();
    private Path selected;
    private float density;
    private DecodeJob job;

    Image request(List<GdxScreenshotStore.Shot> shots, int index) {
        Path previous = index > 0 ? shots.get(index - 1).file() : null;
        Path next = index + 1 < shots.size() ? shots.get(index + 1).file() : null;
        boolean goingBack = selected != null && selected.equals(next);
        selected = shots.get(index).file();
        var nearby = new ArrayList<Path>(3);
        nearby.add(selected);
        if (goingBack && previous != null) nearby.add(previous);
        if (next != null) nearby.add(next);
        if (!goingBack && previous != null) nearby.add(previous);
        wanted = nearby;
        discardUnusedTextures();
        failed.retainAll(wanted);
        return textures.get(selected);
    }

    boolean loading() {
        return selected != null && !textures.containsKey(selected) && !failed();
    }

    boolean failed() {
        return failed.contains(selected);
    }

    Image poll(Viewport viewport) {
        if (selected == null) return null;
        float displayDensity = displayDensity(viewport);
        if (density != displayDensity) {
            density = displayDensity;
            clearTextures();
            failed.clear();
        }
        if (job != null && job.result().isDone()) {
            DecodeJob finished = job;
            job = null;
            collect(finished);
        }
        if (job == null) {
            for (Path file : wanted) {
                if (!eligible(file)) break;
                if (textures.containsKey(file) || failed.contains(file)) continue;
                float scale = density;
                job = new DecodeJob(file, scale, CompletableFuture.supplyAsync(
                        () -> decode(file, scale), DECODER));
                break;
            }
        }
        return textures.get(selected);
    }

    private void collect(DecodeJob finished) {
        Path file = finished.file();
        boolean needed = finished.density() == density && eligible(file);
        try {
            Decoded decoded = finished.result().join();
            try {
                if (needed) {
                    var texture = new Texture(decoded.pixels(), false);
                    texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
                    textures.put(file, new Image(texture, decoded.originalWidth(),
                            decoded.originalHeight()));
                }
            } finally {
                decoded.dispose();
            }
        } catch (RuntimeException problem) {
            if (needed) failed.add(file);
        }
    }

    private boolean eligible(Path file) {
        double upperScale = density / MIN_UNSCALED_RATIO;
        return file.equals(selected) || (wanted.contains(file)
                && IMAGE_AREA.width * IMAGE_AREA.height * upperScale * upperScale
                        * wanted.size() <= MAX_PREFETCH_PIXELS);
    }

    private void discardUnusedTextures() {
        var iterator = textures.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (eligible(entry.getKey())) continue;
            entry.getValue().texture().dispose();
            iterator.remove();
        }
    }

    private static float displayDensity(Viewport viewport) {
        float x = viewport.getScreenWidth() / Math.max(1f, viewport.getWorldWidth())
                * Gdx.graphics.getBackBufferWidth() / Math.max(1, Gdx.graphics.getWidth());
        float y = viewport.getScreenHeight() / Math.max(1f, viewport.getWorldHeight())
                * Gdx.graphics.getBackBufferHeight() / Math.max(1, Gdx.graphics.getHeight());
        return Math.max(0.01f, Math.max(x, y));
    }

    private static Decoded decode(Path file, float density) {
        Pixmap source = new Pixmap(new FileHandle(file.toFile()));
        int width = source.getWidth();
        int height = source.getHeight();
        float logicalFit = Math.min(1f, Math.min(IMAGE_AREA.width / width, IMAGE_AREA.height / height));
        float scale = Math.min(1f, logicalFit * density);
        // Close to native size, resampling costs more than uploading the decoded pixels.
        if (scale >= MIN_UNSCALED_RATIO) {
            return new Decoded(source, width, height);
        }
        int previewWidth = (int) Math.ceil(width * scale);
        int previewHeight = (int) Math.ceil(height * scale);
        try {
            return new Decoded(GdxTextureQuality.resizePixmap(source, previewWidth, previewHeight), width, height);
        } finally {
            source.dispose();
        }
    }

    private void clearTextures() {
        for (Image image : textures.values()) image.texture().dispose();
        textures.clear();
    }

    @Override
    public void close() {
        selected = null;
        wanted = List.of();
        failed.clear();
        clearTextures();
        if (job != null) {
            job.result().thenAccept(Decoded::dispose);
            job = null;
        }
    }
}

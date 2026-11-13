package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Keeps the active font family intact while its replacement is built. */
final class GdxFontRefresh implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(GdxFontRefresh.class.getName());
    private static final Executor WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "coronapoker-font-rasterizer");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private static final long RESIZE_DEBOUNCE_NANOS = 300_000_000L;

    private GdxAdaptiveFontQuality.Profile requested;
    private long dueAt;
    private Batch batch;

    void resize(GdxAdaptiveFontQuality.Profile active,
            GdxAdaptiveFontQuality.Profile next) {
        if (next.equals(active)) {
            close();
        } else if (!next.equals(requested)) {
            close();
            requested = next;
            dueAt = System.nanoTime() + RESIZE_DEBOUNCE_NANOS;
        } else if (batch == null) {
            dueAt = System.nanoTime() + RESIZE_DEBOUNCE_NANOS;
        }
    }

    void update(Supplier<List<Font>> fonts,
            Consumer<GdxAdaptiveFontQuality.Profile> installed) {
        if (requested == null || System.nanoTime() < dueAt) return;
        try {
            if (batch == null) batch = new Batch(fonts.get(), requested);
            if (!batch.advance()) return;
        } catch (RuntimeException failure) {
            close();
            LOG.log(Level.WARNING, "Could not refresh display fonts", failure);
            return;
        }
        Batch ready = batch;
        var profile = requested;
        batch = null;
        requested = null;
        ready.install();
        installed.accept(profile);
    }

    @Override
    public void close() {
        requested = null;
        if (batch != null) {
            batch.close();
            batch = null;
        }
    }

    record Font(FileHandle source, int size, float border, Color color,
            Color borderColor, String characters, Consumer<BitmapFont> install) {

        Font(FileHandle source, int size, float border, Color borderColor,
                String characters, Consumer<BitmapFont> install) {
            this(source, size, border, Color.WHITE, borderColor, characters,
                    install);
        }

        BitmapFont generate(GdxAdaptiveFontQuality.Profile profile) {
            var generator = new FreeTypeFontGenerator(source);
            try {
                return GdxAdaptiveFontQuality.generateForCharacters(generator,
                        size, border, color, borderColor, characters, profile);
            } finally {
                generator.dispose();
            }
        }

        GdxAdaptiveFontQuality.PreparedFont prepare(
                GdxAdaptiveFontQuality.Profile profile) {
            var generator = new FreeTypeFontGenerator(source);
            try {
                return GdxAdaptiveFontQuality.prepare(generator,
                        size, border, color, borderColor, characters, profile);
            } finally {
                generator.dispose();
            }
        }
    }

    private static final class Batch implements AutoCloseable {

        private final List<Font> fonts;
        private final GdxAdaptiveFontQuality.Profile profile;
        private final List<BitmapFont> uploaded = new ArrayList<>();
        private CompletableFuture<GdxAdaptiveFontQuality.PreparedFont> pending;
        private GdxAdaptiveFontQuality.PreparedFont current;

        Batch(List<Font> fonts, GdxAdaptiveFontQuality.Profile profile) {
            this.fonts = fonts;
            this.profile = profile;
            prepare(0);
        }

        private void prepare(int index) {
            if (index == fonts.size()) return;
            Font font = fonts.get(index);
            pending = CompletableFuture.supplyAsync(() -> font.prepare(profile), WORKER);
        }

        boolean advance() {
            if (current == null) {
                if (!pending.isDone()) return false;
                current = pending.join();
                pending = null;
                prepare(uploaded.size() + 1);
            }
            if (!current.uploadNextPage()) return false;
            uploaded.add(current.createFont());
            current.close();
            current = null;
            return uploaded.size() == fonts.size();
        }

        void install() {
            for (int i = 0; i < fonts.size(); i++) {
                fonts.get(i).install().accept(uploaded.get(i));
            }
        }

        @Override
        public void close() {
            if (current != null) current.close();
            if (pending != null) {
                // This result has never been uploaded, so it can be freed by
                // the rasterizer if the resize or scene was superseded.
                pending.thenAccept(GdxAdaptiveFontQuality.PreparedFont::close);
            }
            for (BitmapFont font : uploaded) font.dispose();
        }
    }
}

package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import java.util.ArrayDeque;
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
    private static final long REPLACEMENT_UPLOAD_BUDGET_NANOS = 2_000_000L;
    private static final int MAX_REPLACEMENT_PAGES_PER_FRAME = 3;
    private static final int REPLACEMENT_PREPARATION_DEPTH = 3;

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

    void replace(GdxAdaptiveFontQuality.Profile profile) {
        if (profile.equals(requested) && dueAt == 0) return;
        close();
        requested = profile;
        dueAt = 0;
    }

    void update(Supplier<List<Font>> fonts,
            Consumer<GdxAdaptiveFontQuality.Profile> installed) {
        update(fonts, installed, () -> { });
    }

    void update(Supplier<List<Font>> fonts,
            Consumer<GdxAdaptiveFontQuality.Profile> installed, Runnable failed) {
        if (requested == null || (dueAt != 0 && System.nanoTime() < dueAt)) return;
        try {
            if (batch == null) batch = new Batch(fonts.get(), requested, dueAt == 0);
            if (!batch.advance()) return;
        } catch (RuntimeException failure) {
            close();
            LOG.log(Level.WARNING, "Could not refresh display fonts", failure);
            failed.run();
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
            Color borderColor, String characters, Consumer<BitmapFont> install,
            GdxLocaleFonts localeFonts) {

        Font(FileHandle source, int size, float border, Color borderColor,
                String characters, Consumer<BitmapFont> install) {
            this(source, size, border, Color.WHITE, borderColor, characters, install, null);
        }

        Font(FileHandle source, int size, float border, Color color, Color borderColor,
                String characters, Consumer<BitmapFont> install) {
            this(source, size, border, color, borderColor, characters, install, null);
        }

        Font withLocale(GdxLocaleFonts locale) {
            return new Font(source, size, border, color, borderColor, characters, install, locale);
        }

        BitmapFont generate(GdxAdaptiveFontQuality.Profile profile) {
            var generator = new FreeTypeFontGenerator(source);
            try {
                return GdxAdaptiveFontQuality.generateLocalized(generator,
                        size, border, color, borderColor, characters, profile, localeFonts);
            } finally {
                generator.dispose();
            }
        }

        GdxAdaptiveFontQuality.PreparedFont prepare(
                GdxAdaptiveFontQuality.Profile profile) {
            var generator = new FreeTypeFontGenerator(source);
            try {
                return GdxAdaptiveFontQuality.prepare(generator,
                        size, border, color, borderColor, characters, profile, localeFonts);
            } finally {
                generator.dispose();
            }
        }
    }

    private static final class Batch implements AutoCloseable {

        private final List<Font> fonts;
        private final GdxAdaptiveFontQuality.Profile profile;
        private final boolean replacing;
        private final List<BitmapFont> uploaded = new ArrayList<>();
        private final ArrayDeque<CompletableFuture<GdxAdaptiveFontQuality.PreparedFont>>
                pending = new ArrayDeque<>();
        private GdxAdaptiveFontQuality.PreparedFont current;
        private int nextFont;
        private volatile boolean closed;

        Batch(List<Font> fonts, GdxAdaptiveFontQuality.Profile profile, boolean replacing) {
            this.fonts = fonts;
            this.profile = profile;
            this.replacing = replacing;
            int depth = replacing ? REPLACEMENT_PREPARATION_DEPTH : 1;
            for (int index = 0; index < depth; index++) prepareNextFont();
        }

        private void prepareNextFont() {
            if (nextFont == fonts.size()) return;
            Font font = fonts.get(nextFont++);
            pending.addLast(CompletableFuture.supplyAsync(
                    () -> closed ? null : font.prepare(profile), WORKER));
        }

        boolean advance() {
            long started = System.nanoTime();
            int pageLimit = replacing ? MAX_REPLACEMENT_PAGES_PER_FRAME : 1;
            for (int page = 0; page < pageLimit; page++) {
                if (current == null) {
                    var prepared = pending.peekFirst();
                    if (!prepared.isDone()) return false;
                    current = pending.removeFirst().join();
                    prepareNextFont();
                }
                if (current.uploadNextPage()) {
                    uploaded.add(current.createFont());
                    current.close();
                    current = null;
                    if (uploaded.size() == fonts.size()) return true;
                }
                if (System.nanoTime() - started >= REPLACEMENT_UPLOAD_BUDGET_NANOS) break;
            }
            return false;
        }

        void install() {
            for (int i = 0; i < fonts.size(); i++) {
                fonts.get(i).install().accept(uploaded.get(i));
            }
        }

        @Override
        public void close() {
            closed = true;
            if (current != null) current.close();
            for (var prepared : pending) {
                // These results are still CPU-only. Release them on whichever
                // thread finishes preparation after a resize or cancellation.
                prepared.thenAccept(font -> {
                    if (font != null) font.close();
                });
            }
            for (BitmapFont font : uploaded) font.dispose();
        }
    }
}

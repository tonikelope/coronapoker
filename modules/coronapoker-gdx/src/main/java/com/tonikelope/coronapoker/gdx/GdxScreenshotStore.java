/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.BufferUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Screenshot storage and catalog for GDX. */
final class GdxScreenshotStore {

    private static final String PREFIX = "coronapoker_screenshot_";
    private static final String SUFFIX = ".png";
    private static final Executor SCANNER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "coronapoker-screenshot-catalog");
        thread.setDaemon(true);
        return thread;
    });

    record Shot(Path file, long createdAt) {
    }

    private GdxScreenshotStore() {
    }

    static Path directory() {
        return Path.of(System.getProperty("user.home"),
                ".coronapoker", "Screenshots");
    }

    static String filename(long timestamp) {
        return PREFIX + timestamp + SUFFIX;
    }

    static void writeOpaquePng(Path file, int width, int height, byte[] rgba)
            throws IOException {
        writeOpaquePng(file, width, height, rgba, false);
    }

    static void writeOpaquePng(Path file, int width, int height, byte[] rgba,
            boolean flipY) throws IOException {
        Files.createDirectories(file.getParent());
        // The RGB channels already contain the rendered frame.
        for (int alpha = 3; alpha < rgba.length; alpha += 4) {
            rgba[alpha] = (byte) 0xff;
        }
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            BufferUtils.copy(rgba, 0, pixmap.getPixels(), rgba.length);
            PixmapIO.writePNG(new FileHandle(file.toFile()), pixmap, -1, flipY);
        } finally {
            pixmap.dispose();
        }
    }

    static boolean isScreenshotFile(Path file) {
        if (file == null || file.getFileName() == null) return false;
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith(PREFIX) && name.endsWith(SUFFIX);
    }

    static boolean isManaged(Path directory, Path file) {
        if (directory == null || file == null || !isScreenshotFile(file)) {
            return false;
        }
        Path root = directory.toAbsolutePath().normalize();
        Path candidate = file.toAbsolutePath().normalize();
        return root.equals(candidate.getParent());
    }

    static CompletableFuture<List<Shot>> scanAsync() {
        Path root = directory();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return scan(root);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }, SCANNER);
    }

    static List<Shot> scan(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (var paths = Files.list(directory)) {
            return newestFirst(paths.filter(GdxScreenshotStore::isScreenshotFile)
                    .map(GdxScreenshotStore::readShot)
                    .filter(Objects::nonNull)
                    .toList());
        }
    }

    private static Shot readShot(Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file,
                    BasicFileAttributes.class);
            if (!attributes.isRegularFile()) return null;
            long created = attributes.creationTime().toMillis();
            return new Shot(file, created > 0L ? created
                    : attributes.lastModifiedTime().toMillis());
        } catch (IOException failure) {
            // A file can be removed or replaced after directory enumeration.
            return null;
        }
    }

    static List<Shot> newestFirst(List<Shot> shots) {
        return shots.stream()
                .sorted(Comparator.comparingLong(Shot::createdAt)
                        .reversed()
                        .thenComparing(shot -> shot.file().getFileName()
                                .toString(), String.CASE_INSENSITIVE_ORDER
                                .reversed()))
                .toList();
    }

    static String displayTitle(Shot shot, int index, int total,
            String language) {
        if (shot == null || total <= 0) return "";
        Locale locale = Locale.forLanguageTag(language == null
                ? "es" : language);
        String when = DateFormat.getDateTimeInstance(DateFormat.LONG,
                DateFormat.MEDIUM, locale).format(new Date(shot.createdAt()));
        return when + "     ( " + (index + 1) + " / " + total + " )";
    }
}

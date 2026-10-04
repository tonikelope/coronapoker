/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Shared screenshot catalog used by every GDX entry point. */
final class GdxScreenshotStore {

    private static final String PREFIX = "coronapoker_screenshot_";
    private static final String SUFFIX = ".png";

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

    static List<Shot> scan() throws IOException {
        return scan(directory());
    }

    static List<Shot> scan(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (var paths = Files.list(directory)) {
            return newestFirst(paths.filter(Files::isRegularFile)
                    .filter(GdxScreenshotStore::isScreenshotFile)
                    .map(path -> new Shot(path, creationMillis(path)))
                    .toList());
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

    static long creationMillis(Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file,
                    BasicFileAttributes.class);
            long created = attributes.creationTime().toMillis();
            return created > 0L ? created
                    : attributes.lastModifiedTime().toMillis();
        } catch (IOException failure) {
            try {
                return Files.getLastModifiedTime(file).toMillis();
            } catch (IOException ignored) {
                return 0L;
            }
        }
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

/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Copies new CEF log records into the same JUL pipeline as the game debug log. */
final class PokerVideoChromiumLog implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(PokerVideoChromiumLog.class.getName());
    private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s\"'<>]+");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final int MAX_LINE_BYTES = 2048;
    private static final int MAX_LINES_PER_READ = 12;
    private final Path file;
    private final ByteBuffer chunk = ByteBuffer.allocate(16 * 1024);
    private final ByteArrayOutputStream line = new ByteArrayOutputStream();
    private final ScheduledExecutorService reader;
    private long offset;
    private Object fileKey;
    private boolean readFailed;

    PokerVideoChromiumLog(Path file) {
        this.file = file;
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            offset = attributes.size();
            fileKey = attributes.fileKey();
        } catch (NoSuchFileException ignored) {
            // Chromium creates the file on demand.
        } catch (IOException failure) {
            LOG.log(Level.WARNING, "Cannot read the previous Chromium log", failure);
        }
        reader = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "poker-video-chromium-log");
            thread.setDaemon(true);
            return thread;
        });
        reader.scheduleWithFixedDelay(this::forward, 1, 1, TimeUnit.SECONDS);
    }

    private synchronized void forward() {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (attributes.size() < offset || !Objects.equals(fileKey, attributes.fileKey())) {
                offset = 0;
                line.reset();
            }
            fileKey = attributes.fileKey();
            if (attributes.size() == offset) {
                readFailed = false;
                return;
            }
            chunk.clear();
            try (FileChannel input = FileChannel.open(file)) {
                if (input.read(chunk, offset) <= 0) return;
            }
            readFailed = false;
            chunk.flip();
            offset += chunk.remaining();
            int lines = 0;
            while (chunk.hasRemaining()) {
                byte value = chunk.get();
                if (value == '\n') {
                    if (line.size() != 0 && ++lines <= MAX_LINES_PER_READ) {
                        String message = clean(line.toString(StandardCharsets.UTF_8));
                        Level level = message.contains(":ERROR:") || message.contains(":FATAL:")
                                ? Level.SEVERE : message.contains(":WARNING:") ? Level.WARNING : Level.INFO;
                        LOG.log(level, "Chromium native | {0}", message);
                    }
                    line.reset();
                } else if (line.size() < MAX_LINE_BYTES) {
                    line.write(value);
                }
            }
            if (lines > MAX_LINES_PER_READ) {
                LOG.log(Level.INFO, "Chromium native | {0} more lines in the native log",
                        lines - MAX_LINES_PER_READ);
            }
        } catch (NoSuchFileException ignored) {
            // The file can disappear between the stat and the read.
        } catch (IOException failure) {
            if (!readFailed) LOG.log(Level.WARNING, "Cannot forward the Chromium log", failure);
            readFailed = true;
        }
    }

    static String clean(String message) {
        if (message == null) return "";
        // Request URLs can contain signed media tokens. Keep the error and source file.
        String preview = message.substring(0, Math.min(message.length(), MAX_LINE_BYTES));
        return CONTROL.matcher(URL.matcher(preview).replaceAll("<URL>")).replaceAll(" ").strip();
    }

    @Override
    public void close() {
        reader.shutdownNow();
        forward();
    }
}

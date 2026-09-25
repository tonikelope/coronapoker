package com.tonikelope.coronapoker.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Process owner of persistent CoronaPoker preferences. */
public final class PreferencesService implements ApplicationService {

    private static final Logger LOGGER = Logger.getLogger(PreferencesService.class.getName());
    private static final long DEFERRED_FLUSH_MILLIS = 500;

    private final Path file;
    private final Properties properties = new Properties();
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pendingFlush;
    private Thread shutdownHook;
    private Path rescueCopy;
    private boolean loaded;
    private boolean started;
    private boolean dirty;
    private boolean closed;

    public PreferencesService(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        ensureOpen();
        ensureLoaded();
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CoronaPoker-Preferences-Flush");
            thread.setDaemon(true);
            return thread;
        });
        shutdownHook = new Thread(this::flushDuringShutdown, "CoronaPoker-Preferences-Flush-Hook");
        shutdownHook.setDaemon(false);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        started = true;
    }

    /** Available before start so the legacy Swing static graph can bind safely. */
    public synchronized Properties properties() {
        ensureOpen();
        ensureLoaded();
        return properties;
    }

    public Path file() {
        return file;
    }

    public synchronized Path rescueCopy() {
        ensureLoaded();
        return rescueCopy;
    }

    public synchronized void saveDeferred() {
        ensureStarted();
        dirty = true;
        if (pendingFlush != null) {
            pendingFlush.cancel(false);
        }
        pendingFlush = scheduler.schedule(this::flushDeferred,
                DEFERRED_FLUSH_MILLIS, TimeUnit.MILLISECONDS);
    }

    public synchronized void save() throws IOException {
        ensureStarted();
        cancelPendingFlush();
        saveNow();
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        IOException failure = null;
        try {
            cancelPendingFlush();
            if (dirty) {
                saveNow();
            }
        } catch (IOException ex) {
            failure = ex;
        } finally {
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            removeShutdownHook();
            closed = true;
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.notExists(file)) {
                Files.createFile(file);
            }
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE,
                    "Could not read the preferences file - keeping a copy and starting with what could be read", ex);
            keepRescueCopy();
        } finally {
            loaded = true;
        }
    }

    private void keepRescueCopy() {
        try {
            if (Files.exists(file)) {
                Path copy = file.resolveSibling(file.getFileName() + "_"
                        + System.currentTimeMillis() + ".corrupto");
                Files.copy(file, copy);
                rescueCopy = copy;
                LOGGER.log(Level.SEVERE, "A copy of the unreadable preferences file was kept at {0}", copy);
            }
        } catch (Exception copyFailure) {
            LOGGER.log(Level.SEVERE, "Could not keep a copy of the unreadable preferences file", copyFailure);
        }
    }

    private synchronized void flushDeferred() {
        try {
            if (!closed && dirty) {
                saveNow();
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Could not save preferences", ex);
        }
    }

    private synchronized void flushDuringShutdown() {
        try {
            if (!closed && dirty) {
                saveNow();
            }
        } catch (Throwable ignored) {
            // The process is already terminating; preserve the previous silent hook behavior.
        }
    }

    private void saveNow() throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            properties.store(buffer, null);
            writeStringAtomic(file, buffer.toString(StandardCharsets.ISO_8859_1));
            dirty = false;
        }
    }

    private void cancelPendingFlush() {
        if (pendingFlush != null) {
            pendingFlush.cancel(false);
            pendingFlush = null;
        }
    }

    private void removeShutdownHook() {
        if (shutdownHook == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM shutdown is already in progress and the hook owns the final flush.
        }
        shutdownHook = null;
    }

    private void ensureStarted() {
        ensureOpen();
        if (!started) {
            throw new IllegalStateException("Preferences service has not started");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Preferences service is closed");
        }
    }

    private static void writeStringAtomic(Path target, CharSequence data) throws IOException {
        Path temporary = target.resolveSibling(
                target.getFileName() + ".tmp-" + Long.toHexString(System.nanoTime()));
        try {
            Files.writeString(temporary, data);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception ignored) {
                // Best effort only; preserve the original write failure.
            }
            throw ex;
        }
    }
}

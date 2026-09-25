package com.tonikelope.coronapoker.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Process service that downloads and launches the external updater. */
public final class UpdaterService implements ApplicationService {

    public record Request(String version, Path currentJar, Path newJar,
            Path javaExecutable, boolean spanish) {

        public Request {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("version is required");
            }
            Objects.requireNonNull(currentJar, "currentJar");
            Objects.requireNonNull(newJar, "newJar");
            Objects.requireNonNull(javaExecutable, "javaExecutable");
        }
    }

    @FunctionalInterface
    public interface Downloader {

        Path download() throws Exception;
    }

    @FunctionalInterface
    public interface Launcher {

        void launch(List<String> command) throws Exception;
    }

    private final Downloader downloader;
    private final Launcher launcher;
    private boolean started;
    private boolean closed;

    public UpdaterService(Downloader downloader, Launcher launcher) {
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
    }

    public static UpdaterService production(URI updaterUri, Path temporaryDirectory,
            int connectTimeoutMillis, int readTimeoutMillis) {
        Objects.requireNonNull(updaterUri, "updaterUri");
        Path target = Objects.requireNonNull(temporaryDirectory, "temporaryDirectory")
                .toAbsolutePath().normalize().resolve("coronaupdater.jar");
        return new UpdaterService(
                () -> download(updaterUri, target, connectTimeoutMillis, readTimeoutMillis),
                command -> new ProcessBuilder(command).start());
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        if (closed) {
            throw new IllegalStateException("Updater service is closed");
        }
        started = true;
    }

    /** Performs the characterized blocking handoff; callers must run off the UI thread. */
    public boolean handoff(Request request) throws Exception {
        synchronized (this) {
            ensureStarted();
        }
        Path updater = downloader.download();
        if (updater == null) {
            return false;
        }
        List<String> command = new ArrayList<>(List.of(
                request.javaExecutable().toString(), "-jar", updater.toString(),
                request.version(), request.currentJar().toString(), request.newJar().toString()));
        if (request.spanish()) {
            command.add("¡Santiago y cierra, España!");
        }
        launcher.launch(List.copyOf(command));
        return true;
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    private void ensureStarted() {
        if (!started) {
            throw new IllegalStateException("Updater service has not started");
        }
        if (closed) {
            throw new IllegalStateException("Updater service is closed");
        }
    }

    private static Path download(URI uri, Path target, int connectTimeout, int readTimeout)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.addRequestProperty("User-Agent",
                    "Mozilla/5.0 (X11; Linux x86_64; rv:61.0) Gecko/20100101 Firefox/61.0");
            connection.setUseCaches(false);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            Files.createDirectories(target.getParent());
            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                    BufferedOutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
                input.transferTo(output);
            }
            return target;
        } finally {
            connection.disconnect();
        }
    }
}

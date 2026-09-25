package com.tonikelope.coronapoker.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Process owner of asynchronous CoronaPoker release checks. */
public final class UpdateService implements ApplicationService {

    public enum Status {
        UPDATE_AVAILABLE,
        CURRENT,
        UNAVAILABLE
    }

    public record CheckResult(Status status, String version) {

        public CheckResult {
            Objects.requireNonNull(status, "status");
            if (status == Status.UPDATE_AVAILABLE) {
                if (version == null || version.isBlank()) {
                    throw new IllegalArgumentException("An available update requires a version");
                }
            } else if (version != null) {
                throw new IllegalArgumentException("Only an available update has a version");
            }
        }
    }

    @FunctionalInterface
    public interface LatestVersionSource {

        /** Returns the latest major.minor version, or null when it cannot be determined. */
        String latestVersion() throws Exception;
    }

    private static final Logger LOGGER = Logger.getLogger(UpdateService.class.getName());
    private static final Pattern VERSION_PATTERN = Pattern.compile("([0-9]+)\\.([0-9]+)$");
    private static final Pattern RELEASE_LOCATION_PATTERN
            = Pattern.compile("releases/tag/v?([0-9]+\\.[0-9]+)");

    private final String currentVersion;
    private final int attempts;
    private final LatestVersionSource source;
    private ExecutorService executor;
    private CompletableFuture<CheckResult> currentCheck;
    private boolean started;
    private boolean closed;

    public UpdateService(String currentVersion, int attempts, LatestVersionSource source) {
        this.currentVersion = requireVersion(currentVersion, "currentVersion");
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be positive");
        }
        this.attempts = attempts;
        this.source = Objects.requireNonNull(source, "source");
    }

    public static UpdateService forLatestRelease(
            URI latestReleaseUri, String currentVersion, int attempts, Duration timeout) {
        Objects.requireNonNull(latestReleaseUri, "latestReleaseUri");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return new UpdateService(currentVersion, attempts,
                () -> readLatestVersion(latestReleaseUri, timeout));
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        if (closed) {
            throw new IllegalStateException("Update service is closed");
        }
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CoronaPoker-update-check");
            thread.setDaemon(true);
            return thread;
        });
        started = true;
    }

    /** Starts or joins the one release check currently in flight. */
    public synchronized CompletableFuture<CheckResult> checkLatest() {
        ensureStarted();
        if (currentCheck != null && !currentCheck.isDone()) {
            return currentCheck;
        }
        CompletableFuture<CheckResult> check
                = CompletableFuture.supplyAsync(this::checkWithRetries, executor);
        currentCheck = check;
        check.whenComplete((ignoredResult, ignoredFailure) -> clearCompleted(check));
        return check;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (currentCheck != null && !currentCheck.isDone()) {
            currentCheck.cancel(true);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private CheckResult checkWithRetries() {
        for (int attempt = 0; attempt < attempts && !Thread.currentThread().isInterrupted(); attempt++) {
            CheckResult result = checkOnce();
            if (result != null) {
                return result;
            }
        }
        return new CheckResult(Status.UNAVAILABLE, null);
    }

    private CheckResult checkOnce() {
        try {
            String latestVersion = source.latestVersion();
            if (latestVersion == null) {
                return null;
            }
            latestVersion = requireVersion(latestVersion, "latestVersion");
            if (compareVersions(currentVersion, latestVersion) < 0) {
                return new CheckResult(Status.UPDATE_AVAILABLE, latestVersion);
            }
            return new CheckResult(Status.CURRENT, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage());
            return null;
        }
    }

    private synchronized void clearCompleted(CompletableFuture<CheckResult> check) {
        if (currentCheck == check) {
            currentCheck = null;
        }
    }

    private void ensureStarted() {
        if (!started) {
            throw new IllegalStateException("Update service has not started");
        }
        if (closed) {
            throw new IllegalStateException("Update service is closed");
        }
    }

    private static String readLatestVersion(URI uri, Duration timeout) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("Cache-Control", "no-cache")
                .GET()
                .build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String candidate = null;
        if (response.statusCode() >= 300 && response.statusCode() < 400) {
            candidate = response.headers().firstValue("Location").orElse(null);
        } else {
            candidate = response.body();
        }
        if (candidate == null) {
            return null;
        }
        Matcher matcher = RELEASE_LOCATION_PATTERN.matcher(candidate);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int compareVersions(String left, String right) {
        Matcher leftMatcher = VERSION_PATTERN.matcher(left);
        Matcher rightMatcher = VERSION_PATTERN.matcher(right);
        if (!leftMatcher.find() || !rightMatcher.find()) {
            throw new IllegalArgumentException("Invalid major.minor version");
        }
        int major = Integer.compare(
                Integer.parseInt(leftMatcher.group(1)), Integer.parseInt(rightMatcher.group(1)));
        return major != 0 ? major : Integer.compare(
                Integer.parseInt(leftMatcher.group(2)), Integer.parseInt(rightMatcher.group(2)));
    }

    private static String requireVersion(String version, String name) {
        Objects.requireNonNull(version, name);
        if (!VERSION_PATTERN.matcher(version).find()) {
            throw new IllegalArgumentException(name + " must end in major.minor");
        }
        return version;
    }
}

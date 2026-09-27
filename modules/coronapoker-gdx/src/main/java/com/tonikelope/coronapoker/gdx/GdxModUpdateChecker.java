/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the two-line update descriptor used by CoronaPoker MOD packs. */
final class GdxModUpdateChecker {

    enum Status {
        UPDATE_AVAILABLE,
        CURRENT,
        UNAVAILABLE
    }

    record Result(Status status, String version, String downloadUrl) {

        Result {
            Objects.requireNonNull(status, "status");
            if (status == Status.UPDATE_AVAILABLE) {
                if (version == null || version.isBlank()
                        || downloadUrl == null || downloadUrl.isBlank()) {
                    throw new IllegalArgumentException(
                            "an available MOD update requires version and URL");
                }
            } else if (version != null || downloadUrl != null) {
                throw new IllegalArgumentException(
                        "only an available MOD update has metadata");
            }
        }
    }

    @FunctionalInterface
    interface DescriptorSource {

        String read(URI uri) throws Exception;
    }

    private static final Pattern VERSION = Pattern.compile(
            "([0-9]+)\\.([0-9]+)");

    private GdxModUpdateChecker() {
    }

    static Result check(String currentVersion, URI descriptor,
            Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        return check(currentVersion, descriptor,
                uri -> readDescriptor(uri, timeout));
    }

    static Result check(String currentVersion, URI descriptor,
            DescriptorSource source) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(source, "source");
        try {
            String body = source.read(descriptor);
            if (body == null) return unavailable();
            List<String> lines = body.lines().map(String::trim)
                    .filter(line -> !line.isEmpty()).toList();
            if (lines.size() < 2) return unavailable();
            String latest = extractVersion(lines.get(0));
            String current = extractVersion(currentVersion);
            if (latest == null || current == null) return unavailable();
            if (compare(current, latest) < 0) {
                return new Result(Status.UPDATE_AVAILABLE, latest,
                        lines.get(1));
            }
            return new Result(Status.CURRENT, null, null);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return unavailable();
        } catch (Exception failure) {
            return unavailable();
        }
    }

    static String downloadUrl(Result result, String gameVersion) {
        Objects.requireNonNull(result, "result");
        if (result.status() != Status.UPDATE_AVAILABLE) {
            throw new IllegalArgumentException("MOD update is not available");
        }
        return result.downloadUrl().replace("___CORONA_VERSION___",
                Objects.requireNonNull(gameVersion, "gameVersion"));
    }

    private static Result unavailable() {
        return new Result(Status.UNAVAILABLE, null, null);
    }

    private static String extractVersion(String value) {
        if (value == null) return null;
        Matcher matcher = VERSION.matcher(value);
        return matcher.find() ? matcher.group() : null;
    }

    private static int compare(String left, String right) {
        Matcher leftMatcher = VERSION.matcher(left);
        Matcher rightMatcher = VERSION.matcher(right);
        if (!leftMatcher.find() || !rightMatcher.find()) return 0;
        int major = Integer.compare(Integer.parseInt(leftMatcher.group(1)),
                Integer.parseInt(rightMatcher.group(1)));
        return major != 0 ? major : Integer.compare(
                Integer.parseInt(leftMatcher.group(2)),
                Integer.parseInt(rightMatcher.group(2)));
    }

    private static String readDescriptor(URI uri, Duration timeout)
            throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Cache-Control", "no-cache")
                .GET().build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.statusCode() >= 200 && response.statusCode() < 300
                ? response.body() : null;
    }
}

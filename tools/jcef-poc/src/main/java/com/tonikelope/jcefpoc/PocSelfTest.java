package com.tonikelope.jcefpoc;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Small dependency-free verification invoked by Gradle's check lifecycle. */
public final class PocSelfTest {
    private PocSelfTest() {}

    public static void main(String[] args) throws Exception {
        expect(PocConfig.DEFAULT_VIDEO_ID,
                PocConfig.extractVideoId("https://www.youtube.com/watch?v=M7lc1UVf-VE"));
        expect(PocConfig.DEFAULT_VIDEO_ID,
                PocConfig.extractVideoId("https://youtu.be/M7lc1UVf-VE?t=12"));
        expect(PocConfig.DEFAULT_VIDEO_ID,
                PocConfig.extractVideoId("https://www.youtube.com/embed/M7lc1UVf-VE"));
        expect(null, PocConfig.extractVideoId("https://example.com/not-youtube"));

        Map<String, String> parsed = PocConfig.parseArguments(
                new String[] {"--video", PocConfig.DEFAULT_VIDEO_ID, "--browser-width=800"});
        expect(PocConfig.DEFAULT_VIDEO_ID, parsed.get("video"));
        expect("800", parsed.get("browser-width"));

        try (LocalPlayerServer server = LocalPlayerServer.start(PocConfig.DEFAULT_VIDEO_ID)) {
            HttpURLConnection connection = (HttpURLConnection) URI.create(server.playerUrl())
                    .toURL().openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            expect(200, connection.getResponseCode());
            expect("strict-origin-when-cross-origin",
                    connection.getHeaderField("Referrer-Policy"));
            try (InputStream input = connection.getInputStream()) {
                String html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                require(html.contains(PocConfig.DEFAULT_VIDEO_ID), "player HTML has video id");
                require(html.contains(server.origin()), "player HTML has origin");
                require(html.contains("youtube.com/iframe_api"), "player HTML has IFrame API");
            }
        }

        System.out.println("JCEF PoC self-test: OK");
    }

    private static void expect(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError("Failed: " + label);
    }
}

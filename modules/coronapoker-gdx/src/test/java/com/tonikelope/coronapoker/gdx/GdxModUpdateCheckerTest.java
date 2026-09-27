package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

final class GdxModUpdateCheckerTest {

    private static final URI DESCRIPTOR = URI.create(
            "https://example.test/mod-version.txt");

    @Test
    void detectsANewerModAndPreservesTheDownloadTemplate() {
        GdxModUpdateChecker.Result result = GdxModUpdateChecker.check(
                "v0.57", DESCRIPTOR,
                ignored -> "Chilean MOD 0.58\n"
                        + "https://example.test/___CORONA_VERSION___/mod.zip\n");

        assertEquals(GdxModUpdateChecker.Status.UPDATE_AVAILABLE,
                result.status());
        assertEquals("0.58", result.version());
        assertEquals("https://example.test/25.5/mod.zip",
                GdxModUpdateChecker.downloadUrl(result, "25.5"));
    }

    @Test
    void reportsCurrentMalformedAndUnavailableDescriptorsPrecisely() {
        assertEquals(GdxModUpdateChecker.Status.CURRENT,
                GdxModUpdateChecker.check("0.58", DESCRIPTOR,
                        ignored -> "0.58\nhttps://example.test/mod.zip\n")
                        .status());
        assertEquals(GdxModUpdateChecker.Status.UNAVAILABLE,
                GdxModUpdateChecker.check("0.58", DESCRIPTOR,
                        ignored -> "not enough data").status());
        assertEquals(GdxModUpdateChecker.Status.UNAVAILABLE,
                GdxModUpdateChecker.check("0.58", DESCRIPTOR,
                        ignored -> { throw new java.io.IOException("offline"); })
                        .status());
    }
}

/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PokerVideoPlatformSupportTest {

    @Test
    void mapsOnlyArchitecturesBundledByTheGame() {
        assertEquals("jcef-natives-windows-amd64",
                PokerVideoPlatformSupport.nativeArtifact(
                        "Windows 11", "amd64"));
        assertEquals(null,
                PokerVideoPlatformSupport.nativeArtifact(
                        "Windows 11", "aarch64"));
        assertEquals("jcef-natives-linux-amd64",
                PokerVideoPlatformSupport.nativeArtifact(
                        "Linux", "x86_64"));
        assertEquals("jcef-natives-linux-arm64",
                PokerVideoPlatformSupport.nativeArtifact(
                        "Linux", "arm64"));
        assertEquals("jcef-natives-macosx-amd64",
                PokerVideoPlatformSupport.nativeArtifact(
                        "Mac OS X", "amd64"));
        assertEquals("jcef-natives-macosx-arm64",
                PokerVideoPlatformSupport.nativeArtifact(
                        "Darwin", "aarch64"));
        assertEquals(null, PokerVideoPlatformSupport.nativeArtifact(
                "FreeBSD", "amd64"));
        assertEquals(null, PokerVideoPlatformSupport.nativeArtifact(
                "Windows 11", "x86"));
    }

    @Test
    void availabilityRequiresTheExactNativeResource() {
        String expected = PokerVideoPlatformSupport.nativeResource(
                "jcef-natives-linux-amd64");
        assertTrue(PokerVideoPlatformSupport.isAvailable(
                "Linux", "amd64", expected::equals));
        assertFalse(PokerVideoPlatformSupport.isAvailable(
                "Linux", "amd64", ignored -> false));
        assertFalse(PokerVideoPlatformSupport.isAvailable(
                "Solaris", "amd64", ignored -> true));
    }

    @Test
    void activeBuildContainsItsOwnMatchingNatives() {
        String artifact = PokerVideoPlatformSupport.nativeArtifact(
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""));
        if (artifact != null) {
            assertTrue(PokerVideoPlatformSupport.isAvailable(),
                    "Missing bundled native resource for " + artifact);
        }
    }
}

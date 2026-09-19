/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** Guards the two cinematic assets consumed by the native GDX GAME OVER dialog. */
final class GdxGameOverAssetTest {

    @Test
    void interactiveAndFinalGameOverGifsArePackagedForGdx() throws IOException {
        assertGif("/cinematics/misc/game_over.gif");
        assertGif("/cinematics/misc/game_over_zero.gif");
    }

    private static void assertGif(String resource) throws IOException {
        try (InputStream input = GdxGameOverAssetTest.class
                .getResourceAsStream(resource)) {
            assertNotNull(input, () -> "Missing GDX GAME OVER asset " + resource);
            byte[] header = input.readNBytes(6);
            assertTrue(header.length == 6, () -> "Truncated GIF " + resource);
            boolean gif87a = java.util.Arrays.equals(header,
                    new byte[]{'G', 'I', 'F', '8', '7', 'a'});
            boolean gif89a = java.util.Arrays.equals(header,
                    new byte[]{'G', 'I', 'F', '8', '9', 'a'});
            assertTrue(gif87a || gif89a,
                    () -> "Invalid GIF header in " + resource);
        }
    }
}

/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.net;

import com.tonikelope.coronapoker.StatsSyncProtocol;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AAA tests for {@link StatsSyncProtocol} — the manifest/games message codec that
 * rides inside a {@link com.tonikelope.coronapoker.BinaryWire#TYPE_DB} frame.
 */
class StatsSyncProtocolTest {

    @Test
    void manifestRoundTrips() throws Exception {
        for (int size : new int[]{0, 1, 7, 5000}) {
            List<String> ugis = sampleUgis(size);
            byte[] msg = StatsSyncProtocol.manifestMessage(ugis);
            assertEquals(StatsSyncProtocol.MANIFEST, StatsSyncProtocol.subtype(msg));
            assertEquals(ugis, StatsSyncProtocol.readManifest(msg), "size=" + size);
        }
    }

    @Test
    void largeManifestIsCompressed() throws Exception {
        // 5000 ugis of 50 identical-alphabet chars compress hugely; the message
        // must be far smaller than the raw ~250 KB, proving gzip is in effect.
        byte[] msg = StatsSyncProtocol.manifestMessage(sampleUgis(5000));
        assertTrue(msg.length < 50_000, "manifest not compressed: " + msg.length + " bytes");
    }

    @Test
    void gamesMessageWrapsBlobLosslessly() throws Exception {
        byte[] blob = new byte[1024];
        for (int i = 0; i < blob.length; i++) {
            blob[i] = (byte) (i * 31 + 7);
        }
        byte[] msg = StatsSyncProtocol.gamesMessage(blob);
        assertEquals(StatsSyncProtocol.GAMES, StatsSyncProtocol.subtype(msg));
        assertArrayEquals(blob, StatsSyncProtocol.gamesBlob(msg));
    }

    @Test
    void emptyGamesBlobSurvives() throws Exception {
        byte[] msg = StatsSyncProtocol.gamesMessage(new byte[0]);
        assertEquals(StatsSyncProtocol.GAMES, StatsSyncProtocol.subtype(msg));
        assertEquals(0, StatsSyncProtocol.gamesBlob(msg).length);
    }

    @Test
    void subtypeMismatchIsRejected() throws Exception {
        byte[] manifest = StatsSyncProtocol.manifestMessage(sampleUgis(3));
        byte[] games = StatsSyncProtocol.gamesMessage(new byte[]{1, 2, 3});
        assertThrows(Exception.class, () -> StatsSyncProtocol.readManifest(games));
        assertThrows(Exception.class, () -> StatsSyncProtocol.gamesBlob(manifest));
    }

    @Test
    void emptyMessageHasZeroSubtype() {
        assertEquals(0, StatsSyncProtocol.subtype(new byte[0]));
        assertEquals(0, StatsSyncProtocol.subtype(null));
    }

    private static List<String> sampleUgis(int n) {
        List<String> ugis = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            // 50-char ugi-like string, deterministic, varied per index.
            StringBuilder sb = new StringBuilder(50);
            for (int j = 0; j < 50; j++) {
                sb.append((char) ('a' + ((i + j) % 26)));
            }
            ugis.add(sb.toString());
        }
        return ugis;
    }
}

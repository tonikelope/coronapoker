/*
 * Phase 1 (Ristretto255 engine) — hash-to-group (Elligator) tests, RFC 9496 §4.3.4.
 *
 * The genesis deck maps each card to a group element with UNKNOWN mutual discrete
 * log (unlike s*B): otherwise a locked deck could be de-permuted by matching card
 * ratios. This validates the one-way MAP against the official RFC 9496 Appendix A.3
 * "Group Elements from Uniform Byte Strings" vectors (the clean ones), plus the
 * functional properties the deck needs: on-curve, round-trips, deterministic, and
 * 52 distinct valid cards.
 */
package com.tonikelope.coronapoker.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HashToGroupTest {

    // RFC 9496 Appendix A.3 vectors (the ones extracted cleanly, no formatting
    // artefacts): INPUT (128 hex = 64 bytes) -> OUTPUT ristretto255 encoding (64 hex).
    private static final String[][] A3 = {
        {"f116b34b8f17ceb56e8732a60d913dd10cce47a6d53bee9204be8b44f6678b270102a56902e2488c46120e9276cfe54638286b9e4b3cdb470b542d46c2068d38",
         "f26e5b6f7d362d2d2a94c5d0e7602cb4773c95a2e5c31a64f133189fa76ed61b"},
        {"8422e1bbdaab52938b81fd602effb6f89110e1e57208ad12d9ad767e2e25510c27140775f9337088b982d83d7fcf0b2fa1edffe51952cbe7365e95c86eaf325c",
         "006ccd2a9e6867e6a2c5cea83d3302cc9de128dd2a9a57dd8ee7b9d7ffe02826"},
        {"165d697a1ef3d5cf3c38565beefcf88c0f282b8e7dbd28544c483432f1cec7675debea8ebb4e5fe7d6f6e5db15f15587ac4d4d4a1de7191e0c1ca6664abcc413",
         "ae81e7dedf20a497e10c304a765c1767a42d6e06029758d2d7e8ef7cc4c41179"},
        {"a836e6c9a9ca9f1e8d486273ad56a78c70cf18f0ce10abb1c7172ddd605d7fd2979854f47ae1ccf204a33102095b4200e5befc0465accc263175485f0e17ea5c",
         "e2705652ff9f5e44d3e841bf1c251cf7dddb77d140870d1ab2ed64f1a9ce8628"}
    };

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    @Test
    public void matchesOfficialA3Vectors() {
        for (String[] vec : A3) {
            assertEquals(128, vec[0].length(), "input must be 64 bytes");
            assertEquals(64, vec[1].length(), "output must be 32 bytes");
            byte[] got = Ristretto255.hashToGroupEncoded(hex(vec[0]));
            assertArrayEquals(hex(vec[1]), got,
                    "hashToGroup must match RFC 9496 A.3 vector for input " + vec[0].substring(0, 16) + "...");
        }
    }

    @Test
    public void mapOutputsAreOnCurve() {
        for (long t = 1; t < 60; t++) {
            assertTrue(Ristretto255.map(Fe25519.of(t)).isOnCurve(), "MAP(" + t + ") must be on the curve");
        }
    }

    @Test
    public void hashToGroupRoundTripsAndIsDeterministic() throws Exception {
        MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
        for (int i = 0; i < 40; i++) {
            byte[] seed = sha512.digest(("seed-" + i).getBytes(StandardCharsets.UTF_8));
            EdwardsPoint p = Ristretto255.hashToGroup(seed);
            byte[] enc = Ristretto255.encode(p);
            // Valid ristretto element: decodes and re-encodes to itself.
            EdwardsPoint dec = Ristretto255.decode(enc);
            assertNotNull(dec, "hashToGroup output must be a decodable element");
            assertArrayEquals(enc, Ristretto255.encode(dec), "must re-encode stably");
            // Deterministic.
            assertArrayEquals(enc, Ristretto255.encode(Ristretto255.hashToGroup(seed)),
                    "hashToGroup must be deterministic");
        }
    }

    @Test
    public void genesisDeckIs52DistinctValidCards() throws Exception {
        // Mirrors how the genesis deck will be derived: SHA-512 of a per-card seed.
        MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
        Set<String> encodings = new HashSet<>();
        for (int card = 0; card < 52; card++) {
            byte[] seed = sha512.digest(("CORONAPOKER_RISTRETTO_CARD_" + card).getBytes(StandardCharsets.UTF_8));
            byte[] enc = Ristretto255.hashToGroupEncoded(seed);
            assertNotNull(Ristretto255.decode(enc), "card " + card + " must be a valid element");
            assertTrue(encodings.add(bytesToHex(enc)), "card " + card + " must be distinct from earlier cards");
        }
        assertEquals(52, encodings.size(), "genesis deck must be 52 distinct cards");
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }
}

/*
 * Guards CryptoWarmup: the warmup cycle must exercise the REAL heavy path (cascade lock +
 * shuffle + proveStepWire + verifyChainWire), not silently no-op. If a signature or the
 * shuffle/prove contract ever drifts so the construction stops being self-consistent, the
 * cycle would still "run" but skip the expensive prove/verify — this test catches that by
 * asserting the cycle produced a valid, verifying proof.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CryptoWarmupTest {

    @BeforeAll
    public static void ensureRng() {
        // proveStepWire's prover draws blinding randomness from the CSPRNG.
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    @Test
    public void warmupCycleDoesRealProveAndVerify() {
        assertTrue(CryptoWarmup.runOneCycle(),
                "warmup cycle must complete a real prove+verify (else it is not warming the hot path)");
    }
}

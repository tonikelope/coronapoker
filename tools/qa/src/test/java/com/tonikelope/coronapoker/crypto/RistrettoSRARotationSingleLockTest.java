/*
 * Guards the "single-lock rotation" CPU optimization: applying two commutative locks in
 * sequence (uPocket then kCommunity) must be byte-identical to applying ONE lock with the
 * product scalar s = uPocket*kCommunity mod L. Crupier / WaitingRoomFrame rely on this to
 * halve the rotation scalar-muls without changing the wire bytes or the rotation proof
 * (which already uses the very same product scalar).
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RistrettoSRARotationSingleLockTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    @Test
    public void twoPassEqualsProductSinglePass() {
        byte[] deck = RistrettoSRA.getGenesisDeck();
        for (int trial = 0; trial < 32; trial++) {
            byte[] unlock = RistrettoSRA.generateLockScalar();
            byte[] lock = RistrettoSRA.generateLockScalar();

            // Old rotation form: two sequential locks over the deck.
            byte[] twoPass = RistrettoSRA.applyCommutativeLock(deck, unlock);
            twoPass = RistrettoSRA.applyCommutativeLock(twoPass, lock);

            // Optimized form: one lock with the product scalar (mod L).
            BigInteger product = RistrettoSRA.bytesToScalar(unlock)
                    .multiply(RistrettoSRA.bytesToScalar(lock))
                    .mod(RistrettoSRA.L);
            byte[] singlePass = RistrettoSRA.applyCommutativeLock(deck, RistrettoSRA.scalarToBytes(product));

            assertNotNull(twoPass, "two-pass returned null (off-group?)");
            assertNotNull(singlePass, "single-pass returned null (off-group?)");
            assertArrayEquals(twoPass, singlePass,
                    "trial " + trial + ": single-lock product must equal two sequential locks byte-for-byte");
        }
    }
}

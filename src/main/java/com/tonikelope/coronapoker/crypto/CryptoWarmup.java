/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.DeterministicShuffle;
import com.tonikelope.coronapoker.Helpers;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JIT warm-up for the heavy crypto path (SRA cascade + shuffle proof/verification), run in the
 * background at startup.
 *
 * <p>On a slow machine the first hands run through hot methods that are still interpreted / C1
 * compiled (3x-65x slower than once JIT-compiled to C2), so the first deal can feel frozen for
 * several seconds. This runs a REALISTIC cycle -the same path as a real cascade step: deck
 * lock, shuffle, {@code proveStepWire}, {@code verifyChainWire}, commitment and unlock- over
 * fixed DUMMY data, discarding the result, until per-cycle time stops improving (JIT already
 * reached C2) or a hard cap is hit. Warm-up INPUTS are fixed (dummy scalar), though the prover
 * still draws blinding scalars from the CSPRNG; harmless, since {@code SecureRandom} is
 * thread-safe and the game does not depend on a reproducible sequence. No effect on gameplay.
 *
 * <p>Crypto analogue of {@code Crupier.warmShuffleAnimCache()} (which pre-decodes the shuffle
 * GIF). Exercising the SAME path as a real deal also spares the first hand an uncommon-trap
 * deoptimization from taking a branch the warm-up never walked.
 *
 * @author tonikelope
 */
public final class CryptoWarmup {

    private static final Logger LOGGER = Logger.getLogger(CryptoWarmup.class.getName());

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    // Fixed DUMMY lock scalar (NOT from the CSPRNG): 32 little-endian bytes with the top byte
    // masked so the value stays < L (same rule as generateLockScalar) and != 0. Never leaves
    // this process; it only exercises the hot path.
    private static final byte[] DUMMY_SCALAR = buildDummyScalar();
    private static final byte[] DUMMY_SEED = buildDummySeed();

    // Stop on convergence plus hard caps (both hardware-independent): stop once per-cycle time
    // stops improving appreciably (JIT already compiled), or the cycle/time cap is hit.
    // Requiring several FLAT cycles in a row (not just one) accounts for compilation being
    // async: a method can stay interpreted/C1 for a cycle or two after crossing the threshold.
    private static final int MIN_CYCLES = 3;
    private static final int MAX_CYCLES = 25;
    private static final int STABLE_CYCLES = 2;           // consecutive flat cycles to stop
    private static final double IMPROVE_RATIO = 0.90;     // "appreciable improvement" = >10% faster
    private static final long MAX_NANOS = 4_000_000_000L; // 4 s hard cap

    private CryptoWarmup() {
    }

    private static byte[] buildDummyScalar() {
        byte[] k = new byte[32];
        for (int i = 0; i < 32; i++) {
            k[i] = (byte) (0x37 + i);
        }
        k[31] &= 0x0f; // top byte -> value < 2^252 < L, and nonzero
        return k;
    }

    private static byte[] buildDummySeed() {
        byte[] s = new byte[48];
        for (int i = 0; i < 48; i++) {
            s[i] = (byte) (0x5a + i);
        }
        return s;
    }

    /**
     * Launches the warm-up exactly once, on a background thread. Idempotent and never throws:
     * any failure is swallowed (the warm-up must never break startup).
     */
    public static void warmup() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        Helpers.threadRun(() -> {
            final Thread warmupThread = Thread.currentThread();
            final int warmupPrio = warmupThread.getPriority();
            // Slightly lowered priority: the warm-up runs full crypto cycles that would compete
            // with the EDT painting the splash window on a slow machine. NORM-1 (not NORM-2) so
            // it still reaches C2 soon. Restored in finally (cached pool thread, gets reused).
            warmupThread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            try {
                long best = Long.MAX_VALUE;
                int stable = 0;
                long deadline = System.nanoTime() + MAX_NANOS;
                int done = 0;
                for (int i = 0; i < MAX_CYCLES; i++) {
                    long t0 = System.nanoTime();
                    runOneCycle();
                    long dt = System.nanoTime() - t0;
                    done++;
                    if (dt < best * IMPROVE_RATIO) { // still improving appreciably -> keep going
                        best = Math.min(best, dt);
                        stable = 0;
                    } else {                         // no longer improving (plateau)
                        best = Math.min(best, dt);
                        if (i >= MIN_CYCLES - 1 && ++stable >= STABLE_CYCLES) {
                            break;
                        }
                    }
                    if (System.nanoTime() > deadline) {
                        break;
                    }
                }
                LOGGER.log(Level.INFO, "Crypto JIT warmup done ({0} cycles, best {1} ms)",
                        new Object[]{done, best == Long.MAX_VALUE ? -1 : (best / 1_000_000)});
            } catch (Throwable t) {
                // The warm-up must never break startup.
                LOGGER.log(Level.FINE, "Crypto JIT warmup skipped", t);
            } finally {
                warmupThread.setPriority(warmupPrio);
            }
        });
    }

    /**
     * One cycle = the same expensive operations as a real cascade step plus its proof and
     * verification (exercises {@code Fe25519.mul}, {@code EdwardsPoint.add/dbl},
     * {@code scalarMul}, {@code applyCommutativeLock} and the proofs' MSM), over dummy data.
     * Returns true if prove+verify completed (the hot path ran end to end). Package-private so
     * the test can check the cycle does REAL work, not a silent no-op.
     */
    static boolean runOneCycle() {
        byte[] genesis = RistrettoSRA.getGenesisDeck();
        byte[] locked = RistrettoSRA.applyCommutativeLock(genesis, DUMMY_SCALAR); // 52 scalarMul (cascade lock)
        if (locked == null) {
            return false;
        }
        byte[] shuffled = DeterministicShuffle.shuffleDeck(locked, DUMMY_SEED);
        int[] perm = DeterministicShuffle.shufflePermutation(genesis.length / 32, DUMMY_SEED);
        // shuffled[i] = DUMMY_SCALAR . genesis[perm[i]] (same construction as the real peer).
        byte[] proof = ShuffleCascade.proveStepWire(genesis, shuffled, perm, DUMMY_SCALAR); // prove (the costly part)
        boolean verified = false;
        if (proof != null) {
            verified = ShuffleCascade.verifyChainWire(genesis, List.of(genesis, shuffled), List.of(proof)); // verify
        }
        RistrettoSRA.commitment(DUMMY_SCALAR);      // BASE scalarMul
        RistrettoSRA.getUnlockScalar(DUMMY_SCALAR); // modInverse
        return verified;
    }
}

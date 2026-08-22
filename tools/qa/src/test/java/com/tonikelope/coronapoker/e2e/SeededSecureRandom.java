package com.tonikelope.coronapoker.e2e;

import java.security.SecureRandom;
import java.util.Random;

/** Best-effort reproducible entropy for isolated E2E JVMs; never shipped. */
final class SeededSecureRandom extends SecureRandom {

    private static final long serialVersionUID = 1L;
    private Random delegate;

    SeededSecureRandom(long seed) {
        delegate = new Random(seed);
    }

    @Override
    public synchronized void nextBytes(byte[] bytes) {
        delegate.nextBytes(bytes);
    }

    @Override
    public synchronized byte[] generateSeed(int numBytes) {
        byte[] bytes = new byte[numBytes];
        nextBytes(bytes);
        return bytes;
    }

    @Override
    public synchronized void setSeed(long seed) {
        // SecureRandom's constructor may call this before our delegate exists.
        if (delegate != null) {
            delegate.setSeed(seed);
        }
    }

    @Override
    public synchronized void setSeed(byte[] seed) {
        if (delegate == null || seed == null) {
            return;
        }
        long folded = 0xcbf29ce484222325L;
        for (byte value : seed) {
            folded ^= value & 0xffL;
            folded *= 0x100000001b3L;
        }
        delegate.setSeed(folded);
    }
}

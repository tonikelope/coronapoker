/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.util.concurrent.atomic.AtomicInteger;

/** Process-wide monotonic IDs for the current GAME protocol. */
public final class GameCommandId {

    private static final int LAST_SAFE_ID = Integer.MAX_VALUE - 1;

    /*
     * GAME ids are serialized as canonical non-negative decimal integers.  A
     * nanoTime-derived int is not suitable here: truncating the long randomly
     * starts roughly half of JVM runs in the negative range, so strict current
     * protocol decoders reject the application's own first command.
     *
     * The registry is connection-local, therefore starting the process-wide
     * sequence at zero is sufficient.  The last int value is reserved because
     * confirmed sends use id + 1 on the wire.  Exhaustion is explicit rather
     * than overflowing confirmations, wrapping negative, or silently reusing an
     * earlier value.
     */
    private static final AtomicInteger NEXT = new AtomicInteger(0);

    private GameCommandId() {
    }

    public static int next() {
        while (true) {
            int current = NEXT.get();
            if (current < 0) {
                throw new IllegalStateException("GAME command id space exhausted");
            }
            int following = current == LAST_SAFE_ID ? -1 : current + 1;
            if (NEXT.compareAndSet(current, following)) {
                return current;
            }
        }
    }
}

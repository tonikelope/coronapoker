package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PokerVideoDocumentLoadTest {
    @Test void nativeDocumentHasABoundedVisibleLoadTime() {
        var load = new PokerVideoDocumentLoad();
        assertFalse(load.poll(true, 1));
        assertFalse(load.poll(true, PokerVideoDocumentLoad.TIMEOUT_NANOS));
        assertTrue(load.poll(true, PokerVideoDocumentLoad.TIMEOUT_NANOS + 1));
        assertNotNull(load.error());
        assertFalse(load.poll(true, PokerVideoDocumentLoad.TIMEOUT_NANOS * 3));
    }

    @Test void hidingThePlayerSuspendsItsDocumentDeadline() {
        var load = new PokerVideoDocumentLoad();
        load.poll(true, 1);
        assertFalse(load.poll(false, PokerVideoDocumentLoad.TIMEOUT_NANOS * 2));
        assertFalse(load.poll(true, PokerVideoDocumentLoad.TIMEOUT_NANOS * 3));
        assertNull(load.error());
    }

    @Test void bootOrNewSessionClearsAStaleDocumentError() {
        var load = new PokerVideoDocumentLoad();
        load.failed("load failed");
        load.loaded();
        assertNull(load.error());
        assertFalse(load.poll(true, Long.MAX_VALUE));
        load.reset();
        load.poll(true, 1);
        assertTrue(load.poll(true, PokerVideoDocumentLoad.TIMEOUT_NANOS + 1));
    }
}

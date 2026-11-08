package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PokerVideoDocumentLoadTest {
    @Test void nativeDocumentHasABoundedVisibleLoadTime() {
        var load = new PokerVideoDocumentLoad();
        assertFalse(load.poll(true, false, 1));
        assertFalse(load.poll(true, false, PokerVideoDocumentLoad.TIMEOUT_NANOS));
        assertTrue(load.poll(true, false, PokerVideoDocumentLoad.TIMEOUT_NANOS + 1));
        assertNotNull(load.error());
        assertFalse(load.poll(true, false, PokerVideoDocumentLoad.TIMEOUT_NANOS * 3));
    }

    @Test void hidingThePlayerSuspendsItsDocumentDeadline() {
        var load = new PokerVideoDocumentLoad();
        load.poll(true, false, 1);
        assertFalse(load.poll(false, false, PokerVideoDocumentLoad.TIMEOUT_NANOS * 2));
        assertFalse(load.poll(true, false, PokerVideoDocumentLoad.TIMEOUT_NANOS * 3));
        assertNull(load.error());
    }

    @Test void aNewSessionClearsThePreviousError() {
        var load = new PokerVideoDocumentLoad();
        load.failed("load failed");
        load.loaded();
        assertEquals("load failed", load.error());
        load.reset();
        assertNull(load.error());
        assertFalse(load.poll(true, false, Long.MAX_VALUE));
    }

    @Test void aBootedDocumentCanStopRespondingAfterTheFirstFrame() {
        var load = new PokerVideoDocumentLoad();
        long timeout = PokerVideoDocumentLoad.TIMEOUT_NANOS;
        load.loaded();
        assertFalse(load.poll(true, true, 1));
        load.pulse();
        assertFalse(load.poll(true, true, timeout + 1));
        assertTrue(load.poll(true, true, timeout * 2 + 1));
        assertTrue(load.error().contains("dejó de responder"));
    }

    @Test void aLiveBridgeCannotHideAStallBeforeTheFirstFrame() {
        var load = new PokerVideoDocumentLoad();
        load.loaded();
        assertFalse(load.poll(true, false, 1));
        load.pulse();
        assertTrue(load.poll(true, false, PokerVideoDocumentLoad.TIMEOUT_NANOS + 1));
        assertTrue(load.error().contains("primera imagen"));
    }
}

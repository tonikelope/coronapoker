package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.Socket;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;

class HandshakeExecutorRejectedReleasesPermitTest {

    @Test
    void nullSubmissionRestoresReservedPermit() throws Exception {
        Semaphore slots = new Semaphore(1);
        Socket socket = new Socket();
        slots.acquire();

        boolean submitted = HandshakeAdmission.submit(ignored -> null, () -> { }, socket, slots);

        assertFalse(submitted);
        assertEquals(1, slots.availablePermits());
    }
}

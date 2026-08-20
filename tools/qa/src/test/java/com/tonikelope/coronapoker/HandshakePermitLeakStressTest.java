package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Socket;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;

class HandshakePermitLeakStressTest {

    @Test
    void repeatedRejectedSubmissionsDoNotExhaustAdmission() throws Exception {
        int capacity = 16;
        Semaphore slots = new Semaphore(capacity);

        for (int attempt = 0; attempt < 1_000; attempt++) {
            assertTrue(slots.tryAcquire(), "admission must recover after rejection " + attempt);
            Socket socket = new Socket();
            HandshakeAdmission.submit(ignored -> null, () -> { }, socket, slots);
            assertTrue(socket.isClosed());
        }

        assertEquals(capacity, slots.availablePermits());
    }
}

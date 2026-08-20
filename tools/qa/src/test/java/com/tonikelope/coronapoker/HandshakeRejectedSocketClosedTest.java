package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Socket;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;

class HandshakeRejectedSocketClosedTest {

    @Test
    void rejectedSubmissionClosesUnauthenticatedSocket() throws Exception {
        Semaphore slots = new Semaphore(1);
        Socket socket = new Socket();
        slots.acquire();

        boolean submitted = HandshakeAdmission.submit(ignored -> {
            throw new RejectedExecutionException("executor stopping");
        }, () -> { }, socket, slots);

        assertFalse(submitted);
        assertTrue(socket.isClosed());
    }
}

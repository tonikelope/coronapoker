package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class ClientSocketTeardownTest {

    @Test
    public void teardownCanCloseTheSocketWhileAWriterOwnsTheLifecycleLock() throws Exception {
        NetClient client = new NetClient(null);
        Socket socket = new Socket();
        client.setLocal_client_socket(socket);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        Thread stalledWriter = new Thread(() -> {
            synchronized (client.getLocal_client_socket_lock()) {
                lockHeld.countDown();
                try {
                    releaseLock.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "stalled-client-writer-test");
        stalledWriter.start();
        assertTrue(lockHeld.await(1, TimeUnit.SECONDS));

        FutureTask<Void> close = new FutureTask<>(() -> {
            client.closeClientSocketForTeardown();
            return null;
        });
        Thread closer = new Thread(close, "client-teardown-close-test");
        closer.start();
        try {
            close.get(1, TimeUnit.SECONDS);
            assertTrue(socket.isClosed());
        } finally {
            releaseLock.countDown();
            stalledWriter.join(1_000);
        }
    }

    @Test
    public void aLateReconnectCannotPublishAnOpenSocketAfterTeardown() {
        NetClient client = new NetClient(null);
        client.closeClientSocketForTeardown();

        Socket lateSocket = new Socket();
        client.setLocal_client_socket(lateSocket);

        assertTrue(lateSocket.isClosed(),
                "a reconnect racing teardown must not leave a live socket in the discarded session");
    }
}

package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

public class HostSocketTeardownTest {

    @Test
    public void closingTheHostAlsoReleasesAcceptedSocketsStillInHandshake() throws Exception {
        try (ServerSocket listener = new ServerSocket(0);
                Socket client = new Socket("127.0.0.1", listener.getLocalPort());
                Socket accepted = listener.accept()) {
            NetServer server = new NetServer(null);
            assertTrue(server.trackPendingHandshake(accepted));
            FutureTask<Integer> blockedRead = new FutureTask<>(() -> {
                try {
                    return accepted.getInputStream().read();
                } catch (java.io.IOException closed) {
                    return -1;
                }
            });
            new Thread(blockedRead, "pre-auth-reader-test").start();

            server.closeServerSocket();

            assertEquals(-1, blockedRead.get(1, TimeUnit.SECONDS));
            assertTrue(accepted.isClosed());
        }
    }

    @Test
    public void listenerPublishedAfterTeardownIsClosedImmediately() throws Exception {
        NetServer server = new NetServer(null);
        server.closeServerSocket();
        ServerSocket lateListener = new ServerSocket();

        server.setServer_socket(lateListener);

        assertTrue(lateListener.isClosed());
    }

    @Test
    public void closingTheHostAlsoReleasesAcceptedClientReaders() throws Exception {
        try (ServerSocket listener = new ServerSocket(0);
                Socket client = new Socket("127.0.0.1", listener.getLocalPort());
                Socket accepted = listener.accept()) {
            SecretKeySpec key = new SecretKeySpec(new byte[16], "AES");
            Participant participant = new Participant(null, "peer", null, accepted, key, key, false);
            FutureTask<Integer> blockedRead = new FutureTask<>(() -> {
                try {
                    return accepted.getInputStream().read();
                } catch (java.io.IOException closed) {
                    return -1;
                }
            });
            Thread reader = new Thread(blockedRead, "accepted-client-reader-test");
            reader.start();
            CountDownLatch lockHeld = new CountDownLatch(1);
            CountDownLatch releaseLock = new CountDownLatch(1);
            Thread stalledTransport = new Thread(() -> {
                synchronized (participant.getParticipant_socket_lock()) {
                    lockHeld.countDown();
                    try {
                        releaseLock.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "stalled-host-transport-test");
            stalledTransport.start();
            assertTrue(lockHeld.await(1, TimeUnit.SECONDS));

            FutureTask<Void> close = new FutureTask<>(() -> {
                WaitingRoomFrame.closeAcceptedClientSockets(List.of(participant));
                return null;
            });
            new Thread(close, "host-teardown-close-test").start();
            try {
                close.get(1, TimeUnit.SECONDS);
                assertEquals(-1, blockedRead.get(1, TimeUnit.SECONDS));
                assertTrue(accepted.isClosed(),
                        "host teardown must close accepted sockets instead of relying on peer cooperation");
            } finally {
                releaseLock.countDown();
                stalledTransport.join(1_000);
            }
        }
    }
}

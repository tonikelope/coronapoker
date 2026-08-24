package com.tonikelope.coronapoker;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class HeartbeatGenerationTest {

    @Test
    public void staleHeartbeatCannotRetireReplacementGeneration() {
        HeartbeatGeneration heartbeat = new HeartbeatGeneration();
        long oldGeneration = heartbeat.start();
        long replacementGeneration = heartbeat.start();

        assertFalse(heartbeat.isCurrent(oldGeneration));
        assertFalse(heartbeat.retire(oldGeneration));
        assertTrue(heartbeat.isAlive());
        assertTrue(heartbeat.isCurrent(replacementGeneration));
    }

    @Test
    public void currentHeartbeatCanRetireExactlyOnce() {
        HeartbeatGeneration heartbeat = new HeartbeatGeneration();
        long generation = heartbeat.start();

        assertTrue(heartbeat.retire(generation));
        assertFalse(heartbeat.retire(generation));
        assertFalse(heartbeat.isAlive());
    }

    @Test
    public void invalidationFencesCurrentGenerationUntilReplacementStarts() {
        HeartbeatGeneration heartbeat = new HeartbeatGeneration();
        long staleGeneration = heartbeat.start();

        heartbeat.invalidate();

        assertFalse(heartbeat.isCurrent(staleGeneration));
        assertFalse(heartbeat.retire(staleGeneration));
        assertFalse(heartbeat.isAlive());

        long replacementGeneration = heartbeat.start();
        assertTrue(heartbeat.isCurrent(replacementGeneration));
        assertTrue(heartbeat.isAlive());
    }

    @Test
    public void staleHeartbeatCannotCloseReplacementClientSocket() throws Exception {
        NetClient client = new NetClient(null);
        Socket staleSocket = new Socket();
        Socket replacementSocket = new Socket();

        try {
            client.setLocal_client_socket(staleSocket);
            client.setLocal_client_socket(replacementSocket);

            assertFalse(client.closeHeartbeatSocket(staleSocket));
            assertFalse(staleSocket.isClosed());
            assertFalse(replacementSocket.isClosed());
            assertEquals(replacementSocket, client.getLocal_client_socket());

            assertTrue(client.closeHeartbeatSocket(replacementSocket));
            assertTrue(replacementSocket.isClosed());
        } finally {
            staleSocket.close();
            replacementSocket.close();
        }
    }

    @Test
    public void staleHeartbeatCannotWriteThroughReplacementClientSocket() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (ServerSocket staleListener = new ServerSocket(0, 1, loopback);
                Socket staleSocket = new Socket(loopback, staleListener.getLocalPort());
                Socket stalePeer = staleListener.accept();
                ServerSocket replacementListener = new ServerSocket(0, 1, loopback);
                Socket replacementSocket = new Socket(loopback, replacementListener.getLocalPort());
                Socket replacementPeer = replacementListener.accept()) {
            NetClient client = new NetClient(null);
            client.setLocal_client_socket(replacementSocket);
            stalePeer.setSoTimeout(200);
            replacementPeer.setSoTimeout(1000);

            assertFalse(client.writeHeartbeatCommand("PING#old", staleSocket));
            assertThrows(SocketTimeoutException.class, () -> stalePeer.getInputStream().read());

            byte[] expected = "PING#new\n".getBytes(StandardCharsets.UTF_8);
            assertTrue(client.writeHeartbeatCommand("PING#new", replacementSocket));
            assertArrayEquals(expected, replacementPeer.getInputStream().readNBytes(expected.length));
        }
    }
}

/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GdxWanTrafficProxyTest {
    @Test
    void closingOneDirectionPreservesTheOtherDirectionsQueuedResponse() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (ServerSocket server = new ServerSocket(0, 1, loopback);
                var proxy = new GdxWanTrafficProxy(server.getLocalPort(), 20, 5, 1024, 1)) {
            CompletableFuture<String> request = new CompletableFuture<>();
            Thread host = new Thread(() -> {
                try (Socket connection = server.accept()) {
                    connection.setSoTimeout(10_000);
                    request.complete(new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                    connection.getOutputStream().write("final confirmation".getBytes(StandardCharsets.UTF_8));
                    connection.shutdownOutput();
                } catch (Throwable problem) {
                    request.completeExceptionally(problem);
                }
            }, "wan-proxy-half-close-test");
            host.setDaemon(true);
            host.start();

            try (Socket client = new Socket(loopback, proxy.port())) {
                client.setSoTimeout(10_000);
                client.getOutputStream().write("completed hand".getBytes(StandardCharsets.UTF_8));
                client.shutdownOutput();
                assertEquals("final confirmation",
                        new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                assertEquals("completed hand", request.get(10, TimeUnit.SECONDS));
            }
        }
    }
}

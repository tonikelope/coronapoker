/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class UpdateServiceHttpTest {

    @Test
    void retriesHttpFailureAndReadsTheTagFromAHeadRedirect() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> method = new AtomicReference<>();
        server.createContext("/releases/latest", exchange -> {
            try (exchange) {
                method.set(exchange.getRequestMethod());
                int attempt = requests.incrementAndGet();
                exchange.getResponseHeaders().set("Location",
                        attempt == 1 ? "/releases/tag/v99.99" : "/releases/tag/v27.28");
                exchange.sendResponseHeaders(attempt == 1 ? 503 : 302, -1);
            }
        });
        server.start();
        try (UpdateService service = UpdateService.forLatestRelease(
                latestReleaseUri(server), "27.27", 2, Duration.ofSeconds(2))) {
            service.start();
            assertEquals(new UpdateService.CheckResult(UpdateService.Status.UPDATE_AVAILABLE, "27.28"),
                    service.checkLatest().get(5, TimeUnit.SECONDS));
            assertEquals(2, requests.get());
            assertEquals("HEAD", method.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aServerThatNeverSendsHeadersDoesNotKeepTheCheckPending() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/releases/latest", exchange -> {
            // Leave the connection open without a response.
        });
        server.start();
        try (UpdateService service = UpdateService.forLatestRelease(
                latestReleaseUri(server), "27.27", 2, Duration.ofMillis(200))) {
            service.start();
            assertEquals(UpdateService.Status.UNAVAILABLE,
                    service.checkLatest().get(2, TimeUnit.SECONDS).status());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancellationInterruptsTheCurrentWorkerAndAllowsAnotherCheck() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (UpdateService service = new UpdateService("27.27", 2, () -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException cancelled) {
                    interrupted.countDown();
                    throw cancelled;
                }
            }
            return "27.28";
        })) {
            service.start();
            var check = service.checkLatest();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertSame(check, service.checkLatest());
            assertTrue(check.cancel(true));
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertEquals(UpdateService.Status.UPDATE_AVAILABLE,
                    service.checkLatest().get(2, TimeUnit.SECONDS).status());
            assertEquals(2, calls.get());
        }
    }

    private static URI latestReleaseUri(HttpServer server) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/releases/latest");
    }
}

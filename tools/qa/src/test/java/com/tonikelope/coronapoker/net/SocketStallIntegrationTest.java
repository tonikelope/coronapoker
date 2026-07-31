/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The socket behaviour the peer-drop handling is built on, over a real localhost
 * pair.
 *
 * None of this tests our own code: it pins the three assumptions the reasoning
 * rests on, because if any of them were false the whole design would be wrong
 * and nothing else would reveal it. They cannot be reproduced in a unit test
 * with in-memory streams, and reproducing them by hand needs two machines and a
 * suspended laptop.
 *
 * <ol>
 *   <li>writing to a peer that never reads eventually BLOCKS, once the kernel
 *       buffers fill up. This is what makes a stalled heartbeat write possible
 *       at all, and why the watchdog wraps that write in a timeout;</li>
 *   <li>closing that socket from another thread WAKES the blocked write with an
 *       exception. The whole stall handling depends on this: it closes the
 *       socket precisely to unblock the writer and let the reader take over,
 *       and it marks the close as ours so the resulting exception is not
 *       mistaken for the peer dropping;</li>
 *   <li>closing the socket also wakes a blocked read, which is what lets the
 *       reader notice and open the grace period.</li>
 * </ol>
 *
 * Kernel buffer sizes vary, so the volumes here are deliberately generous and
 * nothing asserts on an exact byte count.
 */
class SocketStallIntegrationTest {

    /** Far more than any socket buffer pair will absorb. */
    private static final int FLOOD_BYTES = 64 * 1024 * 1024;

    private static final int CHUNK = 64 * 1024;

    /** Long enough that a non-blocking write would have finished many times over. */
    private static final long BLOCKED_CHECK_MS = 2000;

    private ServerSocket server;
    private Socket writerSide;
    private Socket readerSide;

    @BeforeEach
    void connect() throws Exception {
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        writerSide = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
        readerSide = server.accept();
    }

    @AfterEach
    void close() {
        for (java.io.Closeable c : new java.io.Closeable[]{writerSide, readerSide, server}) {
            try {
                if (c != null) {
                    c.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Floods the writer side from its own thread. The reader side is never read,
     * so this is expected to wedge partway through.
     */
    private Thread floodInBackground(AtomicLong written, AtomicReference<Exception> failure,
            CountDownLatch finished, CountDownLatch started) {
        Thread flooder = new Thread(() -> {
            byte[] chunk = new byte[CHUNK];

            try {
                OutputStream out = writerSide.getOutputStream();
                started.countDown();

                for (int sent = 0; sent < FLOOD_BYTES; sent += CHUNK) {
                    out.write(chunk);
                    out.flush();
                    written.addAndGet(CHUNK);
                }
            } catch (Exception ex) {
                failure.set(ex);
            } finally {
                finished.countDown();
            }
        }, "flooder");

        flooder.setDaemon(true);
        flooder.start();

        return flooder;
    }

    @Test
    @Timeout(30)
    @DisplayName("Writing to a peer that never reads blocks instead of failing")
    void writeToASilentPeerBlocks() throws Exception {
        AtomicLong written = new AtomicLong();
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);

        floodInBackground(written, failure, finished, started);
        assertTrue(started.await(5, TimeUnit.SECONDS), "the writer never got going");

        // Nobody reads readerSide, so the buffers fill and the write wedges.
        assertFalse(finished.await(BLOCKED_CHECK_MS, TimeUnit.MILLISECONDS),
                "the write finished: the buffers absorbed everything and a stalled write "
                + "would be impossible, which is the premise the watchdog is built on");
        assertNull(failure.get(), "the write failed instead of blocking: " + failure.get());
        assertTrue(written.get() > 0, "nothing was written at all");
    }

    @Test
    @Timeout(30)
    @DisplayName("Closing the socket wakes a write that was blocked on it")
    void closingWakesTheBlockedWrite() throws Exception {
        AtomicLong written = new AtomicLong();
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);

        floodInBackground(written, failure, finished, started);
        assertTrue(started.await(5, TimeUnit.SECONDS), "the writer never got going");
        assertFalse(finished.await(BLOCKED_CHECK_MS, TimeUnit.MILLISECONDS),
                "the write never blocked, so there is nothing to wake");

        // This is what the stall handling does: close so the reader can take over.
        writerSide.close();

        assertTrue(finished.await(10, TimeUnit.SECONDS),
                "closing did NOT wake the blocked write: the stall handling would leave "
                + "that thread wedged forever");
        assertNotNull(failure.get(),
                "the blocked write came back without an error, so the close went unnoticed");
        assertTrue(failure.get() instanceof IOException,
                "expected an IOException from the closed socket, got " + failure.get());
    }

    @Test
    @Timeout(30)
    @DisplayName("Closing the socket wakes a read that was blocked on it")
    void closingWakesTheBlockedRead() throws Exception {
        AtomicBoolean woke = new AtomicBoolean(false);
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            try {
                InputStream in = readerSide.getInputStream();
                started.countDown();
                in.read();
                woke.set(true);
            } catch (Exception ex) {
                failure.set(ex);
                woke.set(true);
            } finally {
                finished.countDown();
            }
        }, "reader");

        reader.setDaemon(true);
        reader.start();

        assertTrue(started.await(5, TimeUnit.SECONDS), "the reader never got going");
        assertFalse(finished.await(BLOCKED_CHECK_MS, TimeUnit.MILLISECONDS),
                "the read returned on its own with nobody sending anything");

        readerSide.close();

        assertTrue(finished.await(10, TimeUnit.SECONDS),
                "closing did NOT wake the blocked read: the reader would never notice the "
                + "drop and would never open the grace period");
        assertTrue(woke.get(), "the read stayed blocked after the close");
    }

    private static void assertNull(Object value, String message) {
        org.junit.jupiter.api.Assertions.assertNull(value, message);
    }
}

/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/** Shapes encrypted TCP traffic in both directions while preserving byte order. */
final class GdxWanTrafficProxy implements Closeable {
    private static final Frame EOF = new Frame(0L, new byte[0]);
    private final ServerSocket listener;
    private final InetAddress loopback;
    private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();
    private final int hostPort;
    private final int delayMs;
    private final int jitterMs;
    private final int bytesPerSecond;
    private final int seed;
    private volatile long upstreamHeldUntil;
    private final AtomicInteger delayedUpstreamFrames = new AtomicInteger();

    GdxWanTrafficProxy(int hostPort, int delayMs, int jitterMs,
            int bytesPerSecond, int seed) throws IOException {
        this.hostPort = hostPort;
        this.delayMs = delayMs;
        this.jitterMs = jitterMs;
        this.bytesPerSecond = bytesPerSecond;
        this.seed = seed;
        loopback = InetAddress.getByName("127.0.0.1");
        listener = new ServerSocket(0, 8, loopback);
        startWorker(this::accept);
    }

    int port() {
        return listener.getLocalPort();
    }

    void holdUpstream(Duration duration) {
        upstreamHeldUntil = System.nanoTime() + duration.toNanos();
    }

    int delayedUpstreamFrames() {
        return delayedUpstreamFrames.get();
    }

    private void accept() {
        while (!listener.isClosed()) {
            try {
                Socket client = listener.accept();
                startWorker(() -> connect(client));
            } catch (IOException closed) {
                return;
            }
        }
    }

    private void connect(Socket client) {
        try (client; Socket host = new Socket(loopback, hostPort)) {
            openSockets.add(client);
            openSockets.add(host);
            try {
                if (listener.isClosed()) return;
                client.setTcpNoDelay(true);
                host.setTcpNoDelay(true);
                Thread upstream = startWorker(() -> relay(client, host, seed * 2, true));
                try {
                    relay(host, client, seed * 2 + 1, false);
                } finally {
                    upstream.interrupt();
                }
            } finally {
                openSockets.remove(host);
                openSockets.remove(client);
            }
        } catch (IOException closed) {
            // The peer's real transport observes the resulting EOF.
        }
    }

    private void relay(Socket from, Socket to, int directionSeed, boolean upstream) {
        BlockingQueue<Frame> frames = new ArrayBlockingQueue<>(64);
        Thread reader = startWorker(() -> queueFrames(from, directionSeed, frames));
        try (OutputStream target = to.getOutputStream()) {
            long available = 0L;
            for (Frame current = frames.take(); current != EOF; current = frames.take()) {
                long transfer = (long) current.bytes().length * 1_000_000_000L / bytesPerSecond;
                long readyAt = current.readyAt();
                if (upstream && readyAt < upstreamHeldUntil) {
                    delayedUpstreamFrames.incrementAndGet();
                    readyAt = upstreamHeldUntil;
                }
                available = Math.max(available, readyAt) + transfer;
                long remaining;
                while ((remaining = available - System.nanoTime()) > 0L) {
                    if (Thread.currentThread().isInterrupted()) return;
                    LockSupport.parkNanos(remaining);
                }
                target.write(current.bytes());
            }
        } catch (IOException closed) {
            // Preserve the actual socket's disconnect signal.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            reader.interrupt();
        }
    }

    private void queueFrames(Socket from, int directionSeed, BlockingQueue<Frame> frames) {
        Random jitter = new Random(directionSeed);
        byte[] buffer = new byte[8192];
        try {
            try {
                InputStream source = from.getInputStream();
                for (int length; (length = source.read(buffer)) != -1;) {
                    int delay = delayMs + jitter.nextInt(-jitterMs, jitterMs + 1);
                    frames.put(new Frame(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay),
                            Arrays.copyOf(buffer, length)));
                }
            } catch (IOException closed) {
                // Deliver pending frames before signaling EOF.
            }
            frames.put(EOF);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() throws IOException {
        listener.close();
        for (Socket socket : openSockets) {
            socket.close();
        }
    }

    private static Thread startWorker(Runnable action) {
        Thread worker = new Thread(action, "gdx-wan-traffic-proxy");
        worker.setDaemon(true);
        worker.start();
        return worker;
    }

    private record Frame(long readyAt, byte[] bytes) { }
}

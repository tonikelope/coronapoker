/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/** Bounded FIFO outbox whose entries belong to exactly one socket generation. */
public final class SessionOutbox {

    public static final class Entry {
        private final String command;
        private final long generation;
        private final int bytes;
        private final int wireId;

        private Entry(String command, long generation, int bytes, int wireId) {
            this.command = command;
            this.generation = generation;
            this.bytes = bytes;
            this.wireId = wireId;
        }

        public String command() { return command; }
        public long generation() { return generation; }
        public int wireId() { return wireId; }
    }

    private final int maxElements;
    private final long maxBytes;
    private final ArrayDeque<Entry> queue = new ArrayDeque<>();
    private long generation;
    private long queuedBytes;

    public SessionOutbox(int maxElements, long maxBytes) {
        if (maxElements <= 0 || maxBytes <= 0L) {
            throw new IllegalArgumentException("positive outbox limits are required");
        }
        this.maxElements = maxElements;
        this.maxBytes = maxBytes;
    }

    public synchronized boolean offer(String command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        int bytes = command.getBytes(StandardCharsets.UTF_8).length;
        if (queue.size() >= maxElements || bytes > maxBytes - queuedBytes) {
            return false;
        }
        queue.addLast(new Entry(command, generation, bytes,
                GameCommandId.next()));
        queuedBytes += bytes;
        notifyAll();
        return true;
    }

    public synchronized Entry peek() { return queue.peekFirst(); }

    public synchronized boolean removeIfHead(Entry entry) {
        if (entry == null || queue.peekFirst() != entry) return false;
        queue.removeFirst();
        queuedBytes -= entry.bytes;
        return true;
    }

    public synchronized boolean isCurrent(Entry entry) {
        return entry != null && entry.generation == generation;
    }

    /** Invalidates all queued or already leased entries from the previous socket. */
    public synchronized long advanceGeneration() {
        generation++;
        queue.clear();
        queuedBytes = 0L;
        notifyAll();
        return generation;
    }

    /**
     * Invalidates leases from the previous socket while retaining the pending
     * plaintext commands in FIFO order for authenticated retransmission with
     * the new socket keys.
     */
    public synchronized long advanceGenerationPreservingEntries() {
        generation++;
        ArrayDeque<Entry> rebound = new ArrayDeque<>(queue.size());
        for (Entry entry : queue) {
            rebound.addLast(new Entry(entry.command, generation, entry.bytes, entry.wireId));
        }
        queue.clear();
        queue.addAll(rebound);
        notifyAll();
        return generation;
    }

    public synchronized long generation() { return generation; }
    public synchronized boolean isEmpty() { return queue.isEmpty(); }
    public synchronized int size() { return queue.size(); }
    public synchronized long queuedBytes() { return queuedBytes; }
}

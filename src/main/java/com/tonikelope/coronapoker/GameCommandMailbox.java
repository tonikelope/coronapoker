/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;

/** Bounded FIFO mailbox whose deferred commands retain age and ordering. */
public final class GameCommandMailbox {

    private static final class Metadata {
        final long acceptedAt;
        final Runnable closeAction;
        Metadata(long acceptedAt, Runnable closeAction) {
            this.acceptedAt = acceptedAt;
            this.closeAction = closeAction;
        }
    }

    private final int capacity;
    private final long maxDeferredAge;
    private final LongSupplier clock;
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final Map<String, Metadata> metadata = new WeakHashMap<>();

    public GameCommandMailbox(int capacity, long maxDeferredAge, LongSupplier clock) {
        if (capacity <= 0 || maxDeferredAge <= 0L || clock == null) {
            throw new IllegalArgumentException("positive mailbox limits and clock are required");
        }
        this.capacity = capacity;
        this.maxDeferredAge = maxDeferredAge;
        this.clock = clock;
    }

    public boolean offer(String command, Runnable closeAction) {
        if (command == null) throw new IllegalArgumentException("command is required");
        synchronized (this) {
            if (queue.size() < capacity) {
                queue.addLast(command);
                metadata.put(command, new Metadata(clock.getAsLong(), closeAction));
                notifyAll();
                return true;
            }
        }
        runClose(closeAction);
        return false;
    }

    /** Direct local insertion; overflow is explicit. */
    public boolean add(String command) {
        if (!offer(command, null)) throw new IllegalStateException("critical mailbox full");
        return true;
    }

    public synchronized boolean addAll(Collection<? extends String> commands) {
        for (String command : commands) add(command);
        return !commands.isEmpty();
    }

    public synchronized String poll() { return queue.pollFirst(); }
    public synchronized String peek() { return queue.peekFirst(); }
    public synchronized boolean contains(String command) { return queue.contains(command); }
    public synchronized boolean isEmpty() { return queue.isEmpty(); }
    public synchronized int size() { return queue.size(); }

    public synchronized void clear() {
        queue.clear();
        metadata.clear();
    }

    /**
     * Restores scanned-but-unconsumed commands ahead of unscanned newer ones.
     * Expired commands are not restored; their source is closed explicitly.
     *
     * @return number of expired commands
     */
    public int restoreRejected(List<String> rejected) {
        if (rejected == null || rejected.isEmpty()) return 0;
        List<Runnable> closes = new ArrayList<>();
        int expired = 0;
        synchronized (this) {
            long now = clock.getAsLong();
            for (int i = rejected.size() - 1; i >= 0; i--) {
                String command = rejected.get(i);
                Metadata meta = metadata.get(command);
                if (meta != null && now - meta.acceptedAt >= maxDeferredAge) {
                    metadata.remove(command);
                    if (meta.closeAction != null) closes.add(meta.closeAction);
                    expired++;
                } else {
                    if (queue.size() >= capacity) {
                        throw new IllegalStateException("critical mailbox overflow while restoring order");
                    }
                    queue.addFirst(command);
                }
            }
        }
        for (Runnable close : closes) runClose(close);
        return expired;
    }

    private static void runClose(Runnable closeAction) {
        if (closeAction != null) closeAction.run();
    }
}

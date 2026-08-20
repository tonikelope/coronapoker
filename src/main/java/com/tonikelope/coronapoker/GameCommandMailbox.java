/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** Weak key with identity, rather than String-content, equality. */
    private static final class CommandRef extends WeakReference<String> {
        private final int identityHash;

        CommandRef(String command, ReferenceQueue<String> queue) {
            super(command, queue);
            this.identityHash = System.identityHashCode(command);
        }

        CommandRef(String command) {
            super(command);
            this.identityHash = System.identityHashCode(command);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CommandRef)) return false;
            String mine = get();
            return mine != null && mine == ((CommandRef) other).get();
        }
    }

    private final int capacity;
    private final long maxDeferredAge;
    private final LongSupplier clock;
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final ReferenceQueue<String> collectedCommands = new ReferenceQueue<>();
    private final Map<CommandRef, Metadata> metadata = new HashMap<>();

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
            purgeCollectedMetadata();
            if (queue.size() < capacity) {
                // One object per accepted occurrence: equal command text from two sources must
                // never alias age or closeAction metadata.
                String queuedCommand = new String(command);
                queue.addLast(queuedCommand);
                metadata.put(new CommandRef(queuedCommand, collectedCommands),
                        new Metadata(clock.getAsLong(), closeAction));
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

    /**
     * Rejects an already-polled critical occurrence and closes exactly the source that
     * supplied it. Returns false when that occurrence no longer has live metadata.
     */
    public boolean reject(String command) {
        if (command == null) return false;
        Runnable close = null;
        boolean found;
        synchronized (this) {
            purgeCollectedMetadata();
            Metadata meta = metadata.remove(new CommandRef(command));
            found = meta != null;
            if (meta != null) close = meta.closeAction;
        }
        runClose(close);
        return found;
    }

    public synchronized void clear() {
        queue.clear();
        metadata.clear();
        while (collectedCommands.poll() != null) {
        }
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
            purgeCollectedMetadata();
            long now = clock.getAsLong();
            for (int i = rejected.size() - 1; i >= 0; i--) {
                String command = rejected.get(i);
                CommandRef lookup = new CommandRef(command);
                Metadata meta = metadata.get(lookup);
                if (meta != null && now - meta.acceptedAt >= maxDeferredAge) {
                    metadata.remove(lookup);
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

    private void purgeCollectedMetadata() {
        CommandRef ref;
        while ((ref = (CommandRef) collectedCommands.poll()) != null) {
            metadata.remove(ref);
        }
    }

    private static void runClose(Runnable closeAction) {
        if (closeAction != null) closeAction.run();
    }
}

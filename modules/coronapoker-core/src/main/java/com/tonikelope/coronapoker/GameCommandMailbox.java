/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded FIFO mailbox whose deferred commands retain ordering and source ownership. */
public final class GameCommandMailbox {

    private static final class Metadata {
        final Runnable closeAction;
        Metadata(Runnable closeAction) {
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
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final ReferenceQueue<String> collectedCommands = new ReferenceQueue<>();
    private final Map<CommandRef, Metadata> metadata = new HashMap<>();

    public GameCommandMailbox(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("positive mailbox capacity is required");
        }
        this.capacity = capacity;
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
                        new Metadata(closeAction));
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
     * An authenticated and accepted critical command never expires merely while
     * waiting for the game phase that consumes it. Capacity remains bounded at
     * admission; a protocol violation is rejected explicitly by {@link #reject}.
     */
    public void restoreRejected(List<String> rejected) {
        if (rejected == null || rejected.isEmpty()) return;
        synchronized (this) {
            purgeCollectedMetadata();
            for (int i = rejected.size() - 1; i >= 0; i--) {
                String command = rejected.get(i);
                if (queue.size() >= capacity) {
                    throw new IllegalStateException("critical mailbox overflow while restoring order");
                }
                queue.addFirst(command);
            }
        }
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

/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Request-scoped, bounded-by-real-work confirmation state. */
public final class ConfirmationTracker {

    public static final class Request {

        private final int id;

        private Request(int id) {
            this.id = id;
        }
    }

    private final Map<Integer, Pending> pending = new LinkedHashMap<>();

    public synchronized Request register(int confirmationId, Collection<String> expectedPeers) {
        if (expectedPeers == null || expectedPeers.isEmpty()) {
            throw new IllegalArgumentException("expectedPeers required");
        }
        if (pending.containsKey(confirmationId)) {
            throw new IllegalStateException("confirmation id already pending: " + confirmationId);
        }
        Request request = new Request(confirmationId);
        pending.put(confirmationId, new Pending(request, expectedPeers));
        return request;
    }

    public synchronized boolean confirm(String peer, int confirmationId) {
        Pending state = pending.get(confirmationId);
        if (state == null || peer == null || !state.remaining.remove(peer)) {
            return false;
        }
        notifyAll();
        return true;
    }

    public synchronized boolean isPending(Request request, String peer) {
        Pending state = state(request);
        return state != null && state.remaining.contains(peer);
    }

    public synchronized boolean isComplete(Request request) {
        Pending state = state(request);
        return state != null && state.remaining.isEmpty();
    }

    public synchronized List<String> remaining(Request request) {
        Pending state = state(request);
        if (state == null) {
            throw new IllegalStateException("confirmation request does not belong to this tracker or is closed");
        }
        return new ArrayList<>(state.remaining);
    }

    public synchronized void cancelPeer(Request request, String peer) {
        Pending state = state(request);
        if (state != null && state.remaining.remove(peer)) {
            notifyAll();
        }
    }

    public synchronized void close(Request request) {
        Pending state = state(request);
        if (state != null) {
            pending.remove(request.id);
            notifyAll();
        }
    }

    public synchronized void clear() {
        pending.clear();
        notifyAll();
    }

    public synchronized void wakeAll() {
        notifyAll();
    }

    public synchronized int pendingRequestCount() {
        return pending.size();
    }

    private Pending state(Request request) {
        if (request == null) {
            return null;
        }
        Pending state = pending.get(request.id);
        return state != null && state.request == request ? state : null;
    }

    private static final class Pending {

        private final Request request;
        private final Set<String> remaining;

        private Pending(Request request, Collection<String> expectedPeers) {
            this.request = request;
            this.remaining = new LinkedHashSet<>(expectedPeers);
            if (remaining.contains(null)) {
                throw new IllegalArgumentException("null peer");
            }
        }
    }
}

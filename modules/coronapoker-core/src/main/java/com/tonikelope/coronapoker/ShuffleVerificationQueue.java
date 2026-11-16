/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.crypto.DualLockWire;
import com.tonikelope.coronapoker.crypto.ShuffleProofContext;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serial, snapshot-based queue for the peer-side verification of dual-lock
 * shuffle bundles ({@code DUALLOCK_BUNDLE}, see {@code SECURITY.md §2.6}).
 *
 * <p>
 * A bounded queue and a single daemon worker let the network reader enqueue
 * the proof without waiting for expensive verification. Each job represents
 * one hand's deck and bundle. The live unlock handler waits for the verdict
 * for that exact installed deck before using a local unlock key.
 *
 * <p>
 * The {@link Sink} publishes the verdict. A late success cannot authorize a
 * replacement deck. Failed and malformed proofs close the critical channel.
 */
public final class ShuffleVerificationQueue {

    private static final Logger LOGGER = Logger.getLogger(ShuffleVerificationQueue.class.getName());

    /**
     * Default bounded capacity. On real hardware the queue holds 0-1 jobs; the
     * cap is a runaway guard.
     */
    public static final int DEFAULT_CAPACITY = 64;

    /**
     * Verifies one job. Default delegates to {@link DualLockWire}; tests inject
     * a fake to probe mechanics.
     */
    public interface Verifier {

        boolean verify(Job job);
    }

    /**
     * Receives each job's verdict, on the worker thread. Implementations must
     * be thread-safe.
     */
    public interface Sink {

        /**
         * The deck is a proven-honest shuffle.
         */
        void onVerified(byte[] megapacket, int handId);

        /**
         * The deck FAILED the honest-shuffle proof — a proven-dishonest deck
         * (host cheated or a bug).
         */
        void onDishonest(byte[] megapacket, int handId);

        /**
         * The bundle could not be evaluated (malformed / threw); ambiguous,
         * treat as "not verified".
         */
        void onMalformed(byte[] megapacket, int handId, Exception error);
    }

    /**
     * Immutable snapshot of one hand's dual-lock bundle, captured at enqueue
     * time off the live state.
     */
    public static final class Job {

        public final byte[] genesis;
        public final List<byte[]> cascadeDecks;
        public final List<byte[]> cascadeProofs;
        public final ShuffleProofContext context;
        public final byte[] megapacket;
        public final List<byte[]> rotationStates;
        public final List<byte[]> rotationProofs;
        public final int handId;

        public Job(ShuffleProofContext context, byte[] genesis, List<byte[]> cascadeDecks,
                List<byte[]> cascadeProofs, byte[] megapacket, List<byte[]> rotationStates,
                List<byte[]> rotationProofs, int handId) {
            this.genesis = genesis;
            this.cascadeDecks = cascadeDecks;
            this.cascadeProofs = cascadeProofs;
            this.context = context;
            this.megapacket = megapacket;
            this.rotationStates = rotationStates;
            this.rotationProofs = rotationProofs;
            this.handId = handId;
        }
    }

    /**
     * Production verifier: the peer-side full dual-lock chain check (genesis
     * recomputed by the caller).
     */
    public static final Verifier DUALLOCK_VERIFIER = job -> DualLockWire.verifyFullChainWire(
            job.context, job.genesis, job.cascadeDecks, job.cascadeProofs,
            job.megapacket, job.rotationStates, job.rotationProofs);

    private final ArrayBlockingQueue<Job> queue;
    private final Sink sink;
    private final Verifier verifier;
    private volatile boolean running;
    private Thread worker;

    public ShuffleVerificationQueue(Sink sink) {
        this(sink, DUALLOCK_VERIFIER, DEFAULT_CAPACITY);
    }

    ShuffleVerificationQueue(Sink sink, Verifier verifier, int capacity) {
        if (sink == null || verifier == null) {
            throw new IllegalArgumentException("sink and verifier are required");
        }
        this.sink = sink;
        this.verifier = verifier;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * Start the single daemon worker (idempotent).
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::runLoop, "shuffle-verify-queue");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Enqueue a verification job. Never blocks the caller: if the bounded queue
     * is full the job is rejected with a log. The production caller must close
     * the critical host channel on {@code false}; it may not continue the hand.
     *
     * @return true if accepted, false if rejected (queue full)
     */
    public boolean enqueue(Job job) {
        if (job == null) {
            return false;
        }
        boolean accepted = queue.offer(job);
        if (!accepted) {
            LOGGER.log(Level.WARNING,
                    "SHUFFLE-VERIFY: queue full. Rejecting critical verify job for hand {0}. "
                    + "caller must close the host channel", job.handId);
        }
        return accepted;
    }

    /**
     * Stop the worker and let it exit after the current job (idempotent).
     */
    public synchronized void shutdown() {
        running = false;
        queue.clear();
        if (worker != null) {
            worker.interrupt();
        }
    }

    /**
     * Number of jobs waiting (not counting one in flight). Test/diagnostic aid.
     */
    public int pending() {
        return queue.size();
    }

    private void runLoop() {
        while (running) {
            Job job;
            try {
                job = queue.take();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                boolean verified = verifier.verify(job);
                // Verification can be CPU/native work that does not honor an
                // interrupt. If teardown happened while it was running, never
                // deliver its late verdict into a replacement GameFrame.
                synchronized (this) {
                    if (!running) {
                        break;
                    }
                    // Serialize the final lifecycle check and callback with
                    // shutdown(). Once shutdown returns, no late verdict can
                    // reach a database or table graph that its owner may now
                    // safely close.
                    if (verified) {
                        sink.onVerified(job.megapacket, job.handId);
                    } else {
                        sink.onDishonest(job.megapacket, job.handId);
                    }
                }
            } catch (Exception e) {
                if (!running) {
                    break;
                }
                // A verifier that throws (malformed bundle, decode error) is ambiguous, not proof of
                // cheating: report it as malformed so the Sink treats it as "not verified", never as a
                // confirmed dishonest deck.
                sink.onMalformed(job.megapacket, job.handId, e);
            }
        }
    }
}

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
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serial, snapshot-based queue for the peer-side verification of dual-lock shuffle bundles
 * ({@code DUALLOCK_BUNDLE}, see {@code SECURITY.md §2.6}).
 *
 * <p><b>Why a queue and not one background thread per hand.</b> The honest-shuffle proof is verified
 * off the live state during betting. The previous {@code Helpers.threadRun}-per-hand approach read the
 * live {@code local_mega_packet} at completion time, so on slow hardware a verification could be
 * <b>clobbered</b>: if hand N+1 dealt before hand N's verify finished, the thread ended up checking
 * hand N's bundle against hand N+1's deck — the bytes no longer matched, so a real smuggle in hand N was
 * never actually proven dishonest (it degraded to {@code flags.bit1} "unverified", the weakest signal).
 * Worse, multiple per-hand threads piled up competing for CPU exactly on the machines that could least
 * afford it.
 *
 * <p>This queue fixes both: every {@link Job} carries an <b>immutable snapshot</b> of its own hand's deck
 * and bundle, and a <b>single</b> daemon worker drains them in FIFO order. A pathologically slow player
 * still finishes verifying past hands — and a smuggle in a past hand is still caught — even after the live
 * hand has moved on. Bounded capacity: a full queue drops the oldest-blocked enqueue with a log rather
 * than growing without limit (no silent cap).
 *
 * <p>Scope: this verifies and dispatches a verdict to a {@link Sink}; it does not touch UI or game state
 * itself. The {@code Sink} (wired by {@code Crupier}) decides what a verdict means — update the live
 * "deck verified" gate only if the snapshot is still the current deck, or soft-warn on a proven-dishonest
 * deck. The real-time anti-peek of a <i>live</i> player's pocket is unchanged and independent of this
 * (the synchronous DLEQ de-lock chain); this only makes the deck-honesty audit reliable on slow hardware.
 */
public final class ShuffleVerificationQueue {

    private static final Logger LOGGER = Logger.getLogger(ShuffleVerificationQueue.class.getName());

    /** Default bounded capacity. On real hardware the queue holds 0-1 jobs; the cap is a runaway guard. */
    public static final int DEFAULT_CAPACITY = 64;

    /** Verifies one job. Default delegates to {@link DualLockWire}; tests inject a fake to probe mechanics. */
    public interface Verifier {
        boolean verify(Job job);
    }

    /** Receives each job's verdict, on the worker thread. Implementations must be thread-safe. */
    public interface Sink {
        /** The deck is a proven-honest shuffle. */
        void onVerified(byte[] megapacket, int handId);

        /** The deck FAILED the honest-shuffle proof — a proven-dishonest deck (host cheated or a bug). */
        void onDishonest(byte[] megapacket, int handId);

        /** The bundle could not be evaluated (malformed / threw); ambiguous, treat as "not verified". */
        void onMalformed(byte[] megapacket, int handId, Exception error);
    }

    /** Immutable snapshot of one hand's dual-lock bundle, captured at enqueue time off the live state. */
    public static final class Job {
        public final byte[] genesis;
        public final List<byte[]> cascadeDecks;
        public final List<byte[]> cascadeProofs;
        public final int pocketCount;
        public final byte[] megapacket;
        public final List<byte[]> rotationStates;
        public final List<byte[]> rotationProofs;
        public final int handId;

        public Job(byte[] genesis, List<byte[]> cascadeDecks, List<byte[]> cascadeProofs, int pocketCount,
                   byte[] megapacket, List<byte[]> rotationStates, List<byte[]> rotationProofs, int handId) {
            this.genesis = genesis;
            this.cascadeDecks = cascadeDecks;
            this.cascadeProofs = cascadeProofs;
            this.pocketCount = pocketCount;
            this.megapacket = megapacket;
            this.rotationStates = rotationStates;
            this.rotationProofs = rotationProofs;
            this.handId = handId;
        }
    }

    /** Production verifier: the peer-side full dual-lock chain check (genesis recomputed by the caller). */
    public static final Verifier DUALLOCK_VERIFIER = job -> DualLockWire.verifyFullChainWire(
            job.genesis, job.cascadeDecks, job.cascadeProofs, job.pocketCount,
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

    /** Start the single daemon worker (idempotent). */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::runLoop, "shuffle-verify-queue");
        worker.setDaemon(true);
        // Prioridad rebajada: el chequeo de honestidad del mazo va FUERA del camino crítico
        // (drena el backlog en background); no debe robar CPU a la partida en PCs lentos.
        worker.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
        worker.start();
    }

    /**
     * Enqueue a verification job. Never blocks the caller: if the bounded queue is full the job is
     * dropped with a log (the deck stays unverified ⇒ receipt {@code bit1}), so a runaway slow client
     * cannot grow memory without bound.
     *
     * @return true if accepted, false if dropped (queue full)
     */
    public boolean enqueue(Job job) {
        if (job == null) {
            return false;
        }
        boolean accepted = queue.offer(job);
        if (!accepted) {
            LOGGER.log(Level.WARNING,
                    "SHUFFLE-VERIFY: queue full (capacity reached) — dropping verify job for hand {0}; "
                    + "that deck stays unverified (receipt bit1)", job.handId);
        }
        return accepted;
    }

    /** Stop the worker and let it exit after the current job (idempotent). */
    public synchronized void shutdown() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    /** Number of jobs waiting (not counting one in flight). Test/diagnostic aid. */
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
                if (verifier.verify(job)) {
                    sink.onVerified(job.megapacket, job.handId);
                } else {
                    sink.onDishonest(job.megapacket, job.handId);
                }
            } catch (Exception e) {
                // A verifier that throws (malformed bundle, decode error) is ambiguous, not proof of
                // cheating: report it as malformed so the Sink treats it as "not verified", never as a
                // confirmed dishonest deck.
                sink.onMalformed(job.megapacket, job.handId, e);
            }
        }
    }
}

/*
 * Mecanica de ShuffleVerificationQueue (la correccion de fondo de DualLockWire.verifyFullChainWire
 * ya la cubren DualLockWireTest/DualLockCascadeTest; aqui probamos la COLA): FIFO, dispatch
 * honesto/deshonesto/malformed, snapshot por job, tope que descarta+reporta, y shutdown limpio.
 * Verificador inyectado (fake) para probar la mecanica sin coste cripto.
 */
package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShuffleVerificationQueueTest {

    /** Records every verdict (thread-safe) and counts down a latch so the test can await processing. */
    private static final class RecordingSink implements ShuffleVerificationQueue.Sink {
        final List<Integer> verified = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> dishonest = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> malformed = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch latch;

        RecordingSink(int expected) {
            this.latch = new CountDownLatch(expected);
        }

        @Override
        public void onVerified(byte[] megapacket, int handId) {
            verified.add(handId);
            latch.countDown();
        }

        @Override
        public void onDishonest(byte[] megapacket, int handId) {
            dishonest.add(handId);
            latch.countDown();
        }

        @Override
        public void onMalformed(byte[] megapacket, int handId, Exception error) {
            malformed.add(handId);
            latch.countDown();
        }
    }

    private static ShuffleVerificationQueue.Job job(int handId) {
        return new ShuffleVerificationQueue.Job(null, null, null, 0,
                new byte[]{(byte) handId}, null, null, handId);
    }

    @Test
    public void fifoOrderPreserved() throws InterruptedException {
        int n = 20;
        RecordingSink sink = new RecordingSink(n);
        ShuffleVerificationQueue q = new ShuffleVerificationQueue(sink, j -> true, 256);
        q.start();
        for (int i = 0; i < n; i++) {
            assertTrue(q.enqueue(job(i)), "job " + i + " aceptado");
        }
        assertTrue(sink.latch.await(10, TimeUnit.SECONDS), "todos procesados a tiempo");
        q.shutdown();

        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            expected.add(i);
        }
        assertEquals(expected, sink.verified, "un unico worker drena en orden FIFO");
    }

    @Test
    public void honestAndDishonestDispatched() throws InterruptedException {
        int n = 10;
        RecordingSink sink = new RecordingSink(n);
        // Verificador fake: par => honesto, impar => deshonesto.
        ShuffleVerificationQueue q = new ShuffleVerificationQueue(
                sink, j -> j.handId % 2 == 0, 256);
        q.start();
        for (int i = 0; i < n; i++) {
            q.enqueue(job(i));
        }
        assertTrue(sink.latch.await(10, TimeUnit.SECONDS), "procesados");
        q.shutdown();

        assertEquals(List.of(0, 2, 4, 6, 8), sink.verified, "pares -> onVerified");
        assertEquals(List.of(1, 3, 5, 7, 9), sink.dishonest, "impares -> onDishonest");
        assertTrue(sink.malformed.isEmpty(), "ninguno malformed");
    }

    @Test
    public void verifierThrowBecomesMalformed() throws InterruptedException {
        RecordingSink sink = new RecordingSink(1);
        ShuffleVerificationQueue q = new ShuffleVerificationQueue(sink, j -> {
            throw new IllegalStateException("bundle podrido");
        }, 256);
        q.start();
        q.enqueue(job(7));
        assertTrue(sink.latch.await(10, TimeUnit.SECONDS), "procesado");
        q.shutdown();

        assertEquals(List.of(7), sink.malformed, "un verificador que lanza -> onMalformed (no onDishonest)");
        assertTrue(sink.dishonest.isEmpty(), "throw NO se confunde con deck deshonesto probado");
    }

    @Test
    public void fullQueueDropsAndReports() {
        // Worker SIN arrancar: la cola se llena hasta el tope y los siguientes enqueue se descartan.
        RecordingSink sink = new RecordingSink(0);
        int cap = 3;
        ShuffleVerificationQueue q = new ShuffleVerificationQueue(sink, j -> true, cap);
        assertTrue(q.enqueue(job(0)), "0 cabe");
        assertTrue(q.enqueue(job(1)), "1 cabe");
        assertTrue(q.enqueue(job(2)), "2 cabe (tope)");
        assertFalse(q.enqueue(job(3)), "3 se descarta (cola llena) — sin cap silencioso");
        assertFalse(q.enqueue(job(4)), "4 se descarta");
        assertEquals(cap, q.pending(), "la cola no crece por encima del tope");
        q.shutdown();
    }

    @Test
    public void shutdownIsCleanAndIdempotent() throws InterruptedException {
        RecordingSink sink = new RecordingSink(1);
        ShuffleVerificationQueue q = new ShuffleVerificationQueue(sink, j -> true, 8);
        q.start();
        q.start(); // idempotente
        q.enqueue(job(0));
        assertTrue(sink.latch.await(10, TimeUnit.SECONDS), "procesado");
        q.shutdown();
        q.shutdown(); // idempotente, no lanza
    }
}

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
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.DeterministicShuffle;
import com.tonikelope.coronapoker.Helpers;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Calentamiento del JIT de la criptografía pesada (cascada SRA + prueba/verificación de
 * barajado) al ARRANQUE, en background.
 *
 * <p>En un PC lento las primeras manos corren con los métodos calientes todavía
 * interpretados / en C1 (3×–65× más lentos que ya compilados a C2), así que el primer
 * reparto se siente congelado varios segundos. Aquí se ejecuta un ciclo REALISTA —las
 * mismas rutas que un paso de cascada real: lock del deck, shuffle, {@code proveStepWire},
 * {@code verifyChainWire}, commitment y unlock— sobre datos DUMMY fijos, descartando el
 * resultado, hasta que el tiempo por ciclo deja de mejorar (el JIT ya compiló a C2) o se
 * alcanza un tope duro. Las ENTRADAS del warmup son fijas (escalar dummy), aunque el prover
 * sí saca escalares de blinding del CSPRNG — inofensivo: SecureRandom es thread-safe y el
 * juego no depende de una secuencia reproducible. Ningún efecto en el juego.
 *
 * <p>Es el análogo cripto de {@code Crupier.warmShuffleAnimCache()} (que pre-decodifica el
 * GIF del barajado). Ejecutar el MISMO camino que el reparto real evita además que la
 * primera mano pague una desoptimización por trampa poco común (uncommon trap) al tomar
 * una rama que el calentamiento no hubiera recorrido.
 *
 * @author tonikelope
 */
public final class CryptoWarmup {

    private static final Logger LOGGER = Logger.getLogger(CryptoWarmup.class.getName());

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    // Escalar de bloqueo DUMMY fijo (NO del CSPRNG): 32 bytes little-endian con el byte más
    // significativo enmascarado para quedar < L (mismo criterio que generateLockScalar) y != 0.
    // No sale del proceso; solo ejercita el camino caliente.
    private static final byte[] DUMMY_SCALAR = buildDummyScalar();
    private static final byte[] DUMMY_SEED = buildDummySeed();

    // Auto-parada por convergencia + topes duros (independientes del hardware): se para cuando
    // el tiempo por ciclo deja de mejorar apreciablemente (el JIT ya compiló), o al llegar al
    // tope de ciclos/tiempo. Exigir varios ciclos PLANOS (no uno solo) cubre que la compilación
    // es asíncrona: el método sigue interpretado/C1 uno o dos ciclos tras cruzar el umbral.
    private static final int MIN_CYCLES = 3;
    private static final int MAX_CYCLES = 25;
    private static final int STABLE_CYCLES = 2;           // ciclos planos consecutivos para parar
    private static final double IMPROVE_RATIO = 0.90;     // "mejora apreciable" = >10% más rápido
    private static final long MAX_NANOS = 4_000_000_000L; // 4 s de tope duro

    private CryptoWarmup() {
    }

    private static byte[] buildDummyScalar() {
        byte[] k = new byte[32];
        for (int i = 0; i < 32; i++) {
            k[i] = (byte) (0x37 + i);
        }
        k[31] &= 0x0f; // byte más significativo -> valor < 2^252 < L, y distinto de cero
        return k;
    }

    private static byte[] buildDummySeed() {
        byte[] s = new byte[48];
        for (int i = 0; i < 48; i++) {
            s[i] = (byte) (0x5a + i);
        }
        return s;
    }

    /**
     * Lanza el calentamiento UNA sola vez, en un hilo de fondo. Idempotente. Nunca lanza:
     * cualquier fallo se traga (el warmup jamás debe romper el arranque).
     */
    public static void warmup() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        Helpers.threadRun(() -> {
            final Thread warmupThread = Thread.currentThread();
            final int warmupPrio = warmupThread.getPriority();
            // Prioridad ligeramente rebajada: el warmup hace ciclos cripto completos que en un PC
            // lento competirían con el EDT pintando la ventana de inicio. NORM-1 (no NORM-2) para
            // que aún alcance C2 pronto. Restaurada en finally (hilo del pool cacheado, reutilizado).
            warmupThread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            try {
                long best = Long.MAX_VALUE;
                int stable = 0;
                long deadline = System.nanoTime() + MAX_NANOS;
                int done = 0;
                for (int i = 0; i < MAX_CYCLES; i++) {
                    long t0 = System.nanoTime();
                    runOneCycle();
                    long dt = System.nanoTime() - t0;
                    done++;
                    if (dt < best * IMPROVE_RATIO) { // aún mejora apreciablemente -> seguir
                        best = Math.min(best, dt);
                        stable = 0;
                    } else {                         // ya no mejora (meseta)
                        best = Math.min(best, dt);
                        if (i >= MIN_CYCLES - 1 && ++stable >= STABLE_CYCLES) {
                            break;
                        }
                    }
                    if (System.nanoTime() > deadline) {
                        break;
                    }
                }
                LOGGER.log(Level.INFO, "Crypto JIT warmup done ({0} cycles, best {1} ms)",
                        new Object[]{done, best == Long.MAX_VALUE ? -1 : (best / 1_000_000)});
            } catch (Throwable t) {
                // El warmup nunca debe romper el arranque.
                LOGGER.log(Level.FINE, "Crypto JIT warmup skipped", t);
            } finally {
                warmupThread.setPriority(warmupPrio);
            }
        });
    }

    /**
     * Un ciclo = las mismas operaciones caras que un paso de cascada real + su prueba y
     * verificación (ejercita {@code Fe25519.mul}, {@code EdwardsPoint.add/dbl}, {@code scalarMul},
     * {@code applyCommutativeLock} y el MSM de las pruebas), sobre datos dummy. Devuelve true si
     * el prove+verify se completó (el camino caliente se recorrió entero). Package-private para el
     * test que verifica que el ciclo hace trabajo REAL (no un no-op silencioso).
     */
    static boolean runOneCycle() {
        byte[] genesis = RistrettoSRA.getGenesisDeck();
        byte[] locked = RistrettoSRA.applyCommutativeLock(genesis, DUMMY_SCALAR); // 52 scalarMul (lock de cascada)
        if (locked == null) {
            return false;
        }
        byte[] shuffled = DeterministicShuffle.shuffleDeck(locked, DUMMY_SEED);
        int[] perm = DeterministicShuffle.shufflePermutation(genesis.length / 32, DUMMY_SEED);
        // shuffled[i] = DUMMY_SCALAR · genesis[perm[i]] (misma construcción que el peer real).
        byte[] proof = ShuffleCascade.proveStepWire(genesis, shuffled, perm, DUMMY_SCALAR); // prove (~el caro)
        boolean verified = false;
        if (proof != null) {
            verified = ShuffleCascade.verifyChainWire(genesis, List.of(genesis, shuffled), List.of(proof)); // verify
        }
        RistrettoSRA.commitment(DUMMY_SCALAR);      // BASE scalarMul
        RistrettoSRA.getUnlockScalar(DUMMY_SCALAR); // modInverse
        return verified;
    }
}

/*
 * B1 (desacople del prove remoto del reparto): test del recolector async de pruebas de barajado
 * Crupier.collectAsyncCascadeProofs. Fija de forma determinista los tres comportamientos que la
 * auditoria adversaria de B1 arreglo, mas el retorno acotado al llamador fail-closed:
 *   1) acepta una prueba VALIDA y la casa con su paso por hash(deckOut);
 *   2) RE-ENCOLA (no se come) un comando que no es de sus pasos (otro builder / otro comando) -> no
 *      deja a otro builder sin sus pruebas (HIGH-1);
 *   3) RECHAZA y cierra al owner que entrega una prueba basura; no admite un retry posterior;
 *   4) liga la prueba al owner autenticado del paso y cierra a quien intente sustituirlo;
 *   5) sin prueba -> mapa incompleto para que el llamador rechace el mazo, sin colgarse.
 *
 * Llama al metodo privado por reflexion. Construye pasos remotos reales con ShuffleCascade.proveStepWire
 * (mismo patron que ShuffleCascadeWireTest). new Crupier() solo inicializa campos (received_commands es
 * final inline), no necesita GameFrame.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.Helpers;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsyncCascadeProofCollectorTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    // ---- helpers de mazo (mismo patron que ShuffleCascadeWireTest) ----
    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    private static byte[] encodeDeck(EdwardsPoint[] deck) {
        byte[] out = new byte[deck.length * 32];
        for (int i = 0; i < deck.length; i++) {
            System.arraycopy(Ristretto255.encode(deck[i]), 0, out, i * 32, 32);
        }
        return out;
    }

    private static EdwardsPoint[] genesisDeck(int n) {
        EdwardsPoint[] a = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            a[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        return a;
    }

    // Replica EXACTA de Crupier.cascadeDeckHash: Base64(SHA-256(deck)).
    private static String cascadeDeckHash(byte[] deck) throws Exception {
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(deck));
    }

    // Un paso REMOTO real (perm null en la lista de perms) con su prueba valida serializada.
    private static final class Step {

        List<byte[]> decks;
        List<int[]> perms;
        String hash;
        byte[] validProof;
    }

    private static Step buildRemoteStep(int n) throws Exception {
        EdwardsPoint[] in = genesisDeck(n);
        int[] perm = DeckTransform.randomPermutation(n);
        byte[] k = RistrettoSRA.generateLockScalar();
        EdwardsPoint[] out = DeckTransform.apply(in, perm, RistrettoSRA.bytesToScalar(k));
        byte[] inB = encodeDeck(in);
        byte[] outB = encodeDeck(out);
        byte[] proof = ShuffleCascade.proveStepWire(inB, outB, perm, k);
        assertNotNull(proof, "proveStepWire de partida no nulo");
        assertTrue(ShuffleCascade.verifyStepWire(inB, outB, proof), "prueba valida de partida");
        Step s = new Step();
        s.decks = new ArrayList<>();
        s.decks.add(inB);
        s.decks.add(outB);
        s.perms = new ArrayList<>();
        s.perms.add(null); // paso remoto: su prueba llega async
        s.hash = cascadeDeckHash(outB);
        s.validProof = proof;
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, byte[]> collect(Crupier c, List<byte[]> decks, List<int[]> perms) throws Exception {
        Method m = Crupier.class.getDeclaredMethod("collectAsyncCascadeProofs", List.class, List.class, List.class);
        m.setAccessible(true);
        return (Map<String, byte[]>) m.invoke(c, decks, perms, java.util.Collections.singletonList("peer"));
    }

    private static String proofCmd(String hash, byte[] proof) {
        return proofCmd("peer", hash, proof);
    }

    private static String proofCmd(String sender, String hash, byte[] proof) {
        return "GAME#1#DECK_CASCADE_PROOF#" + Base64.getEncoder().encodeToString(sender.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "#" + hash + "#" + Base64.getEncoder().encodeToString(proof);
    }

    @Test
    public void validProofIsAcceptedAndMatchedToItsStep() throws Exception {
        Step s = buildRemoteStep(9);
        Crupier c = new Crupier();
        c.getReceived_commands().add(proofCmd(s.hash, s.validProof));
        Map<String, byte[]> got = collect(c, s.decks, s.perms);
        assertEquals(1, got.size(), "una prueba recogida");
        assertArrayEquals(s.validProof, got.get(s.hash), "casada con su paso por hash(deckOut)");
    }

    @Test
    public void foreignCommandIsRequeuedNotEaten() throws Exception {
        Step s = buildRemoteStep(9);
        Crupier c = new Crupier();
        // Comando que NO es de nuestros pasos (hash desconocido, p.ej. de otro builder de una mano
        // solapada). NO debe consumirse: se re-encola. Si el recolector se lo comiera, dejaria a ese
        // otro builder sin su prueba (HIGH-1).
        String foreign = "GAME#7#DECK_CASCADE_PROOF#cGVlcg==#hashDeOtroBuilder#" + Base64.getEncoder().encodeToString(new byte[]{9, 9, 9});
        c.getReceived_commands().add(foreign);
        c.getReceived_commands().add(proofCmd(s.hash, s.validProof));
        Map<String, byte[]> got = collect(c, s.decks, s.perms);
        assertArrayEquals(s.validProof, got.get(s.hash), "la nuestra se acepta");
        assertTrue(c.getReceived_commands().contains(foreign), "el comando ajeno se RE-ENCOLA, no se come");
    }

    @Test
    public void garbageProofFromExpectedOwnerClosesAndCannotBeRetried() throws Exception {
        Step s = buildRemoteStep(9);
        Crupier c = new Crupier();
        // Basura con el hash CORRECTO (casa el hash) pero bytes que NO verifican, encolada ANTES que la
        // buena. El owner esperado que entrega una prueba invalida pierde la conexion y no puede
        // corregirla con un segundo mensaje ya encolado.
        byte[] garbage = new byte[64];
        java.util.Arrays.fill(garbage, (byte) 0x5A);
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        c.enqueueReceivedCommand(proofCmd(s.hash, garbage), () -> closed.set(true));
        c.getReceived_commands().add(proofCmd(s.hash, s.validProof));
        Map<String, byte[]> got = collect(c, s.decks, s.perms);
        assertTrue(got.isEmpty(), "una prueba invalida del contribuyente aborta la recogida");
        assertTrue(closed.get(), "se cierra el origen autenticado de la prueba invalida");
    }

    @Test
    public void proofFromWrongAuthenticatedOwnerIsClosedAndNeverSubstitutesContributor() throws Exception {
        Step s = buildRemoteStep(9);
        Crupier c = new Crupier();
        java.util.concurrent.atomic.AtomicBoolean attackerClosed = new java.util.concurrent.atomic.AtomicBoolean();
        c.enqueueReceivedCommand(proofCmd("attacker", s.hash, s.validProof),
                () -> attackerClosed.set(true));
        c.getReceived_commands().add(proofCmd(s.hash, s.validProof));

        Map<String, byte[]> got = collect(c, s.decks, s.perms);

        assertTrue(attackerClosed.get(), "se cierra la conexion que suplanta al contribuyente");
        assertArrayEquals(s.validProof, got.get(s.hash), "solo se acepta la prueba del owner esperado");
    }

    @Test
    public void missingProofReturnsIncompleteForCallerToFailClosed() throws Exception {
        Step s = buildRemoteStep(9);
        Crupier c = new Crupier();
        c.setFin_de_la_transmision(true); // corta la espera al instante (simula fin de reparto)
        // Sin prueba en la cola, el paso queda sin recoger. El llamador convierte este mapa incompleto
        // en fallo terminal del mazo antes de apostar. Aqui fijamos que el recolector termina acotado.
        Map<String, byte[]> got = collect(c, s.decks, s.perms);
        assertTrue(got.isEmpty(), "sin prueba -> mapa vacio para rechazo fail-closed");
        assertFalse(got.containsKey(s.hash), "el paso remoto queda sin prueba");
    }
}

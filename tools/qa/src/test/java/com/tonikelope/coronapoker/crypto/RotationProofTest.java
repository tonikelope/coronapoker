/*
 * Batch-DLEQ de la rotacion dual-lock: out[i]=s*in[i] mismo s, mismo indice, sin reordenar.
 * Cierra el smuggle de la rotacion. Clave adversaria: relocacion / duplicacion / escalar por-posicion
 * -> rechazados (Schwartz-Zippel sobre el agregado).
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RotationProofTest {

    private static final BigInteger L = EdwardsPoint.L;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    private static EdwardsPoint[] randomRegion(int n) {
        EdwardsPoint[] a = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            a[i] = EdwardsPoint.BASE.scalarMul(scalar());
        }
        return a;
    }

    private static EdwardsPoint[] rekey(EdwardsPoint[] in, BigInteger s) {
        EdwardsPoint[] out = new EdwardsPoint[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i].scalarMul(s.mod(L));
        }
        return out;
    }

    @Test
    public void honestRekeyVerifies() {
        EdwardsPoint[] in = randomRegion(20);
        BigInteger s = scalar();
        EdwardsPoint[] out = rekey(in, s);
        RotationProof.Proof p = RotationProof.prove(s, in, out);
        assertTrue(RotationProof.verify(in, out, p), "out[i]=s*in[i] honesto -> verifica");
    }

    @Test
    public void relocationRejected() {
        // out = s * in permutado (relocacion: el smuggle de la rotacion)
        int n = 20;
        EdwardsPoint[] in = randomRegion(n);
        BigInteger s = scalar();
        EdwardsPoint[] out = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            int src = (i + 5) % n; // permutacion no identidad
            out[i] = in[src].scalarMul(s.mod(L));
        }
        RotationProof.Proof p = RotationProof.prove(s, in, out);
        assertFalse(RotationProof.verify(in, out, p), "[ATAQUE] relocacion (permutacion) -> rechazado");
    }

    @Test
    public void duplicationRejected() {
        // El host duplica: out[1] pasa a ser s*in[0] en vez de s*in[1]
        int n = 16;
        EdwardsPoint[] in = randomRegion(n);
        BigInteger s = scalar();
        EdwardsPoint[] out = rekey(in, s);
        out[1] = in[0].scalarMul(s.mod(L)); // duplicado de la posicion 0
        RotationProof.Proof p = RotationProof.prove(s, in, out);
        assertFalse(RotationProof.verify(in, out, p), "[ATAQUE] duplicado -> rechazado");
    }

    @Test
    public void perPositionScalarRejected() {
        // Escalar distinto por posicion (no es un unico re-key)
        int n = 16;
        EdwardsPoint[] in = randomRegion(n);
        EdwardsPoint[] out = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            out[i] = in[i].scalarMul(scalar().mod(L)); // s_i distinto
        }
        RotationProof.Proof p = RotationProof.prove(scalar(), in, out); // miente un s
        assertFalse(RotationProof.verify(in, out, p), "escalar por-posicion -> rechazado");
    }

    @Test
    public void singlePositionTamperRejected() {
        int n = 18;
        EdwardsPoint[] in = randomRegion(n);
        BigInteger s = scalar();
        EdwardsPoint[] out = rekey(in, s);
        out[7] = out[7].add(EdwardsPoint.BASE); // una posicion alterada
        RotationProof.Proof p = RotationProof.prove(s, in, out);
        assertFalse(RotationProof.verify(in, out, p), "una posicion alterada -> rechazado");
    }

    @Test
    public void tamperedProofRejected() {
        EdwardsPoint[] in = randomRegion(12);
        BigInteger s = scalar();
        EdwardsPoint[] out = rekey(in, s);
        RotationProof.Proof p = RotationProof.prove(s, in, out);
        assertFalse(RotationProof.verify(in, out, new RotationProof.Proof(p.t, p.z.add(BigInteger.ONE))),
                "z manipulado -> rechazado");
        assertFalse(RotationProof.verify(in, out, new RotationProof.Proof(p.t, p.z.add(L))),
                "z+L fuera de rango -> rechazado");
    }

    @Test
    public void identityPositionRejected() {
        EdwardsPoint[] in = randomRegion(10);
        BigInteger s = scalar();
        EdwardsPoint[] out = rekey(in, s);
        out[4] = EdwardsPoint.IDENTITY;
        RotationProof.Proof p = RotationProof.prove(s, in, out);
        assertFalse(RotationProof.verify(in, out, p), "posicion identidad -> rechazado");
    }

    @Test
    public void forgedProverCannotPassRelocation() {
        // Modelo de amenaza REAL: el host recompilado no usa el prove() honesto; emite bytes (t,z)
        // arbitrarios intentando validar una relocacion. Sin resolver el log discreto, no puede.
        int n = 16;
        EdwardsPoint[] in = randomRegion(n);
        BigInteger s = scalar();
        EdwardsPoint[] relocated = new EdwardsPoint[n];
        for (int i = 0; i < n; i++) {
            relocated[i] = in[(i + 3) % n].scalarMul(s.mod(L)); // smuggle por relocacion
        }
        for (int attempt = 0; attempt < 64; attempt++) {
            byte[] forgedT = Ristretto255.encode(EdwardsPoint.BASE.scalarMul(scalar()));
            BigInteger forgedZ = scalar();
            assertFalse(RotationProof.verify(in, relocated, new RotationProof.Proof(forgedT, forgedZ)),
                    "[ATAQUE] proof forjado (t,z arbitrarios) sobre relocacion -> rechazado (intento " + attempt + ")");
        }
        // Tampoco transfiere un proof HONESTO de una rotacion honesta de la misma region de entrada.
        EdwardsPoint[] honestOut = rekey(in, s);
        RotationProof.Proof honest = RotationProof.prove(s, in, honestOut);
        assertFalse(RotationProof.verify(in, relocated, honest),
                "proof honesto de la rotacion honesta NO valida la relocacion");
    }

    @Test
    public void proofForOneRegionDoesNotVerifyAnother() {
        EdwardsPoint[] in = randomRegion(14);
        BigInteger s = scalar();
        EdwardsPoint[] out = rekey(in, s);
        RotationProof.Proof p = RotationProof.prove(s, in, out);
        EdwardsPoint[] inOther = randomRegion(14);
        EdwardsPoint[] outOther = rekey(inOther, s);
        assertFalse(RotationProof.verify(inOther, outOther, p), "prueba de una region no vale para otra");
    }
}

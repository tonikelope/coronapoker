import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.crypto.*;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** Local cost measurement using only correctly constructed bound contributions. */
public final class ProtocolCost {
    static final int CARDS = 52, WARMUP = 5, SAMPLES = 11;
    static final String SESSION = "e04cd1d8-15f8-41fc-8a09-887a73acb8d7";
    static final SecureRandom RANDOM = new SecureRandom();
    static final com.sun.management.OperatingSystemMXBean OS =
        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    static final class Fixture {
        final int n, pockets;
        final ShuffleProofContext context;
        final int[][] permutations;
        final byte[][] shuffleScalarBytes;
        final BigInteger[] rotationScalar;
        final byte[] genesis, rotationStart, finalDeck;
        final List<byte[]> deckBytes = new ArrayList<>(), rotationBytes = new ArrayList<>();

        Fixture(int peers) {
            n = peers;
            pockets = 2 * n;
            String[] ring = new String[n];
            Map<String, BigInteger> alpha = new HashMap<>(), beta = new HashMap<>();
            Map<String, byte[]> pocketKeys = new HashMap<>(), communityKeys = new HashMap<>();
            for (int i = 0; i < n; i++) {
                String name = "P" + i;
                ring[i] = name;
                BigInteger a = scalar(), b = scalar();
                alpha.put(name, a);
                beta.put(name, b);
                pocketKeys.put(name, RistrettoSRA.commitment(RistrettoSRA.scalarToBytes(a)));
                communityKeys.put(name, RistrettoSRA.commitment(RistrettoSRA.scalarToBytes(b)));
            }
            byte[] handId = new byte[16];
            RANDOM.nextBytes(handId);
            String host = ring[n - 1];
            context = new ShuffleProofContext(SESSION, handId, host, ring,
                pocketKeys, communityKeys);
            genesis = Crupier.contextBoundShuffleGenesis(SESSION, handId, ring);
            EdwardsPoint[] deck = ShuffleCascade.decodeDeck(genesis);
            permutations = new int[n][];
            shuffleScalarBytes = new byte[n][];
            rotationScalar = new BigInteger[n];
            for (int i = 0; i < n; i++) {
                BigInteger a = alpha.get(context.shuffleStep(i).participant());
                shuffleScalarBytes[i] = RistrettoSRA.scalarToBytes(a);
                permutations[i] = DeckTransform.randomPermutation(CARDS);
                deck = DeckTransform.apply(deck, permutations[i], a);
                deckBytes.add(DualLockWire.encodeDeck(deck));
            }
            EdwardsPoint[] community = Arrays.copyOfRange(deck, pockets, CARDS);
            rotationStart = DualLockWire.encodeDeck(community);
            for (int i = 0; i < n; i++) {
                String name = context.rotationStep(i).participant();
                rotationScalar[i] = beta.get(name)
                    .multiply(alpha.get(name).modInverse(EdwardsPoint.L)).mod(EdwardsPoint.L);
                EdwardsPoint[] output = new EdwardsPoint[CARDS - pockets];
                for (int j = 0; j < output.length; j++)
                    output[j] = community[j].scalarMul(rotationScalar[i]);
                community = output;
                rotationBytes.add(DualLockWire.encodeDeck(community));
            }
            EdwardsPoint[] result = deck.clone();
            System.arraycopy(community, 0, result, pockets, community.length);
            finalDeck = DualLockWire.encodeDeck(result);
        }
    }

    static final class Bundle {
        final List<byte[]> shuffle = new ArrayList<>(), rotation = new ArrayList<>();

        Bundle(Fixture f) {
            byte[] before = f.genesis;
            for (int i = 0; i < f.n; i++) {
                ShuffleProofContext.Step step = f.context.shuffleStep(i);
                byte[] after = f.deckBytes.get(i);
                byte[] proof = ShuffleCascade.proveBoundStepWire(before, after,
                    f.permutations[i], f.shuffleScalarBytes[i], step, f.context.pocketKey(step));
                if (proof == null) throw new IllegalStateException("Shuffle proof rejected its witness");
                shuffle.add(proof);
                before = after;
            }
            before = f.rotationStart;
            for (int i = 0; i < f.n; i++) {
                ShuffleProofContext.Step step = f.context.rotationStep(i);
                byte[] after = f.rotationBytes.get(i);
                byte[] proof = DualLockWire.proveBoundRotationStepWire(before, after,
                    f.rotationScalar[i], step, f.context.pocketKey(step), f.context.communityKey(step));
                if (proof == null) throw new IllegalStateException("Rotation proof rejected its witness");
                rotation.add(proof);
                before = after;
            }
        }

        boolean verify(Fixture f) {
            return DualLockWire.verifyFullChainWire(f.context, f.genesis,
                f.deckBytes, shuffle, f.finalDeck, f.rotationBytes, rotation);
        }

        int payloadBytes(Fixture f) {
            String command = "DUALLOCK_BUNDLE#" + join(f.deckBytes) + "#" + join(shuffle)
                + "#" + join(f.rotationBytes) + "#" + join(rotation);
            return command.getBytes(StandardCharsets.US_ASCII).length;
        }

        int binaryBytes(Fixture f) {
            int total = 0;
            for (List<byte[]> list : List.of(f.deckBytes, shuffle, f.rotationBytes, rotation))
                for (byte[] item : list) total += item.length;
            return total;
        }
    }

    static String join(List<byte[]> list) {
        StringJoiner output = new StringJoiner(",");
        for (byte[] item : list) output.add(Base64.getEncoder().encodeToString(item));
        return output.toString();
    }

    public static void main(String[] args) {
        if (OS.getProcessCpuTime() < 0)
            throw new IllegalStateException("Process CPU time is unavailable");
        System.out.println("# runtime=" + System.getProperty("java.runtime.version"));
        System.out.println("# vm=" + System.getProperty("java.vm.name"));
        System.out.println("# os=" + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("# visible_processors=" + Runtime.getRuntime().availableProcessors());
        System.out.println("# max_heap=" + Runtime.getRuntime().maxMemory());
        System.out.println("# warmup=" + WARMUP + ",samples=" + SAMPLES + ",cards=" + CARDS);
        System.out.println("# APIs=proveBoundStepWire,proveBoundRotationStepWire,verifyFullChainWire(context,...)");
        System.out.println("# session=" + SESSION + ",hand=fresh-16-random-bytes,host=last-ring-member");
        System.out.println("peers,sample,proof_wall_ms,proof_cpu_ms,verify_wall_ms,verify_cpu_ms,binary_bytes,bundle_command_bytes");
        for (int n : new int[]{2, 4, 6, 10}) {
            for (int run = -WARMUP; run < SAMPLES; run++) {
                Fixture f = new Fixture(n);
                long c0 = OS.getProcessCpuTime(), t0 = System.nanoTime();
                Bundle b = new Bundle(f);
                long t1 = System.nanoTime(), c1 = OS.getProcessCpuTime();
                long c2 = OS.getProcessCpuTime(), t2 = System.nanoTime();
                boolean valid = b.verify(f);
                long t3 = System.nanoTime(), c3 = OS.getProcessCpuTime();
                if (!valid) throw new IllegalStateException("Valid construction rejected at " + n + ":" + run);
                if (run >= 0)
                    System.out.printf(Locale.ROOT, "%d,%d,%.6f,%.6f,%.6f,%.6f,%d,%d%n",
                        n, run, (t1-t0)/1e6, (c1-c0)/1e6, (t3-t2)/1e6, (c3-c2)/1e6,
                        b.binaryBytes(f), b.payloadBytes(f));
            }
        }
    }
}

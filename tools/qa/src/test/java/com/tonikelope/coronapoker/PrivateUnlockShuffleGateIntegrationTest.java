package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.NewGameTableDraft;
import com.tonikelope.coronapoker.core.game.GameAsync;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.game.GameSession;
import com.tonikelope.coronapoker.core.network.ConfirmationTracker;
import com.tonikelope.coronapoker.core.network.GameTransport;
import com.tonikelope.coronapoker.crypto.DealChain;
import com.tonikelope.coronapoker.crypto.DeckTransform;
import com.tonikelope.coronapoker.crypto.DualLockWire;
import com.tonikelope.coronapoker.crypto.EdwardsPoint;
import com.tonikelope.coronapoker.crypto.RistrettoSRA;
import com.tonikelope.coronapoker.crypto.RotationProof;
import com.tonikelope.coronapoker.crypto.ShuffleCascade;
import com.tonikelope.coronapoker.crypto.UnlockChainWire;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateUnlockShuffleGateIntegrationTest {

    private static final String HOST = "host";
    private static final String CLIENT = "client";
    private static final int HAND = 2;
    private static final int POCKET = Crupier.UNLOCK_PHASE_POCKET;
    private static final int POCKET_POINTS = 4;
    private static final GameConfigCodecV1.Configuration CONFIG = GameConfigCodecV1.fromSettings(
            new NewGameTableDraft().snapshot(), false, "private-unlock-gate-test");
    private static Deal deal;

    @BeforeAll
    static void prepareHonestDeal() {
        deal = Deal.create();
    }

    @Test
    void bundleReceiptAndEarlierVerdictDoNotReleaseUnlockButCurrentProofDoes() throws Exception {
        try (Fixture fixture = new Fixture(ShuffleVerificationQueue.DUALLOCK_VERIFIER)) {
            fixture.deliverBundle();
            fixture.requestPocketUnlock();
            assertTrue(fixture.transport.sent.isEmpty());

            // A different envelope with identical deck bytes still belongs to a different hand.
            fixture.verdicts.onVerified(fixture.dealer.local_mega_packet.clone(), HAND - 1);
            assertNull(fixture.dealer.dual_lock_verified_megapacket);
            assertTrue(fixture.transport.sent.isEmpty());

            fixture.releaseVerification.countDown();
            String response = fixture.transport.sent.poll(5, TimeUnit.SECONDS);
            assertNotNull(response);
            String[] fields = response.split("#", -1);
            assertEquals("RESP_SRA_UNLOCK_CHAIN", fields[2]);
            var items = UnlockChainWire.parseResp(fields[4]);
            assertNotNull(items);
            assertEquals(1, items.size());
            assertEquals(0, items.get(0).peerIdx);
            for (int slot = 0; slot < 2; slot++) {
                var chain = DealChain.parse(items.get(0).chains.get(slot));
                byte[] anchor = Arrays.copyOfRange(deal.finalDeck, slot * 32, (slot + 1) * 32);
                assertNotNull(chain);
                assertEquals(1, chain.size());
                assertTrue(DealChain.verify(anchor, chain, fixture.dealer.peer_k_pocket));
                assertArrayEquals(RistrettoSRA.applyCommutativeLock(anchor, deal.clientPocketUnlock),
                        DealChain.tail(anchor, chain));
            }
            fixture.unlockTask.get(5, TimeUnit.SECONDS);
            assertFalse(Crupier.SECURITY_LOCKDOWN);
        }
    }

    @Test
    void rejectedVerificationClosesHostChannelWithoutUnlocking() throws Exception {
        try (Fixture fixture = new Fixture(job -> false)) {
            fixture.deliverBundle();
            fixture.requestPocketUnlock();
            fixture.releaseVerification.countDown();
            assertTrue(fixture.transport.closed.await(5, TimeUnit.SECONDS));
            fixture.unlockTask.get(5, TimeUnit.SECONDS);
            assertTrue(fixture.transport.sent.isEmpty());
        }
    }

    @Test
    void absentBundleTimesOutWithoutUnlocking() throws Exception {
        try (Fixture fixture = new Fixture(ShuffleVerificationQueue.DUALLOCK_VERIFIER)) {
            fixture.requestPocketUnlock();
            fixture.unlockTask.get(Crupier.SHUFFLE_PROOF_GATE_TIMEOUT_MS + 5_000, TimeUnit.MILLISECONDS);
            assertTrue(fixture.transport.closed.await(5, TimeUnit.SECONDS));
            assertTrue(fixture.transport.sent.isEmpty());
        }
    }

    @Test
    void cancellationWakesPendingRequestWithoutUnlocking() throws Exception {
        try (Fixture fixture = new Fixture(ShuffleVerificationQueue.DUALLOCK_VERIFIER)) {
            fixture.deliverBundle();
            fixture.requestPocketUnlock();
            fixture.dealer.setTerminationPending();
            fixture.unlockTask.get(5, TimeUnit.SECONDS);
            assertTrue(fixture.transport.sent.isEmpty());
            assertFalse(Crupier.SECURITY_LOCKDOWN);
        }
    }

    @Test
    void misdealFencesUnlocksBeforeTheDealerProcessesTheRefund() throws Exception {
        try (Fixture fixture = new Fixture(ShuffleVerificationQueue.DUALLOCK_VERIFIER)) {
            fixture.deliverBundle();
            fixture.requestPocketUnlock();
            assertFalse(fixture.dealer.handleClientCriticalHostCommand(
                    "MISDEAL#" + b64("peer.community_unlock_no_testament")));
            fixture.unlockTask.get(5, TimeUnit.SECONDS);
            fixture.verdicts.onVerified(fixture.dealer.local_mega_packet, HAND);
            assertNull(fixture.dealer.dual_lock_verified_megapacket);
            assertTrue(fixture.transport.sent.isEmpty());
            assertFalse(Crupier.SECURITY_LOCKDOWN);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Crupier dealer = new Crupier();
        final RecordingTransport transport = new RecordingTransport();
        final ExecutorService commands = Executors.newSingleThreadExecutor();
        final AtomicReference<Thread> unlockThread = new AtomicReference<>();
        final CountDownLatch verificationStarted = new CountDownLatch(1);
        final CountDownLatch releaseVerification = new CountDownLatch(1);
        final ShuffleVerificationQueue.Sink verdicts;
        Future<?> unlockTask;

        Fixture(ShuffleVerificationQueue.Verifier verifier) throws Exception {
            set(dealer, "game_session", new GameSession(CLIENT, false, CONFIG));
            set(dealer, "game_transport", transport);
            set(dealer, "conta_mano", HAND);
            dealer.local_sra_unlock = deal.clientPocketUnlock;
            // Keep production dispatch and keep a handle on the asynchronously scheduled request.
            set(dealer, "game_async", new GameAsync() {
                @Override public Future<?> execute(Runnable task) {
                    Future<?> result = commands.submit(() -> {
                        unlockThread.compareAndSet(null, Thread.currentThread());
                        task.run();
                    });
                    if (unlockTask == null) unlockTask = result;
                    return result;
                }
                @Override public <T> Future<T> submit(Callable<T> task) { return commands.submit(task); }
                @Override public void pause(long millis) { GameAsync.standalone().pause(millis); }
                @Override public void park(long millis) { GameAsync.standalone().park(millis); }
            });
            assertFalse(dealer.handleClientCriticalHostCommand(deal.megapacket));
            assertNotNull(dealer.local_mega_packet);

            ShuffleVerificationQueue production = dealer.getShuffleVerifyQueue();
            Field sink = ShuffleVerificationQueue.class.getDeclaredField("sink");
            sink.setAccessible(true);
            verdicts = (ShuffleVerificationQueue.Sink) sink.get(production);
            production.shutdown();
            ShuffleVerificationQueue queue = new ShuffleVerificationQueue(verdicts, job -> {
                verificationStarted.countDown();
                try {
                    releaseVerification.await();
                    return verifier.verify(job);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }, 1);
            set(dealer, "shuffle_verify_queue", queue);
            queue.start();
        }

        void deliverBundle() throws InterruptedException {
            assertTrue(dealer.handleClientCriticalHostCommand(deal.bundle));
            assertTrue(verificationStarted.await(5, TimeUnit.SECONDS));
            assertArrayEquals(dealer.local_mega_packet, dealer.dual_lock_bundle_received_for);
        }

        void requestPocketUnlock() throws InterruptedException {
            assertTrue(dealer.handleClientCriticalHostCommand(deal.unlock));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!unlockTask.isDone() && System.nanoTime() < deadline) {
                Thread thread = unlockThread.get();
                if (thread != null && Arrays.stream(thread.getStackTrace()).anyMatch(frame ->
                        frame.getMethodName().equals("awaitShuffleProofGate"))) {
                    return;
                }
                Thread.sleep(10);
            }
            throw new AssertionError("ordinary pocket request must wait in the production proof gate");
        }

        @Override public void close() throws InterruptedException {
            dealer.setTerminationPending();
            dealer.shutdownShuffleVerifyQueue();
            commands.shutdownNow();
            assertTrue(commands.awaitTermination(5, TimeUnit.SECONDS));
            Crupier.SECURITY_LOCKDOWN = false;
        }
    }

    private static final class RecordingTransport implements GameTransport {
        final BlockingQueue<String> sent = new LinkedBlockingQueue<>();
        final CountDownLatch closed = new CountDownLatch(1);
        private final ConfirmationTracker tracker = new ConfirmationTracker();

        @Override public String hostNickname() { return HOST; }
        @Override public String tablePassword() { return null; }
        @Override public boolean gameStarted() { return true; }
        @Override public ConfirmationTracker confirmations() { return tracker; }
        @Override public void sendCommandToHost(String command) { sent.add(command); }
        @Override public void closeHostConnection() { closed.countDown(); }
    }

    private record Deal(String megapacket, String bundle, String unlock,
            byte[] finalDeck, byte[] clientPocketUnlock) {
        static Deal create() {
            String[] ring = {HOST, CLIENT};
            byte[] handId = new byte[CanonicalActionRecord.HAND_ID_BYTES];
            handId[0] = HAND;
            byte[] deck = Crupier.contextBoundShuffleGenesis(CONFIG.sessionId(), handId, ring);
            List<byte[]> outputs = new ArrayList<>();
            List<byte[]> shuffleProofs = new ArrayList<>();
            List<String> commitments = new ArrayList<>();
            List<byte[]> pocketUnlocks = new ArrayList<>();
            List<BigInteger> rotations = new ArrayList<>();
            for (String peer : ring) {
                byte[] pocket = RistrettoSRA.generateLockScalar();
                byte[] community = RistrettoSRA.generateLockScalar();
                byte[] unlock = RistrettoSRA.getUnlockScalar(pocket);
                int[] permutation = DeckTransform.randomPermutation(52);
                byte[] output = DualLockWire.encodeDeck(DeckTransform.apply(
                        ShuffleCascade.decodeDeck(deck), permutation, RistrettoSRA.bytesToScalar(pocket)));
                shuffleProofs.add(ShuffleCascade.proveStepWire(deck, output, permutation, pocket));
                outputs.add(output);
                commitments.add(b64(peer) + ":" + b64(RistrettoSRA.commitment(pocket)) + ":"
                        + b64(RistrettoSRA.commitment(community)));
                pocketUnlocks.add(unlock);
                rotations.add(RistrettoSRA.bytesToScalar(unlock)
                        .multiply(RistrettoSRA.bytesToScalar(community)).mod(EdwardsPoint.L));
                deck = output;
            }

            List<byte[]> rotationStates = new ArrayList<>();
            List<byte[]> rotationProofs = new ArrayList<>();
            byte[] communityDeck = Arrays.copyOfRange(deck, POCKET_POINTS * 32, deck.length);
            for (BigInteger scalar : rotations) {
                EdwardsPoint[] before = ShuffleCascade.decodeDeck(communityDeck);
                EdwardsPoint[] after = Arrays.stream(before).map(point -> point.scalarMul(scalar))
                        .toArray(EdwardsPoint[]::new);
                rotationProofs.add(DualLockWire.encodeRotationProof(RotationProof.prove(scalar, before, after)));
                communityDeck = DualLockWire.encodeDeck(after);
                rotationStates.add(communityDeck);
            }
            byte[] finalDeck = deck.clone();
            System.arraycopy(communityDeck, 0, finalDeck, POCKET_POINTS * 32, communityDeck.length);

            String megapacket = "MEGAPACKET#" + b64(b64(HOST) + "," + b64(CLIENT) + ",")
                    + "#" + b64(finalDeck) + "#" + b64(handId) + "#" + String.join(";", commitments);
            String bundle = "DUALLOCK_BUNDLE#" + join(outputs) + "#" + join(shuffleProofs)
                    + "#" + join(rotationStates) + "#" + join(rotationProofs);
            String unlock = "REQ_SRA_UNLOCK_CHAIN#" + POCKET + "#" + HAND + "#"
                    + UnlockChainWire.serializeReq(List.of(new UnlockChainWire.ReqItem(0, 0, List.of("", ""))));
            return new Deal(megapacket, bundle, unlock, finalDeck, pocketUnlocks.get(1));
        }
    }

    private static String join(List<byte[]> values) {
        return values.stream().map(PrivateUnlockShuffleGateIntegrationTest::b64).collect(Collectors.joining(","));
    }

    private static String b64(String text) {
        return b64(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static void set(Crupier dealer, String name, Object value) throws ReflectiveOperationException {
        Field field = Crupier.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(dealer, value);
    }
}

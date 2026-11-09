package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.GameIdentity;
import com.tonikelope.coronapoker.core.game.GamePeerController;
import com.tonikelope.coronapoker.core.game.GamePlayerController;
import com.tonikelope.coronapoker.core.game.GameSession;
import com.tonikelope.coronapoker.core.identity.GameIdentityProtocol;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.security.KeyPairGenerator;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class CrupierTerminationTailTest {

    @Test
    void naturalClientCompletionPreservesTheAuthenticatedFinalRoster() {
        assertFalse(Crupier.shouldSendLocalExitAtDealerTail(
                false, false, false, false, false));
    }

    @Test
    void explicitlyArmedClientExitStillSendsItsTestament() {
        assertTrue(Crupier.shouldSendLocalExitAtDealerTail(
                false, false, false, false, true));
    }

    @Test
    void voluntaryShutdownAbortsBothHandverifyPhasesBeforeUsingTheTransport()
            throws ReflectiveOperationException {
        Crupier dealer = new Crupier();
        dealer.setTerminationPending();
        for (String name : new String[]{"awaitHandverifyBarrier",
            "waitForHandverifyTrigger", "runSettlementConsensus"}) {
            var method = Crupier.class.getDeclaredMethod(name);
            method.setAccessible(true);
            assertFalse((boolean) method.invoke(dealer), name);
        }
    }

    @Test
    void voluntaryExitDuringLocalReceiptBroadcastDoesNotCreateAnIncident()
            throws Exception {
        Crupier dealer = new Crupier();
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var signedReceipt = new AtomicBoolean();
        var exitDuringBroadcast = new AtomicBoolean();
        setField(dealer, "game_session", new GameSession("host", true));
        setField(dealer, "game_identity", proxy(GameIdentity.class, (p, method, args) -> {
            return switch (method.getName()) {
                case "isReady" -> true;
                case "signReceipt" -> {
                    signedReceipt.set(true);
                    yield GameIdentityProtocol.signReceipt(key.getPrivate(),
                            (byte[]) args[0], (byte[]) args[1], (byte) args[2]);
                }
                default -> throw new AssertionError(method.getName());
            };
        }));
        GamePlayerController host = proxy(GamePlayerController.class, (p, method, args) -> {
            return switch (method.getName()) {
                case "getNickname" -> "host";
                case "getBote", "getPagar" -> 0.3d;
                default -> throw new AssertionError(method.getName());
            };
        });
        setField(dealer, "player_controllers", new ArrayList<>(List.of(host)));
        // A host with bots still enumerates peers when broadcasting its own receipt.
        setField(dealer, "peer_controllers", new AbstractMap<String, GamePeerController>() {
            @Override public Set<Entry<String, GamePeerController>> entrySet() {
                if (signedReceipt.get() && exitDuringBroadcast.compareAndSet(false, true)) {
                    dealer.setTerminationPending();
                    dealer.setFin_de_la_transmision(true);
                }
                return Set.of();
            }
        });
        byte[] verifiedDeck = {1};
        setField(dealer, "local_mega_packet", verifiedDeck);
        setField(dealer, "dual_lock_verified_megapacket", verifiedDeck);
        setField(dealer, "hand_state_chain", HandStateChain.start(
                new byte[CanonicalActionRecord.HAND_ID_BYTES], List.of(new byte[32]),
                List.of(new byte[32]), List.of(new byte[32]), verifiedDeck));

        var consensus = Crupier.class.getDeclaredMethod("runSettlementConsensus");
        consensus.setAccessible(true);
        assertFalse((boolean) consensus.invoke(dealer));
        assertTrue(exitDuringBroadcast.get());
        assertNull(dealer.getTableFailure());
    }

    private static void setField(Crupier dealer, String name, Object value)
            throws ReflectiveOperationException {
        var field = Crupier.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(dealer, value);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[]{type}, handler));
    }

    @Test
    void authoritativeAndRecoveryTeardownsNeverAnswerWithAClientExit() {
        assertFalse(Crupier.shouldSendLocalExitAtDealerTail(
                false, false, true, false, true));
        assertFalse(Crupier.shouldSendLocalExitAtDealerTail(
                false, true, false, false, true));
        assertFalse(Crupier.shouldSendLocalExitAtDealerTail(
                false, false, false, true, true));
        assertFalse(Crupier.shouldSendLocalExitAtDealerTail(
                true, false, false, false, true));
    }
}

package com.tonikelope.coronapoker;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class RecoveryBalanceReconcilerTest {

    @Test
    public void protectedPlayerOmittedByHostIsRejectedAtomically() {
        Map<String, double[]> local = localRows(
                row("alice", 75.25, 100, 1),
                row("bob", 124.75, 100, 0));

        RecoveryBalanceReconciler.Result result = RecoveryBalanceReconciler.reconcile(
                wire(row("bob", 124.75, 100, 0)), local, Set.of("alice", "bob"));

        assertFalse(result.isOk());
        assertEquals(RecoveryBalanceReconciler.Error.MISSING_HOST_PLAYER, result.error());
    }

    @Test
    public void protectedBalanceMismatchIsRejectedInsteadOfPartiallyApplied() {
        Map<String, double[]> local = localRows(row("alice", 75.25, 100, 1));

        RecoveryBalanceReconciler.Result result = RecoveryBalanceReconciler.reconcile(
                wire(row("alice", 75.24, 100, 1)), local, Set.of("alice"));

        assertFalse(result.isOk());
        assertEquals(RecoveryBalanceReconciler.Error.BALANCE_MISMATCH, result.error());
    }

    @Test
    public void missingLocalSnapshotForProtectedPlayerNeverFallsBackToHost() {
        RecoveryBalanceReconciler.Result result = RecoveryBalanceReconciler.reconcile(
                wire(row("alice", 75.25, 100, 1)), Map.of(), Set.of("alice"));

        assertFalse(result.isOk());
        assertEquals(RecoveryBalanceReconciler.Error.MISSING_LOCAL_PLAYER, result.error());
    }

    @Test
    public void passiveObserverCanUseACompleteCanonicalHostSnapshot() {
        RecoveryBalanceReconciler.Result result = RecoveryBalanceReconciler.reconcile(
                wire(row("alice", 75.25, 100, 1), row("bob", 124.75, 100, 0)),
                Map.of(), Set.of());

        assertTrue(result.isOk());
        assertEquals(7525L, result.balances().get("alice").stack().cents());
        assertEquals(1, result.balances().get("alice").rebuyCount().value());
    }

    @Test
    public void duplicateOrOutOfDomainRowsAreRejectedBeforeApply() {
        String alice = b64("alice");
        String duplicate = alice + "|1.00|100|0@" + alice + "|2.00|100|0";
        String excessiveRebuys = alice + "|1.00|100|" + (BuyinCount.MAX_VALUE + 1);

        assertEquals(RecoveryBalanceReconciler.Error.DUPLICATE_PLAYER,
                RecoveryBalanceReconciler.reconcile(duplicate, Map.of(), Set.of()).error());
        assertEquals(RecoveryBalanceReconciler.Error.BAD_VALUE,
                RecoveryBalanceReconciler.reconcile(excessiveRebuys, Map.of(), Set.of()).error());
    }

    @Test
    public void participantRequiresTheExactLocallyPersistedBalanceRoster() {
        Map<String, double[]> local = localRows(row("alice", 75.25, 100, 1));

        RecoveryBalanceReconciler.Result result = RecoveryBalanceReconciler.reconcileExact(
                wire(row("alice", 75.25, 100, 1), row("mallory", 500.00, 500, 0)), local);

        assertFalse(result.isOk());
        assertEquals(RecoveryBalanceReconciler.Error.MISSING_LOCAL_PLAYER, result.error());
    }

    @Test
    public void emptyParticipantSnapshotCannotDegenerateIntoObserverTrust() {
        RecoveryBalanceReconciler.Result result = RecoveryBalanceReconciler.reconcileExact(
                wire(row("alice", 75.25, 100, 1)), Map.of());

        assertFalse(result.isOk());
        assertEquals(RecoveryBalanceReconciler.Error.MISSING_LOCAL_PLAYER, result.error());
    }

    @Test
    public void locallyOpenHandBindsIdentityAndRosterIndependentlyOfSafeRefundClosure() {
        Set<String> localRoster = Set.of("alice", "bob");
        assertTrue(RecoveryBalanceReconciler.sameHandIdentityAndRoster(
                "hand-a", localRoster, "hand-a", Set.of("bob", "alice")));
        assertFalse(RecoveryBalanceReconciler.sameHandIdentityAndRoster(
                "hand-a", localRoster, "hand-b", localRoster));
        assertFalse(RecoveryBalanceReconciler.sameHandIdentityAndRoster(
                "hand-a", localRoster, "hand-a", Set.of("alice")));

        // A crashed peer still sees its row as open after the host has safely
        // closed/refunded the hand. Closure is accepted only after the caller's
        // exact opening-balance reconciliation; identity and roster stay bound.
        assertTrue(RecoveryBalanceReconciler.reconcileExact(
                wire(row("alice", 75.25, 100, 1), row("bob", 124.75, 100, 0)),
                localRows(row("alice", 75.25, 100, 1),
                        row("bob", 124.75, 100, 0))).isOk());
        assertFalse(RecoveryBalanceReconciler.reconcileExact(
                wire(row("alice", 75.24, 100, 1), row("bob", 124.76, 100, 0)),
                localRows(row("alice", 75.25, 100, 1),
                        row("bob", 124.75, 100, 0))).isOk());
    }

    @Test
    public void passiveObserverCannotBeConscriptedIntoAnOpenHostHand() {
        assertFalse(RecoveryBalanceReconciler.passiveObserverContextIsSafe(
                0L, Set.of("alice", "bob"), "alice"));
        assertTrue(RecoveryBalanceReconciler.passiveObserverContextIsSafe(
                0L, Set.of("alice", "bob"), "carol"));
        assertTrue(RecoveryBalanceReconciler.passiveObserverContextIsSafe(
                123L, Set.of("alice", "bob"), "alice"));
    }

    private static Object[] row(String nick, double stack, int buyin, int rebuy) {
        return new Object[]{nick, stack, buyin, rebuy};
    }

    private static Map<String, double[]> localRows(Object[]... rows) {
        Map<String, double[]> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], new double[]{
                (double) row[1], (int) row[2], (int) row[3]
            });
        }
        return result;
    }

    private static String wire(Object[]... rows) {
        StringBuilder result = new StringBuilder();
        for (Object[] row : rows) {
            if (result.length() > 0) {
                result.append('@');
            }
            result.append(b64((String) row[0])).append('|')
                    .append(row[1]).append('|').append(row[2]).append('|').append(row[3]);
        }
        return result.toString();
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}

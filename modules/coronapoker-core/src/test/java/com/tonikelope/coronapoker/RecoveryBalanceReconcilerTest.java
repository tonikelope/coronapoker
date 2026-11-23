/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RecoveryBalanceReconcilerTest {

    @Test
    void derivesInheritedRemainderFromAuthenticatedBoundary() {
        Map<String, double[]> rows = new LinkedHashMap<>();
        rows.put("Hannibal Smith$", row(46.3, 10, 0));
        rows.put("Robocop$", row(9.3, 10, 0));
        rows.put("Snoopy$", row(10, 20, 1));
        rows.put("T-1000$", row(4, 10, 0));
        rows.put("T-800$", row(4, 20, 1));
        rows.put("portatil", row(10, 10, 0));
        rows.put("tonikelop3", row(16.3, 20, 1));

        RecoveryBalanceReconciler.Result snapshot
                = RecoveryBalanceReconciler.parseObserver(
                        RecoveryBalanceReconciler.encodeLocal(rows),
                        rows.keySet());

        assertEquals(10L,
                RecoveryBalanceReconciler.unallocatedCents(snapshot));
    }

    @Test
    void derivesZeroWhenEveryPurchasedChipIsInAStack() {
        Map<String, double[]> rows = Map.of(
                "host", row(23.25, 20, 1),
                "client", row(6.75, 10, 0),
                "bot$", row(10, 10, 0));

        RecoveryBalanceReconciler.Result snapshot
                = RecoveryBalanceReconciler.parseObserver(
                        RecoveryBalanceReconciler.encodeLocal(rows),
                        rows.keySet());

        assertEquals(0L,
                RecoveryBalanceReconciler.unallocatedCents(snapshot));
    }

    @Test
    void rejectsSnapshotWhoseStacksExceedAllBuyins() {
        Map<String, double[]> rows = Map.of(
                "host", row(10.01, 10, 0));
        RecoveryBalanceReconciler.Result snapshot
                = RecoveryBalanceReconciler.parseObserver(
                        RecoveryBalanceReconciler.encodeLocal(rows),
                        rows.keySet());

        assertThrows(IllegalArgumentException.class,
                () -> RecoveryBalanceReconciler.unallocatedCents(snapshot));
    }

    @Test
    void fractionalBuyinSurvivesCanonicalRecoveryBalances() {
        var rows = Map.of("alice", new double[]{10d, 15.70d, 1d},
                "bob", new double[]{5.70d, 0d, 0d});
        String wire = RecoveryBalanceReconciler.encodeLocal(rows);
        var result = RecoveryBalanceReconciler.reconcileExact(wire, rows);
        assertEquals(true, result.isOk());
        assertEquals(15.70d, result.balances().get("alice").buyin());
        assertEquals(0L, RecoveryBalanceReconciler.unallocatedCents(result));
        assertEquals(wire, RecoveryBalanceReconciler.encode(result));
    }

    private static double[] row(double stack, int buyin, int rebuyCount) {
        return new double[]{stack, buyin, rebuyCount};
    }
}

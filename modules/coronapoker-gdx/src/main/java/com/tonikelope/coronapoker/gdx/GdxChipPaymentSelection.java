/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/** Chooses the existing physical chips that pay an amount or make the least change. */
final class GdxChipPaymentSelection {
    private static final int MAX_DENSE_SUM = 65_536;
    private static final Pick EMPTY = new Pick(-1, 0, null);

    private GdxChipPaymentSelection() {
    }

    static <T> Choice find(List<T> chips, ToLongFunction<T> value, long target) {
        long[] amounts = new long[chips.size()];
        long total = 0L;
        long largest = 0L;
        long unit = 0L;
        for (int i = 0; i < amounts.length; i++) {
            long amount = value.applyAsLong(chips.get(i));
            amounts[i] = amount;
            total = addCapped(total, amount);
            largest = Math.max(largest, amount);
            unit = gcd(unit, amount);
        }
        if (total < target || unit == 0L) return null;
        if (total == target && total != Long.MAX_VALUE) {
            Pick all = EMPTY;
            for (int i = 0; i < amounts.length; i++) {
                if (amounts[i] != 0L) all = new Pick(i, all.count() + 1, all);
            }
            return new Choice(all, true);
        }
        // The first crossing of target in any sufficient subset is at most
        // one chip past it. Larger partial sums cannot win either selection.
        long max = Math.min(total, addCapped(target, largest - 1L));
        if (max / unit <= MAX_DENSE_SUM) {
            return dense(amounts, target, unit, (int) (max / unit));
        }
        return sparse(amounts, target, max);
    }

    private static Choice dense(long[] amounts, long target, long unit, int max) {
        Pick[] best = new Pick[max + 1];
        best[0] = EMPTY;
        int reachable = 0;
        for (int i = 0; i < amounts.length; i++) {
            long scaled = amounts[i] / unit;
            if (scaled == 0L || scaled > max) continue;
            int amount = (int) scaled;
            // Descending sums never reuse the current chip. Keeping the first
            // equal-count pick preserves the original chip identities.
            for (int sum = Math.min(reachable, max - amount); sum >= 0; sum--) {
                Pick prior = best[sum];
                if (prior == null) continue;
                Pick current = best[sum + amount];
                if (current == null || prior.count() + 1 < current.count()) {
                    best[sum + amount] = new Pick(i, prior.count() + 1, prior);
                }
            }
            reachable = Math.min(max, reachable + amount);
        }
        int first = (int) (target / unit);
        if (target % unit != 0L) first++;
        for (int sum = first; sum <= reachable; sum++) {
            if (best[sum] != null) return new Choice(best[sum], sum * unit == target);
        }
        return null;
    }

    private static Choice sparse(long[] amounts, long target, long max) {
        Map<Long, Pick> best = new HashMap<>();
        best.put(0L, EMPTY);
        for (int i = 0; i < amounts.length; i++) {
            long amount = amounts[i];
            if (amount == 0L || amount > max) continue;
            Map<Long, Pick> next = new HashMap<>(best);
            for (var entry : best.entrySet()) {
                if (entry.getKey() > max - amount) continue;
                long sum = entry.getKey() + amount;
                Pick prior = entry.getValue();
                Pick current = next.get(sum);
                if (current == null || prior.count() + 1 < current.count()) {
                    next.put(sum, new Pick(i, prior.count() + 1, prior));
                }
            }
            best = next;
        }
        long smallest = max;
        Pick selection = null;
        for (var entry : best.entrySet()) {
            long amount = entry.getKey();
            if (amount >= target && amount <= smallest) {
                smallest = amount;
                selection = entry.getValue();
            }
        }
        return selection == null ? null : new Choice(selection, smallest == target);
    }

    private static long gcd(long a, long b) {
        while (b != 0L) {
            long remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }

    private static long addCapped(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    record Choice(Pick pick, boolean exact) { }

    record Pick(int index, int count, Pick previous) { }
}

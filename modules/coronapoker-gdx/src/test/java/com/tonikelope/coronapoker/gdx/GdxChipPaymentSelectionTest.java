/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxChipPaymentSelectionTest {
    @Test
    void keepsTheFirstPhysicalChipWhenEqualValueAndCountCanPay() {
        for (long base : List.of(5L, 70_000L)) {
            var choice = GdxChipPaymentSelection.find(
                    List.of(1L, base, base, base + 1L), Long::longValue, base);

            assertTrue(choice.exact());
            assertEquals(1, choice.pick().count());
            assertEquals(1, choice.pick().index());
        }
    }

    @Test
    void prefersLessChangeEvenWhenItRequiresMoreChips() {
        var choice = GdxChipPaymentSelection.find(
                List.of(4L, 5L, 10L), Long::longValue, 8L);

        assertFalse(choice.exact());
        assertEquals(2, choice.pick().count());
        assertEquals(1, choice.pick().index());
        assertEquals(0, choice.pick().previous().index());
    }

    @Test
    void neverUsesAChipTwiceOrDiscardsTheOnlyOverpayment() {
        var choice = GdxChipPaymentSelection.find(
                List.of(3L, 9L), Long::longValue, 6L);
        assertFalse(choice.exact());
        assertEquals(1, choice.pick().index());
        assertNull(GdxChipPaymentSelection.find(
                List.of(3L, 9L), Long::longValue, 13L));
    }
}

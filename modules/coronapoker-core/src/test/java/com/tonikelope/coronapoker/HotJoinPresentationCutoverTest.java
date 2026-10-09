package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class HotJoinPresentationCutoverTest {

    @Test
    void dropsEveryPreSnapshotEventAndKeepsOnlyTheLiveTail() {
        HotJoinPresentationCutover<String> cutover
                = new HotJoinPresentationCutover<>();
        assertTrue(cutover.accept(1L, "deal-flop-0").isEmpty());
        assertTrue(cutover.accept(2L, "deal-flop-1").isEmpty());

        cutover.beginSnapshot(2L);
        assertTrue(cutover.accept(3L, "reveal-turn").isEmpty());

        assertEquals(List.of("reveal-turn"), cutover.completeSnapshot());
        assertTrue(cutover.accept(2L, "late-stale-flop").isEmpty());
        assertEquals("reveal-river",
                cutover.accept(4L, "reveal-river").orElseThrow());
    }

    @Test
    void eachReentryOwnsAFreshIndependentCutover() {
        HotJoinPresentationCutover<String> first
                = new HotJoinPresentationCutover<>();
        first.beginSnapshot(8L);
        assertEquals(List.of(), first.completeSnapshot());

        HotJoinPresentationCutover<String> second
                = new HotJoinPresentationCutover<>();
        assertTrue(second.accept(9L, "old-deal").isEmpty());
        second.beginSnapshot(9L);
        assertTrue(second.accept(10L, "new-action").isEmpty());
        assertEquals(List.of("new-action"), second.completeSnapshot());
    }

    @Test
    void rejectsInvalidOrRepeatedLifecycleTransitions() {
        HotJoinPresentationCutover<String> cutover
                = new HotJoinPresentationCutover<>();
        assertThrows(IllegalArgumentException.class,
                () -> cutover.accept(0L, "invalid"));
        assertThrows(IllegalStateException.class, cutover::completeSnapshot);
        cutover.beginSnapshot(0L);
        assertThrows(IllegalStateException.class,
                () -> cutover.beginSnapshot(0L));
        cutover.completeSnapshot();
        assertThrows(IllegalStateException.class, cutover::completeSnapshot);
    }
}

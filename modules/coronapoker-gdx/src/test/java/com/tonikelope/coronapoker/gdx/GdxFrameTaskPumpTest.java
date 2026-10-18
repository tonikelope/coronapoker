/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GdxFrameTaskPumpTest {

    @Test
    void denseReplayIsSpreadAcrossFramesWithoutReordering() {
        ArrayDeque<Runnable> frames = new ArrayDeque<>();
        AtomicLong clock = new AtomicLong();
        List<Integer> applied = new ArrayList<>();
        GdxFrameTaskPump pump = new GdxFrameTaskPump(frames::addLast,
                clock::get, 3, 2_000_000L);

        for (int event = 0; event < 8; event++) {
            int orderedEvent = event;
            pump.submit(() -> applied.add(orderedEvent), failure -> { });
        }

        assertEquals(1, frames.size(), "one render callback owns the batch");
        frames.removeFirst().run();
        assertEquals(List.of(0, 1, 2), applied);
        assertEquals(1, frames.size(), "the remainder waits for another frame");
        frames.removeFirst().run();
        assertEquals(List.of(0, 1, 2, 3, 4, 5), applied);
        frames.removeFirst().run();
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), applied);
        assertTrue(frames.isEmpty());
    }

    @Test
    void elapsedBudgetYieldsEvenBeforeTheCountLimit() {
        ArrayDeque<Runnable> frames = new ArrayDeque<>();
        AtomicLong clock = new AtomicLong();
        List<Integer> applied = new ArrayList<>();
        GdxFrameTaskPump pump = new GdxFrameTaskPump(frames::addLast,
                clock::get, 20, 2_000_000L);
        pump.submit(() -> {
            applied.add(1);
            clock.addAndGet(2_100_000L);
        }, failure -> { });
        pump.submit(() -> applied.add(2), failure -> { });

        frames.removeFirst().run();
        assertEquals(List.of(1), applied);
        assertEquals(1, frames.size());
        frames.removeFirst().run();
        assertEquals(List.of(1, 2), applied);
    }

    @Test
    void closeRejectsEveryQueuedTask() {
        ArrayDeque<Runnable> frames = new ArrayDeque<>();
        List<Throwable> failures = new ArrayList<>();
        GdxFrameTaskPump pump = new GdxFrameTaskPump(frames::addLast,
                System::nanoTime, 3, 2_000_000L);
        pump.submit(() -> { }, failures::add);
        pump.submit(() -> { }, failures::add);

        pump.close();

        assertEquals(2, failures.size());
        failures.forEach(failure -> assertInstanceOf(
                IllegalStateException.class, failure));
        AtomicReference<Throwable> lateFailure = new AtomicReference<>();
        pump.submit(() -> { }, lateFailure::set);
        assertInstanceOf(IllegalStateException.class, lateFailure.get());
    }
}

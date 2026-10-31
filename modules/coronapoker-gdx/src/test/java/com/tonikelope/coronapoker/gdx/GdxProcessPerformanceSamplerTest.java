/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class GdxProcessPerformanceSamplerTest {

    @Test
    void renderThreadOnlyQueuesSamplingAndReturnsCachedDataImmediately() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicInteger probeCalls = new AtomicInteger();
        GdxProcessPerformanceSampler sampler =
                new GdxProcessPerformanceSampler(queued::set, () -> {
                    probeCalls.incrementAndGet();
                    return new GdxProcessPerformanceSampler.Sample(27, 48L);
                });

        String initial = sampler.label(new GdxGameText("es"), 1L);

        assertEquals(0, probeCalls.get(),
                "the caller must never perform platform sampling");
        assertTrue(initial.contains("CPU: --% | MEMORIA: 0 MB"));
        assertTrue(sampler.samplePending());
        assertNotNull(queued.get());

        queued.get().run();

        assertEquals(1, probeCalls.get());
        assertFalse(sampler.samplePending());
        assertTrue(sampler.label(new GdxGameText("es"), 2L)
                .contains("CPU: 27% | MEMORIA: 48 MB"));
    }

    @Test
    void optionalProbeFailureNeverEscapesIntoTheGameLoop() {
        GdxProcessPerformanceSampler sampler =
                new GdxProcessPerformanceSampler(Runnable::run, () -> {
                    throw new IllegalStateException("platform probe failed");
                });

        String label = assertDoesNotThrow(() -> sampler.label(
                new GdxGameText("en"), 1L));

        assertTrue(label.contains("CPU: --% | MEMORY: 0 MB"));
        assertFalse(sampler.samplePending());
    }

    @Test
    void pendingSampleCoalescesEveryRenderRequestIntoOneTask() {
        AtomicInteger queued = new AtomicInteger();
        GdxProcessPerformanceSampler sampler =
                new GdxProcessPerformanceSampler(task -> {
                    queued.incrementAndGet();
                    // Deliberately leave the sample pending.
                }, () -> new GdxProcessPerformanceSampler.Sample(1, 1L));

        for (int frame = 0; frame < 120; frame++) {
            sampler.label(new GdxGameText("en"),
                    frame * GdxProcessPerformanceSampler.SAMPLE_INTERVAL_NANOS);
        }

        assertEquals(1, queued.get(),
                "a stalled platform query must not grow an executor queue");
    }

    @Test
    void rejectedBackgroundTaskNeverEscapesIntoTheGameLoop() {
        GdxProcessPerformanceSampler sampler =
                new GdxProcessPerformanceSampler(task -> {
                    throw new IllegalStateException("executor unavailable");
                }, () -> new GdxProcessPerformanceSampler.Sample(1, 1L));

        assertDoesNotThrow(() -> sampler.label(new GdxGameText("en"), 1L));
        assertFalse(sampler.samplePending());
    }

    @Test
    void cpuLoadIsNormalizedAcrossAvailableProcessors() {
        assertEquals(25, GdxProcessPerformanceSampler.normalizedCpuPercent(
                3_000_000_000L, 1_000_000_000L,
                4_000_000_000L, 2_000_000_000L, 4));
        assertEquals(-1, GdxProcessPerformanceSampler.normalizedCpuPercent(
                -1L, 0L, 2L, 1L, 4));
    }

    @Test
    void standardProcessDurationIsConvertedWithoutManagementMxBeans() {
        assertEquals(2_000_000_000L,
                GdxProcessPerformanceSampler.processCpuNanos(
                        () -> Optional.of(Duration.ofSeconds(2L))));
        assertEquals(-1L, GdxProcessPerformanceSampler.processCpuNanos(
                Optional::empty));
    }

    @Test
    void formattedSnapshotUsesNoMiddleDotSeparator() {
        GdxProcessPerformanceSampler sampler =
                new GdxProcessPerformanceSampler(Runnable::run,
                        () -> new GdxProcessPerformanceSampler.Sample(12, 2L));

        String spanish = sampler.label(new GdxGameText("es"), 1L);

        assertTrue(spanish.startsWith("CPU: 12%"));
        assertTrue(spanish.contains(" | MEMORIA: 2 MB"));
        assertFalse(spanish.contains("·"));
    }
}

/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;

final class GdxProcessPerformanceSamplerTest {

    @Test
    void managedMemoryIncludesHeapNonHeapAndDirectBuffersExactly() {
        MemoryMXBean memory = memory(20L, 7L);
        BufferPoolMXBean direct = buffer(5L);

        assertEquals(32L, GdxProcessPerformanceSampler.managedMemoryBytes(
                memory, List.of(direct)));
    }

    @Test
    void formattedSnapshotIsLocalizedAndUsesNoMiddleDotSeparator() {
        GdxProcessPerformanceSampler sampler =
                new GdxProcessPerformanceSampler(
                        ManagementFactory.getOperatingSystemMXBean(),
                        memory(2L * 1024L * 1024L, 0L), List.of());

        String spanish = sampler.label(new GdxGameText("es"), 1L);

        assertTrue(spanish.startsWith("CPU: "));
        assertTrue(spanish.contains(" | MEMORIA: 2 MB"));
        assertTrue(!spanish.contains("·"));
    }

    private static MemoryMXBean memory(long heap, long nonHeap) {
        return new MemoryMXBean() {
            @Override public int getObjectPendingFinalizationCount() {
                return 0;
            }
            @Override public MemoryUsage getHeapMemoryUsage() {
                return usage(heap);
            }
            @Override public MemoryUsage getNonHeapMemoryUsage() {
                return usage(nonHeap);
            }
            @Override public boolean isVerbose() { return false; }
            @Override public void setVerbose(boolean value) { }
            @Override public void gc() { }
            @Override public ObjectName getObjectName() {
                return objectName("java.lang:type=Memory");
            }
        };
    }

    private static BufferPoolMXBean buffer(long bytes) {
        return new BufferPoolMXBean() {
            @Override public String getName() { return "test"; }
            @Override public long getCount() { return 1L; }
            @Override public long getTotalCapacity() { return bytes; }
            @Override public long getMemoryUsed() { return bytes; }
            @Override public ObjectName getObjectName() {
                return objectName("java.nio:type=BufferPool,name=test");
            }
        };
    }

    private static MemoryUsage usage(long used) {
        return new MemoryUsage(0L, used, used, Long.MAX_VALUE);
    }

    private static ObjectName objectName(String value) {
        try {
            return new ObjectName(value);
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }
}

/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.util.List;

/**
 * Low-overhead process telemetry for the optional on-screen debug overlay.
 * Values are sampled at most once per second so enabling the meter does not
 * materially change the load it reports.
 */
final class GdxProcessPerformanceSampler {

    static final long SAMPLE_INTERVAL_NANOS = 1_000_000_000L;
    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024L;

    private final OperatingSystemMXBean operatingSystem;
    private final MemoryMXBean memory;
    private final List<BufferPoolMXBean> bufferPools;
    private long lastSampleAt = Long.MIN_VALUE;
    private int cpuPercent = -1;
    private long managedMemoryMebibytes;

    GdxProcessPerformanceSampler() {
        this(ManagementFactory.getOperatingSystemMXBean(),
                ManagementFactory.getMemoryMXBean(),
                ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class));
    }

    GdxProcessPerformanceSampler(OperatingSystemMXBean operatingSystem,
            MemoryMXBean memory, List<BufferPoolMXBean> bufferPools) {
        this.operatingSystem = operatingSystem;
        this.memory = memory;
        this.bufferPools = bufferPools == null ? List.of()
                : List.copyOf(bufferPools);
    }

    String label(GdxGameText text, long nowNanos) {
        sampleIfDue(nowNanos);
        String cpu = cpuPercent < 0 ? "--" : Integer.toString(cpuPercent);
        return text.translate("gdx.performance.overlay", cpu,
                managedMemoryMebibytes);
    }

    int cpuPercent() {
        return cpuPercent;
    }

    long managedMemoryMebibytes() {
        return managedMemoryMebibytes;
    }

    private void sampleIfDue(long nowNanos) {
        if (lastSampleAt != Long.MIN_VALUE
                && nowNanos - lastSampleAt < SAMPLE_INTERVAL_NANOS) return;
        lastSampleAt = nowNanos;
        cpuPercent = processCpuPercent(operatingSystem);
        managedMemoryMebibytes = bytesToMebibytes(managedMemoryBytes(memory,
                bufferPools));
    }

    static int processCpuPercent(OperatingSystemMXBean bean) {
        if (!(bean instanceof com.sun.management.OperatingSystemMXBean os)) {
            return -1;
        }
        double load = os.getProcessCpuLoad();
        if (!Double.isFinite(load) || load < 0d) return -1;
        return (int) Math.round(Math.max(0d, Math.min(1d, load)) * 100d);
    }

    static long managedMemoryBytes(MemoryMXBean memory,
            List<BufferPoolMXBean> bufferPools) {
        long total = 0L;
        if (memory != null) {
            total = saturatingAdd(total, used(memory.getHeapMemoryUsage()));
            total = saturatingAdd(total,
                    used(memory.getNonHeapMemoryUsage()));
        }
        if (bufferPools != null) {
            for (BufferPoolMXBean pool : bufferPools) {
                if (pool != null) {
                    total = saturatingAdd(total,
                            Math.max(0L, pool.getMemoryUsed()));
                }
            }
        }
        return total;
    }

    private static long used(MemoryUsage usage) {
        return usage == null ? 0L : Math.max(0L, usage.getUsed());
    }

    private static long bytesToMebibytes(long bytes) {
        if (bytes <= 0L) return 0L;
        long rounding = BYTES_PER_MEBIBYTE / 2L;
        long rounded = bytes > Long.MAX_VALUE - rounding
                ? Long.MAX_VALUE : bytes + rounding;
        return Math.max(1L, rounded
                / BYTES_PER_MEBIBYTE);
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) return left;
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }
}

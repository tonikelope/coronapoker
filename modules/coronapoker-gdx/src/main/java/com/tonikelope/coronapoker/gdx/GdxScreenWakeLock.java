/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.mac.CoreFoundation;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.ptr.IntByReference;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds the operating system's native idle inhibitor while GDX runs windowed
 * or borderless. Exclusive fullscreen delegates that responsibility to GLFW.
 * It never synthesizes keyboard or mouse input.
 */
final class GdxScreenWakeLock implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(
            GdxScreenWakeLock.class.getName());
    static final int REQUIRED_FLAGS = WinBase.ES_CONTINUOUS
            | WinBase.ES_DISPLAY_REQUIRED | WinBase.ES_SYSTEM_REQUIRED;
    static final int RELEASE_FLAGS = WinBase.ES_CONTINUOUS;
    private static final int MAC_ASSERTION_LEVEL_ON = 255;
    private static final int MAC_SUCCESS = 0;
    private static final String MAC_PREVENT_DISPLAY_SLEEP =
            "PreventUserIdleDisplaySleep";
    private static final String MAC_PREVENT_SYSTEM_SLEEP =
            "PreventUserIdleSystemSleep";

    @FunctionalInterface
    interface ExecutionStateApi {

        int set(int flags);
    }

    interface MacPowerApi {

        int acquire(String assertionType, String reason,
                IntByReference assertionId);

        int release(int assertionId);
    }

    @FunctionalInterface
    interface ProcessStarter {

        Process start(List<String> command) throws IOException;
    }

    private interface Backend {

        boolean acquire() throws Exception;

        boolean release() throws Exception;

        String description();
    }

    private final Backend backend;
    private boolean held;
    private long ownerThreadId = -1L;

    private GdxScreenWakeLock(Backend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    static GdxScreenWakeLock forCurrentPlatform() {
        String os = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return windows(flags -> Kernel32.INSTANCE
                    .SetThreadExecutionState(flags));
        }
        if (os.startsWith("mac") || os.startsWith("darwin")) {
            return mac(MacNative::acquire, MacNative::release);
        }
        if (os.startsWith("linux")) {
            return linux(command -> new ProcessBuilder(command)
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start());
        }
        return new GdxScreenWakeLock(new UnsupportedBackend(os));
    }

    static GdxScreenWakeLock windowsForTesting(ExecutionStateApi api) {
        return windows(api);
    }

    static GdxScreenWakeLock macForTesting(MacPowerApi api) {
        return mac(api::acquire, api::release);
    }

    static GdxScreenWakeLock unsupportedForTesting() {
        return new GdxScreenWakeLock(new UnsupportedBackend("test"));
    }

    static List<String> linuxInhibitorCommand(long processId) {
        return List.of("systemd-inhibit", "--what=idle:sleep",
                "--mode=block", "--who=CoronaPoker",
                "--why=Poker game in progress", "tail",
                "--pid=" + processId, "-f", "/dev/null");
    }

    private static GdxScreenWakeLock windows(ExecutionStateApi api) {
        Objects.requireNonNull(api, "api");
        return new GdxScreenWakeLock(new WindowsBackend(api));
    }

    private static GdxScreenWakeLock mac(MacAcquire acquire,
            MacRelease release) {
        return new GdxScreenWakeLock(new Backend() {
            private int displayAssertionId;
            private int systemAssertionId;

            @Override public boolean acquire() {
                IntByReference displayId = new IntByReference();
                if (acquire.call(MAC_PREVENT_DISPLAY_SLEEP,
                        "CoronaPoker game in progress", displayId)
                        != MAC_SUCCESS) {
                    return false;
                }
                displayAssertionId = displayId.getValue();
                IntByReference systemId = new IntByReference();
                if (acquire.call(MAC_PREVENT_SYSTEM_SLEEP,
                        "CoronaPoker game in progress", systemId)
                        != MAC_SUCCESS) {
                    release.call(displayAssertionId);
                    displayAssertionId = 0;
                    return false;
                }
                systemAssertionId = systemId.getValue();
                return true;
            }

            @Override public boolean release() {
                boolean systemReleased = release.call(systemAssertionId)
                        == MAC_SUCCESS;
                boolean displayReleased = release.call(displayAssertionId)
                        == MAC_SUCCESS;
                systemAssertionId = 0;
                displayAssertionId = 0;
                return systemReleased && displayReleased;
            }

            @Override public String description() { return "macOS IOKit"; }
        });
    }

    private static GdxScreenWakeLock linux(ProcessStarter starter) {
        Objects.requireNonNull(starter, "starter");
        return new GdxScreenWakeLock(new Backend() {
            private Process process;

            @Override public boolean acquire() throws Exception {
                process = starter.start(linuxInhibitorCommand(
                        ProcessHandle.current().pid()));
                if (process.waitFor(150L, TimeUnit.MILLISECONDS)) {
                    process = null;
                    return false;
                }
                return true;
            }

            @Override public boolean release() throws Exception {
                Process running = process;
                process = null;
                if (running == null) return true;
                running.descendants().forEach(ProcessHandle::destroy);
                running.destroy();
                if (!running.waitFor(750L, TimeUnit.MILLISECONDS)) {
                    running.descendants().forEach(
                            ProcessHandle::destroyForcibly);
                    running.destroyForcibly();
                    running.waitFor(750L, TimeUnit.MILLISECONDS);
                }
                return !running.isAlive();
            }

            @Override public String description() {
                return "Linux systemd inhibitor";
            }
        });
    }

    void acquire() {
        if (held) return;
        try {
            if (!backend.acquire()) {
                LOGGER.warning(backend.description()
                        + " rejected the CoronaPoker idle inhibitor");
                return;
            }
            held = true;
            ownerThreadId = Thread.currentThread().getId();
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, backend.description()
                    + " idle inhibitor is unavailable", failure);
        }
    }

    boolean held() {
        return held;
    }

    @Override
    public void close() {
        if (!held) return;
        if (Thread.currentThread().getId() != ownerThreadId) {
            LOGGER.warning(
                    "Idle inhibitor release did not run on its owner thread");
            return;
        }
        try {
            if (!backend.release()) {
                LOGGER.warning(backend.description()
                        + " rejected the idle inhibitor release");
                return;
            }
            held = false;
            ownerThreadId = -1L;
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, backend.description()
                    + " idle inhibitor release failed", failure);
        }
    }

    @FunctionalInterface
    private interface MacAcquire {

        int call(String assertionType, String reason,
                IntByReference assertionId);
    }

    @FunctionalInterface
    private interface MacRelease {

        int call(int assertionId);
    }

    private interface IOKitPower extends Library {

        int IOPMAssertionCreateWithName(
                CoreFoundation.CFStringRef assertionType,
                int assertionLevel,
                CoreFoundation.CFStringRef reason,
                IntByReference assertionId);

        int IOPMAssertionRelease(int assertionId);
    }

    private static final class MacNative {

        private static final IOKitPower IOKIT = Native.load("IOKit",
                IOKitPower.class);

        private MacNative() { }

        static int acquire(String assertionType, String reason,
                IntByReference assertionId) {
            CoreFoundation.CFStringRef type =
                    CoreFoundation.CFStringRef.createCFString(
                            assertionType);
            CoreFoundation.CFStringRef message =
                    CoreFoundation.CFStringRef.createCFString(reason);
            try {
                return IOKIT.IOPMAssertionCreateWithName(type,
                        MAC_ASSERTION_LEVEL_ON, message, assertionId);
            } finally {
                CoreFoundation.INSTANCE.CFRelease(message);
                CoreFoundation.INSTANCE.CFRelease(type);
            }
        }

        static int release(int assertionId) {
            return IOKIT.IOPMAssertionRelease(assertionId);
        }
    }

    private static final class UnsupportedBackend implements Backend {

        private final String os;

        UnsupportedBackend(String os) { this.os = os; }

        @Override public boolean acquire() { return false; }

        @Override public boolean release() { return true; }

        @Override public String description() {
            return "Unsupported platform " + os;
        }
    }

    /**
     * Windows execution requirements belong to the thread that sets them.
     * GLFW also changes the render thread's requirement when fullscreen mode
     * changes, so CoronaPoker owns a separate daemon thread whose state GLFW
     * cannot accidentally clear.
     */
    private static final class WindowsBackend implements Backend {

        private final ExecutionStateApi api;
        private final CountDownLatch acquired = new CountDownLatch(1);
        private final CountDownLatch releaseRequested = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);
        private final AtomicBoolean acquisitionAccepted = new AtomicBoolean();
        private final AtomicBoolean releaseAccepted = new AtomicBoolean();
        private Thread owner;

        WindowsBackend(ExecutionStateApi api) { this.api = api; }

        @Override
        public boolean acquire() throws InterruptedException {
            owner = new Thread(this::hold, "coronapoker-idle-inhibitor");
            owner.setDaemon(true);
            owner.start();
            boolean ready;
            try {
                ready = acquired.await(2L, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                releaseRequested.countDown();
                throw interrupted;
            }
            if (!ready) {
                releaseRequested.countDown();
                return false;
            }
            return acquisitionAccepted.get();
        }

        private void hold() {
            try {
                acquisitionAccepted.set(api.set(REQUIRED_FLAGS) != 0);
            } finally {
                acquired.countDown();
            }
            if (!acquisitionAccepted.get()) {
                finished.countDown();
                return;
            }
            try {
                releaseRequested.await();
                releaseAccepted.set(api.set(RELEASE_FLAGS) != 0);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                finished.countDown();
            }
        }

        @Override
        public boolean release() throws InterruptedException {
            releaseRequested.countDown();
            if (!finished.await(2L, TimeUnit.SECONDS)) {
                if (owner != null) owner.interrupt();
                return false;
            }
            return releaseAccepted.get();
        }

        @Override public String description() { return "Windows"; }
    }
}

package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.cef.CefApp.CefAppState;
import org.junit.jupiter.api.Test;

class PokerVideoRuntimeSessionTest {
    @Test void nativeStartupHasAVisibleDeadlineBeforeTheDocumentExists() {
        long limit = TimeUnit.SECONDS.toNanos(60);
        long started = 1L;
        assertFalse(PokerVideoOverlay.nativeStartupTimedOut(0L, limit + started));
        assertFalse(PokerVideoOverlay.nativeStartupTimedOut(started, limit));
        assertTrue(PokerVideoOverlay.nativeStartupTimedOut(started, limit + started));
    }

    @Test void asyncNativeStartupFailureBecomesAPlayerError() throws Exception {
        var runtime = interrupted();
        runtime.onCefStateChanged(CefAppState.INITIALIZING);
        assertNull(runtime.initializationError());
        runtime.onCefStateChanged(CefAppState.INITIALIZATION_FAILED);
        assertNotNull(runtime.initializationError());
        assertEquals("REPRODUCTOR NO DISPONIBLE", runtime.state());
    }

    @Test void interruptedBrowserCanResumeItsLastStableAudio() throws Exception {
        var runtime = interrupted();
        runtime.releaseBrowserSession(true);
        assertEquals(false, field(runtime, "resumeMuted"));
        assertEquals(71, field(runtime, "resumeVolume"));
        assertEquals(true, field(runtime, "restoreAudioOnNextSession"));
        assertNull(runtime.documentLoadError());
    }

    @Test void iconReopeningAlwaysStartsMutedEvenAfterABrowserError() throws Exception {
        var runtime = interrupted();
        runtime.releaseBrowserSession(false);
        assertEquals(true, field(runtime, "resumeMuted"));
        assertEquals(100, field(runtime, "resumeVolume"));
        assertEquals(false, field(runtime, "restoreAudioOnNextSession"));
    }

    private static PokerVideoRuntime interrupted() throws Exception {
        var constructor = PokerVideoRuntime.class.getDeclaredConstructor(List.class,
                PokerVideoFrameBuffer.class, String.class);
        constructor.setAccessible(true);
        var runtime = (PokerVideoRuntime)constructor.newInstance(List.of(
                new PokerVideoSearch.Video("ABCDEFGHIJK", "Video")), new PokerVideoFrameBuffer(), "es");
        set(runtime, "resumeMuted", false);
        set(runtime, "resumeVolume", 71);
        ((PokerVideoDocumentLoad)field(runtime, "documentLoad")).failed("Renderer stopped");
        return runtime;
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

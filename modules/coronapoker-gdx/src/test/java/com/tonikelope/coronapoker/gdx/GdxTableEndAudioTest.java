/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class GdxTableEndAudioTest {

    @Test
    void acceptedExitSilencesTheTableWhileTheCloseRemainsPending() throws Exception {
        var commands = new ArrayList<TableCommand>();
        var snapshot = new TableSnapshot(1L, "local", TableSnapshot.Street.PREFLOP,
                0d, "", false, List.of(), List.of());
        var table = new CoronaPokerGdxTable(60, new GdxTableViewState(snapshot),
                commands::add, () -> { }, new GdxGameLogSink(), null);
        var shuffleStops = new AtomicInteger();
        var dealStops = new AtomicInteger();
        var loopStops = new AtomicInteger();
        setField(table, "shuffleSound", track(Sound.class, shuffleStops));
        setField(table, "dealSound", track(Sound.class, dealStops));
        putTrack(table, "liveAudioCueLoops", "misc/recovering.mp3", track(Music.class, loopStops));

        var exit = new TableCommand.ExitGame();
        invoke(table, "submitTermination", new Class<?>[]{TableCommand.class, boolean.class}, exit, false);
        assertEquals(List.of(exit), commands);
        assertTrue(table.tableClosingOverlayVisible());
        assertEquals(1, shuffleStops.get());
        assertEquals(1, dealStops.get());
        assertEquals(1, loopStops.get());
        assertFalse((boolean) invoke(table, "startShuffleSound",
                new Class<?>[]{Sound.class, float.class}, track(Sound.class, shuffleStops), 0f));

        var closure = GdxFinalSummaryNoticeTest.close(table, TableSessionSummary.CloseReason.EXITED, false);
        assertFalse(table.tableClosingOverlayVisible());
        assertFalse(closure.isDone(), "the renderer must wait for the final-screen action");
        assertTrue(table.handleFinalSummaryNavigationAction(0));
        assertTrue(closure.isDone());
    }

    @Test
    void serverCloseRetiresBothShuffleTracksAndReleasesAVisualFinishWait() throws Exception {
        var table = GdxFinalSummaryNoticeTest.table();
        var fallbackStops = new AtomicInteger();
        var riffleStops = new AtomicInteger();
        setField(table, "shuffleSound", track(Sound.class, fallbackStops));
        putTrack(table, "riffleTracks", GdxShuffleGeometry.Motion.FULL_DECK, track(Sound.class, riffleStops));

        var start = new CompletableFuture<Void>();
        var finish = new CompletableFuture<Void>();
        table.acceptEvent(new TableVisualEvent.Shuffle(2L, "goliat", TableVisualEvent.Shuffle.Phase.START), start);
        table.acceptEvent(new TableVisualEvent.Shuffle(3L, "goliat", TableVisualEvent.Shuffle.Phase.FINISH), finish);
        assertTrue(start.isDone());
        assertFalse(finish.isDone());

        var summary = new TableSessionSummary("local", 1, 15L, 1_000L,
                TableSessionSummary.CloseReason.COMPLETED,
                List.of(new TableSessionSummary.PlayerBalance("local", 20d, 20d, 0)));
        var closure = new CompletableFuture<Void>();
        table.acceptEvent(new TableVisualEvent.CloseTable(4L, summary,
                TableSnapshot.Street.FINISHED, true), closure);
        assertTrue(finish.isDone());
        assertNull(field(table, "liveShuffle").get(table));
        assertTrue(fallbackStops.get() > 0);
        assertTrue(riffleStops.get() > 0);
        assertFalse(closure.isDone());
    }

    private static <T> T track(Class<T> type, AtomicInteger stops) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (self, method, arguments) -> {
                    if (method.getName().equals("stop")) stops.incrementAndGet();
                    return null;
                }));
    }

    @SuppressWarnings("unchecked")
    private static void putTrack(CoronaPokerGdxTable table, String name, Object key, Object value)
            throws Exception {
        ((Map<Object, Object>) field(table, name).get(table)).put(key, value);
    }

    private static void setField(CoronaPokerGdxTable table, String name, Object value) throws Exception {
        field(table, name).set(table, value);
    }

    private static java.lang.reflect.Field field(CoronaPokerGdxTable table, String name) throws Exception {
        var field = table.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object invoke(CoronaPokerGdxTable table, String name, Class<?>[] types,
            Object... arguments) throws Exception {
        var method = table.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(table, arguments);
    }
}

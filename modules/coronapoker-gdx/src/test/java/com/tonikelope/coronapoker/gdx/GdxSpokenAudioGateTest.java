/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class GdxSpokenAudioGateTest {

    @Test
    void voiceAndTextToSpeechCannotOverlap() throws Exception {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempted = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);

        Thread first = new Thread(() -> callGate(() -> {
            order.add("voice-start");
            firstEntered.countDown();
            assertTrue(releaseFirst.await(2, TimeUnit.SECONDS));
            order.add("voice-end");
        }));
        Thread second = new Thread(() -> {
            secondAttempted.countDown();
            callGate(() -> {
                order.add("tts-start");
                secondEntered.countDown();
                order.add("tts-end");
            });
        });

        first.start();
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
        second.start();
        assertTrue(secondAttempted.await(2, TimeUnit.SECONDS));
        assertFalse(secondEntered.await(80, TimeUnit.MILLISECONDS));
        releaseFirst.countDown();
        first.join(2_000L);
        second.join(2_000L);

        assertEquals(List.of("voice-start", "voice-end", "tts-start",
                "tts-end"), order);
    }

    @Test
    void waitingPlaybackCanBeCancelledDuringTableTeardown() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);

        Thread first = new Thread(() -> callGate(() -> {
            firstEntered.countDown();
            assertTrue(releaseFirst.await(2, TimeUnit.SECONDS));
        }));
        Thread second = new Thread(() -> {
            try {
                GdxSpokenAudioGate.call(() -> {
                    secondEntered.countDown();
                    return null;
                });
            } catch (InterruptedException expected) {
                Thread.currentThread().interrupt();
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });

        first.start();
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
        second.start();
        second.interrupt();
        second.join(2_000L);
        assertFalse(second.isAlive());
        assertFalse(secondEntered.await(20, TimeUnit.MILLISECONDS));
        releaseFirst.countDown();
        first.join(2_000L);
        assertFalse(first.isAlive());
    }

    private static void callGate(InterruptibleRunnable operation) {
        try {
            GdxSpokenAudioGate.call(() -> {
                operation.run();
                return null;
            });
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    @FunctionalInterface
    private interface InterruptibleRunnable {
        void run() throws Exception;
    }
}

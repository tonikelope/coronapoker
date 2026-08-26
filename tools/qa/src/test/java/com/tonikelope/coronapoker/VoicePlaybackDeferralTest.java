package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class VoicePlaybackDeferralTest {

    @Test
    void queuedPlaybackWaitsUntilRecordingSilenceEnds() throws Exception {
        Audio.setVoiceRecording(true);
        Thread release = new Thread(() -> {
            try {
                Thread.sleep(75L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                Audio.setVoiceRecording(false);
            }
        }, "voice-playback-deferral-test");

        try {
            release.start();
            assertTrue(org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                    Duration.ofSeconds(2), () -> Audio.awaitVoiceRecordingEnd(1000L)));
        } finally {
            release.join(2000L);
            if (Audio.VOICE_RECORDING) {
                Audio.setVoiceRecording(false);
            }
        }
    }

    @Test
    void deferralHasABoundedTimeout() {
        Audio.setVoiceRecording(true);
        try {
            assertFalse(Audio.awaitVoiceRecordingEnd(0L));
        } finally {
            Audio.setVoiceRecording(false);
        }
    }
}

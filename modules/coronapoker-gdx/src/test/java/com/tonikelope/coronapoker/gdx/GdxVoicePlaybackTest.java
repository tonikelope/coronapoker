package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.audio.VoiceWavContract;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;

final class GdxVoicePlaybackTest {

    @Test
    void emptyPayloadIsAnInertSuccessfulPlayback() {
        AtomicBoolean playbackStarted = new AtomicBoolean();
        assertDoesNotThrow(() -> GdxVoicePlayback.play(new byte[0],
                () -> playbackStarted.set(true))
                .get(5, TimeUnit.SECONDS));
        assertFalse(playbackStarted.get());
    }

    @Test
    void decoderFailureReachesTheUiInsteadOfBeingSilenced() {
        AtomicBoolean playbackStarted = new AtomicBoolean();
        assertThrows(ExecutionException.class,
                () -> GdxVoicePlayback.play(new byte[]{1, 2, 3, 4},
                        () -> playbackStarted.set(true))
                        .get(5, TimeUnit.SECONDS));
        assertFalse(playbackStarted.get());
    }

    @Test
    void sharedUlawVoiceNoteIsDecodedToPcmForOpenAl() throws Exception {
        byte[] pcm = new byte[(int) GdxVoiceRecorder.SAMPLE_RATE];
        byte[] ulawWav = GdxVoiceRecorder.encodePcm(pcm);

        byte[] playbackWav = GdxVoicePlayback.playbackPcmWav(ulawWav);

        try (AudioInputStream decoded = AudioSystem.getAudioInputStream(
                new ByteArrayInputStream(playbackWav))) {
            AudioFormat format = decoded.getFormat();
            assertEquals(AudioFormat.Encoding.PCM_SIGNED,
                    format.getEncoding());
            assertEquals(16, format.getSampleSizeInBits());
            assertEquals(1, format.getChannels());
            assertEquals(GdxVoiceRecorder.SAMPLE_RATE,
                    format.getSampleRate());
            assertFalse(format.isBigEndian());
        }
        assertEquals(VoiceWavContract.durationMillis(ulawWav) + 1_500L,
                GdxVoicePlayback.playbackWatchdogMillis(ulawWav));
    }
}

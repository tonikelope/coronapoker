package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
}

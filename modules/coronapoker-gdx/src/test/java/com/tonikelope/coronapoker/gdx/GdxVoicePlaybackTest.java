package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class GdxVoicePlaybackTest {

    @Test
    void emptyPayloadIsAnInertSuccessfulPlayback() {
        assertDoesNotThrow(() -> GdxVoicePlayback.play(new byte[0])
                .get(5, TimeUnit.SECONDS));
    }

    @Test
    void decoderFailureReachesTheUiInsteadOfBeingSilenced() {
        assertThrows(ExecutionException.class,
                () -> GdxVoicePlayback.play(new byte[]{1, 2, 3, 4})
                        .get(5, TimeUnit.SECONDS));
    }
}

package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public class VoiceRecorderTeardownTest {

    @Test
    public void definitiveTableTeardownReleasesRecorderWaitWithoutMicInteraction() {
        VoiceRecorder recorder = new VoiceRecorder();

        recorder.abortForTableTeardown();

        assertTimeoutPreemptively(Duration.ofSeconds(1), recorder::stop,
                "table teardown must not wait for the recorder's normal three-second safety net");
    }

    @Test
    public void definitiveTableTeardownDiscardsAudioAlreadyCaptured() throws Exception {
        VoiceRecorder recorder = new VoiceRecorder();
        Field pcmField = VoiceRecorder.class.getDeclaredField("pcm");
        pcmField.setAccessible(true);
        ByteArrayOutputStream pcm = (ByteArrayOutputStream) pcmField.get(recorder);
        byte[] audiblePcm = new byte[4_000];
        for (int i = 0; i < audiblePcm.length; i += 2) {
            audiblePcm[i] = 0x20;
            audiblePcm[i + 1] = 0x03;
        }
        pcm.write(audiblePcm);

        Field gotAudioField = VoiceRecorder.class.getDeclaredField("got_audio");
        gotAudioField.setAccessible(true);
        gotAudioField.setBoolean(recorder, true);

        recorder.abortForTableTeardown();

        assertNull(recorder.stop(),
                "a stop task already in flight must not encode or send a note after teardown");
    }
}

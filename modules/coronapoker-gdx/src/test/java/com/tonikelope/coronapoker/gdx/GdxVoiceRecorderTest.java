package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;

class GdxVoiceRecorderTest {

    @Test
    void encodesTheSwingCompatibleMulawContract() throws Exception {
        byte[] pcm = new byte[(int) GdxVoiceRecorder.SAMPLE_RATE * 2];
        for (int index = 0; index + 1 < pcm.length; index += 2) {
            short sample = (short) (Math.sin(index / 19d) * 12_000);
            pcm[index] = (byte) sample;
            pcm[index + 1] = (byte) (sample >>> 8);
        }

        byte[] wav = GdxVoiceRecorder.encodePcm(pcm);

        assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(wav, 8, 4, StandardCharsets.US_ASCII));
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(
                new java.io.ByteArrayInputStream(wav))) {
            assertEquals(16_000f, stream.getFormat().getSampleRate());
            assertEquals(1, stream.getFormat().getChannels());
            assertTrue(stream.getFormat().getEncoding().toString()
                    .toUpperCase().contains("ULAW"));
        }
    }
}

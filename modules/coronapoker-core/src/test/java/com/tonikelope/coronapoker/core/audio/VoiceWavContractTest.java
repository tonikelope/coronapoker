package com.tonikelope.coronapoker.core.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;

class VoiceWavContractTest {

    @Test
    void acceptsTheOfficialMulawFormatAndRejectsLookalikeBytes() throws Exception {
        byte[] pcm = new byte[16_000 * 2 / 5];
        for (int index = 0; index + 1 < pcm.length; index += 2) {
            short sample = (short) (Math.sin(index / 11d) * 10_000);
            pcm[index] = (byte) sample;
            pcm[index + 1] = (byte) (sample >>> 8);
        }
        AudioFormat source = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                16_000f, 16, 1, 2, 16_000f, false);
        AudioFormat target = new AudioFormat(AudioFormat.Encoding.ULAW,
                16_000f, 8, 1, 1, 16_000f, false);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (AudioInputStream input = new AudioInputStream(
                new ByteArrayInputStream(pcm), source, pcm.length / 2);
                AudioInputStream ulaw = AudioSystem.getAudioInputStream(target,
                        input)) {
            AudioSystem.write(ulaw, AudioFileFormat.Type.WAVE, encoded);
        }

        assertTrue(VoiceWavContract.isValid(encoded.toByteArray()));
        assertFalse(VoiceWavContract.isValid("RIFF-not-a-wave"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    }
}

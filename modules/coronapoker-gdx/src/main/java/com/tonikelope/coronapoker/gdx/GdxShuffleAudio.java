/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.files.FileHandle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.sound.sampled.*;

/** Places two copies of the original recording on the mesh's sample clock. */
final class GdxShuffleAudio {
    private GdxShuffleAudio() {}

    record Track(FileHandle file, float originalDuration) {}

    static Track track(FileHandle recording) {
        try (AudioInputStream input = AudioSystem.getAudioInputStream(recording.read(8192))) {
            var original = input.getFormat();
            var format = new AudioFormat(original.getSampleRate(), 16, original.getChannels(), true, false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(format, input)) {
                byte[] source = pcm.readAllBytes();
                float duration = source.length / (format.getFrameSize() * format.getFrameRate());
                byte[] audio = arrange(source, format);
                var output = new ByteArrayOutputStream();
                try (var stream = new AudioInputStream(new ByteArrayInputStream(audio), format,
                        audio.length / format.getFrameSize())) {
                    AudioSystem.write(stream, AudioFileFormat.Type.WAVE, output);
                }
                byte[] wav = output.toByteArray();
                FileHandle file = new FileHandle("table-riffle.wav") {
                    @Override public InputStream read() { return new ByteArrayInputStream(wav); }
                };
                return new Track(file, duration);
            }
        } catch (IOException | UnsupportedAudioFileException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot prepare the shuffle recording " + recording, e);
        }
    }

    static byte[] arrange(byte[] source, AudioFormat format) {
        int stride = format.getFrameSize();
        float rate = format.getSampleRate();
        int frames = Math.round(GdxShuffleGeometry.Motion.MID_BRIDGE.duration() * rate);
        byte[] result = new byte[frames * stride];
        for (int cue = 0; cue < 2; cue++) {
            int start = Math.round(GdxShuffleGeometry.soundStart(cue) * rate);
            int end = Math.round(GdxShuffleGeometry.soundEnd(cue) * rate);
            System.arraycopy(source, 0, result, start * stride, Math.min(source.length, (end - start) * stride));
        }
        return result;
    }
}

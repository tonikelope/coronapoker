/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.files.FileHandle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import com.tonikelope.coronapoker.gdx.GdxShuffleGeometry.Motion;
import javax.sound.sampled.*;

/** Places the shuffle recording on the sample clock of each playable motion. */
final class GdxShuffleAudio {
    private GdxShuffleAudio() {}

    record Tracks(EnumMap<Motion, FileHandle> files, float originalDuration) {}

    static Tracks tracks(FileHandle recording) {
        try (AudioInputStream input = AudioSystem.getAudioInputStream(recording.read(8192))) {
            var original = input.getFormat();
            var format = new AudioFormat(original.getSampleRate(), 16, original.getChannels(), true, false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(format, input)) {
                byte[] source = pcm.readAllBytes();
                float duration = source.length / (format.getFrameSize() * format.getFrameRate());
                var files = new EnumMap<Motion, FileHandle>(Motion.class);
                for (Motion motion : new Motion[]{Motion.MID_BRIDGE, Motion.FULL_DECK, Motion.ORIGINAL}) {
                    files.put(motion, wav(arrange(source, format, motion), format));
                }
                return new Tracks(files, duration);
            }
        } catch (IOException | UnsupportedAudioFileException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot prepare the shuffle recording " + recording, e);
        }
    }

    private static FileHandle wav(byte[] pcm, AudioFormat format) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var stream = new AudioInputStream(new ByteArrayInputStream(pcm), format,
                pcm.length / format.getFrameSize())) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, output);
        }
        byte[] bytes = output.toByteArray();
        return new FileHandle("table-riffle.wav") {
            @Override public InputStream read() { return new ByteArrayInputStream(bytes); }
        };
    }

    static byte[] arrange(byte[] source, AudioFormat format, Motion motion) {
        int stride = format.getFrameSize();
        float rate = format.getSampleRate();
        int frames = Math.round(motion.duration() * rate);
        byte[] result = new byte[frames * stride];
        for (int cue = 0; cue < (motion.hasBridge() ? 2 : 1); cue++) {
            int start = Math.round(motion.soundStart(cue) * rate);
            int end = Math.round(motion.soundEnd(cue) * rate);
            System.arraycopy(source, 0, result, start * stride, Math.min(source.length, (end - start) * stride));
        }
        return result;
    }
}

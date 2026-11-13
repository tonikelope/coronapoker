/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.tonikelope.coronapoker.core.audio.VoiceWavContract;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Serial, non-blocking playback for the shared voice-note WAV format.
 *
 * Voice notes deliberately use libGDX audio instead of Java Sound so they
 * share the selected OpenAL output device with music, effects and TTS.
 */
final class GdxVoicePlayback {

    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "gdx-voice-playback");
                thread.setDaemon(true);
                return thread;
            });
    private static final Set<Request> REQUESTS = ConcurrentHashMap.newKeySet();
    private static volatile float voiceVolume = 1f;

    static CompletableFuture<Void> play(byte[] wav, Runnable playbackStarted) {
        return play(wav, 1f, playbackStarted);
    }

    static CompletableFuture<Void> play(byte[] wav, float volume,
            Runnable playbackStarted) {
        return play(wav, volume, () -> true, playbackStarted);
    }

    static CompletableFuture<Void> play(byte[] wav, float volume,
            BooleanSupplier allowed, Runnable playbackStarted) {
        if (wav == null || wav.length == 0) return CompletableFuture.completedFuture(null);
        return enqueue(wav::clone, volume, allowed, playbackStarted).completion();
    }

    static Request playManual(Callable<byte[]> source, float volume,
            BooleanSupplier allowed, Runnable playbackStarted) {
        stop();
        return enqueue(source, volume, allowed, playbackStarted);
    }

    private static Request enqueue(Callable<byte[]> source, float volume,
            BooleanSupplier allowed, Runnable playbackStarted) {
        voiceVolume = GdxTextToSpeechPlayback.ttsVolume(volume);
        Request request = new Request(Gdx.app, allowed, playbackStarted);
        REQUESTS.add(request);
        request.completion.whenComplete((unused, error) -> REQUESTS.remove(request));
        QUEUE.execute(() -> request.prepare(source));
        return request;
    }

    static void refreshVolume(float masterVolume) {
        float volume = GdxTextToSpeechPlayback.ttsVolume(masterVolume);
        if (voiceVolume == volume) return;
        voiceVolume = volume;
        for (Request request : REQUESTS) request.refreshVolume();
    }

    static void stop() {
        for (Request request : REQUESTS) request.stop();
    }

    /** Null means that a manual note can be played with the current settings. */
    static String blockedReason(boolean outputAvailable, boolean soundEnabled,
            boolean voiceBlocked) {
        if (!outputAvailable) return "gdx.audio.no_output_device";
        if (voiceBlocked) return "gdx.lobby.voice_playback_disabled";
        if (!soundEnabled) return "gdx.lobby.voice_sound_muted";
        return null;
    }

    static final class Request {
        private static final long PAUSED = Long.MAX_VALUE;
        private final Application app;
        private final BooleanSupplier allowed;
        private final Runnable playbackStarted;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private volatile boolean cancelled;
        private volatile long deadline;
        private Music music;
        private long pausedAt;
        private long deadlineBeforePause;

        private Request(Application app, BooleanSupplier allowed, Runnable playbackStarted) {
            this.app = app;
            this.allowed = allowed;
            this.playbackStarted = playbackStarted;
        }

        CompletableFuture<Void> completion() {
            return completion;
        }

        boolean loading() {
            return music == null && !completion.isDone() && !cancelled;
        }

        boolean playing() {
            return music != null && pausedAt == 0L && !cancelled;
        }

        void togglePause() {
            if (music == null || cancelled) return;
            try {
                if (!allowed.getAsBoolean() || !audioOutputAvailable()) {
                    finish(null);
                } else if (pausedAt == 0L) {
                    music.pause();
                    pausedAt = System.nanoTime();
                    deadlineBeforePause = deadline;
                    deadline = PAUSED;
                } else {
                    music.play();
                    deadline = System.nanoTime()
                            + Math.max(0L, deadlineBeforePause - pausedAt);
                    pausedAt = 0L;
                    verifyPlaying(music);
                }
            } catch (RuntimeException failure) {
                finish(failure);
            }
        }

        void stop() {
            if (cancelled || completion.isDone()) return;
            cancelled = true;
            if (app != null) app.postRunnable(() -> finish(null));
            else completion.complete(null);
        }

        private void refreshVolume() {
            if (app == null || completion.isDone()) return;
            app.postRunnable(() -> {
                if (music == null || cancelled) return;
                try {
                    music.setVolume(voiceVolume);
                } catch (RuntimeException failure) {
                    finish(failure);
                }
            });
        }

        private void prepare(Callable<byte[]> source) {
            if (cancelled) return;
            Path temporary = null;
            try {
                byte[] wav = source.call();
                if (cancelled) return;
                if (!VoiceWavContract.isValid(wav)) {
                    throw new IOException("Invalid CoronaPoker voice-note WAV");
                }
                if (app == null) throw new IllegalStateException("Audio backend unavailable");
                temporary = Files.createTempFile("coronapoker-voice-", ".wav");
                Files.write(temporary, playbackPcmWav(wav));
                Path ready = temporary;
                GdxSpokenAudioGate.call(() -> {
                    if (cancelled) return null;
                    deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    app.postRunnable(() -> start(ready, playbackWatchdogMillis(wav)));
                    awaitPlayback();
                    return null;
                });
            } catch (ExecutionException failure) {
                completion.completeExceptionally(failure.getCause());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                stop();
            } catch (Exception failure) {
                if (!cancelled) completion.completeExceptionally(failure);
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException ignored) {
                        temporary.toFile().deleteOnExit();
                    }
                }
            }
        }

        private void awaitPlayback() throws InterruptedException, ExecutionException {
            while (!completion.isDone()) {
                try {
                    completion.get(250, TimeUnit.MILLISECONDS);
                } catch (TimeoutException pending) {
                    if (!expired()) continue;
                    app.postRunnable(() -> {
                        if (expired()) finish(new TimeoutException(
                                "OpenAL voice playback did not complete"));
                    });
                    try {
                        completion.get(2, TimeUnit.SECONDS);
                    } catch (TimeoutException stillPending) {
                        // A pause or a delayed start can change the deadline.
                    }
                }
            }
        }

        private boolean expired() {
            long limit = deadline;
            return limit != PAUSED && System.nanoTime() - limit >= 0L;
        }

        private void start(Path wav, long watchdogMillis) {
            if (cancelled || completion.isDone()) return;
            try {
                if (!allowed.getAsBoolean()) {
                    finish(null);
                    return;
                }
                if (!audioOutputAvailable() || Gdx.audio == null) {
                    throw new IllegalStateException("No audio output device");
                }
                music = Gdx.audio.newMusic(new FileHandle(wav.toFile()));
                Music current = music;
                music.setVolume(voiceVolume);
                music.setOnCompletionListener(ignored -> {
                    if (music == current) finish(null);
                });
                deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(watchdogMillis);
                music.play();
                verifyPlaying(current);
                if (playbackStarted != null && music == current) playbackStarted.run();
            } catch (RuntimeException failure) {
                finish(failure);
            }
        }

        private void verifyPlaying(Music current) {
            // Some backends accept play() with no OpenAL source available.
            if (!current.isPlaying()) {
                finish(new IllegalStateException("OpenAL voice playback could not start"));
            }
        }

        private void finish(Throwable failure) {
            Music current = music;
            music = null;
            pausedAt = 0L;
            if (current != null) {
                try { current.stop(); } catch (RuntimeException ignored) { }
                try { current.dispose(); } catch (RuntimeException ignored) { }
            }
            if (failure == null || cancelled) completion.complete(null);
            else completion.completeExceptionally(failure);
        }
    }

    /**
     * Decodes the shared G.711 mu-law voice-note format for OpenAL playback.
     * The original payload remains untouched for storage and network transport.
     */
    static byte[] playbackPcmWav(byte[] wav) throws IOException {
        if (!VoiceWavContract.isValid(wav)) {
            throw new IOException("Invalid CoronaPoker voice-note WAV");
        }
        try (AudioInputStream source = AudioSystem.getAudioInputStream(
                new ByteArrayInputStream(wav))) {
            AudioFormat sourceFormat = source.getFormat();
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(),
                    16,
                    sourceFormat.getChannels(),
                    sourceFormat.getChannels() * 2,
                    sourceFormat.getSampleRate(),
                    false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(
                    pcmFormat, source);
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] audio = GdxVoiceRecorder.trimLeadingSilence(pcm.readAllBytes());
                try (var trimmed = new AudioInputStream(new ByteArrayInputStream(audio),
                        pcmFormat, audio.length / pcmFormat.getFrameSize())) {
                    AudioSystem.write(trimmed, AudioFileFormat.Type.WAVE, output);
                }
                return output.toByteArray();
            }
        } catch (UnsupportedAudioFileException | IllegalArgumentException failure) {
            throw new IOException("Unable to decode CoronaPoker voice note",
                    failure);
        }
    }

    static long playbackWatchdogMillis(byte[] wav) {
        long duration = VoiceWavContract.durationMillis(wav);
        if (duration < 0L) return 1_500L;
        return Math.min(VoiceWavContract.MAX_SECONDS * 1_000L + 1_500L,
                Math.max(1_500L, duration + 1_500L));
    }

    private static boolean audioOutputAvailable() {
        GdxApplicationShell shell = GdxApplicationShell.active();
        return shell == null || shell.audioOutputAvailable();
    }

    private GdxVoicePlayback() {
    }
}

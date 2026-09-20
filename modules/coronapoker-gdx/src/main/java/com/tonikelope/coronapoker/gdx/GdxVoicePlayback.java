/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.tonikelope.coronapoker.core.audio.VoiceWavContract;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final AtomicReference<Music> ACTIVE = new AtomicReference<>();
    private static final AtomicReference<CompletableFuture<Void>> ACTIVE_COMPLETION
            = new AtomicReference<>();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static volatile float activeVolume = 1f;

    static CompletableFuture<Void> play(byte[] wav) {
        return play(wav, 1f, () -> { });
    }

    static CompletableFuture<Void> play(byte[] wav, Runnable playbackStarted) {
        return play(wav, 1f, playbackStarted);
    }

    static CompletableFuture<Void> play(byte[] wav, float masterVolume,
            Runnable playbackStarted) {
        if (wav == null || wav.length == 0) {
            return CompletableFuture.completedFuture(null);
        }
        byte[] payload = Arrays.copyOf(wav, wav.length);
        Runnable started = playbackStarted == null ? () -> { }
                : playbackStarted;
        float voiceVolume = GdxTextToSpeechPlayback.ttsVolume(masterVolume);
        long generation = GENERATION.get();
        return CompletableFuture.runAsync(
                () -> playQueued(payload, voiceVolume, started, generation),
                QUEUE);
    }

    static void refreshVolume(float masterVolume) {
        activeVolume = GdxTextToSpeechPlayback.ttsVolume(masterVolume);
        Music music = ACTIVE.get();
        if (music == null || Gdx.app == null) return;
        Gdx.app.postRunnable(() -> {
            if (ACTIVE.get() == music) music.setVolume(activeVolume);
        });
    }

    static void stop() {
        GENERATION.incrementAndGet();
        Music music = ACTIVE.get();
        if (music == null || Gdx.app == null) return;
        CompletableFuture<Void> completion = ACTIVE_COMPLETION.get();
        Gdx.app.postRunnable(() -> finish(music, completion, null));
    }

    private static void playQueued(byte[] wav, float volume,
            Runnable playbackStarted, long generation) {
        if (!VoiceWavContract.isValid(wav)) {
            throw new CompletionException(new IOException(
                    "Invalid CoronaPoker voice-note WAV"));
        }
        if (Gdx.app == null || Gdx.audio == null) {
            throw new CompletionException(new IllegalStateException(
                    "libGDX audio backend is unavailable"));
        }
        if (generation != GENERATION.get()) return;
        Path temporary = null;
        try {
            temporary = Files.createTempFile("coronapoker-voice-", ".wav");
            Files.write(temporary, wav);
            Path ready = temporary;
            CompletableFuture<Void> completion = new CompletableFuture<>();
            Gdx.app.postRunnable(() -> start(ready, volume,
                    playbackStarted, completion, generation));
            completion.join();
        } catch (IOException failure) {
            throw new CompletionException(failure);
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

    private static void start(Path wav, float volume, Runnable playbackStarted,
            CompletableFuture<Void> completion, long generation) {
        if (generation != GENERATION.get()) {
            completion.complete(null);
            return;
        }
        Music music = null;
        try {
            music = Gdx.audio.newMusic(new FileHandle(wav.toFile()));
            ACTIVE.set(music);
            ACTIVE_COMPLETION.set(completion);
            activeVolume = volume;
            music.setVolume(volume);
            Music playing = music;
            music.setOnCompletionListener(ignored -> finish(playing,
                    completion, null));
            music.play();
            playbackStarted.run();
        } catch (Throwable failure) {
            finish(music, completion, failure);
        }
    }

    private static void finish(Music music, CompletableFuture<Void> completion,
            Throwable failure) {
        if (music != null) {
            ACTIVE.compareAndSet(music, null);
            ACTIVE_COMPLETION.compareAndSet(completion, null);
            try {
                music.stop();
            } catch (RuntimeException ignored) {
                // The backend may already have completed the source.
            }
            music.dispose();
        }
        if (completion == null || completion.isDone()) return;
        if (failure == null) completion.complete(null);
        else completion.completeExceptionally(failure);
    }

    private GdxVoicePlayback() {
    }
}

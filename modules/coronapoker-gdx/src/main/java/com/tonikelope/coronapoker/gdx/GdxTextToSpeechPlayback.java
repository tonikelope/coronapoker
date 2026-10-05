/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GDX playback adapter for Swing's canonical
 * {@code FastChatDialog -> TTSWatchdog -> Audio.TTS} contract.
 */
final class GdxTextToSpeechPlayback implements AutoCloseable {

    static final int MAX_TTS_LENGTH = 150;
    private static final int IO_TIMEOUT_MILLIS = 3_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:61.0) Gecko/20100101 Firefox/61.0";
    private static final Pattern BBCODE_I = Pattern.compile(
            "(?i)\\[ *([i]) *\\](.*?)\\[ */ *\\1 *\\]");
    private static final Pattern BBCODE_B = Pattern.compile(
            "(?i)\\[ *([b]) *\\](.*?)\\[ */ *\\1 *\\]");
    private static final Pattern BBCODE_COLOR = Pattern.compile(
            "(?i)\\[ *([c](?:olor)?) *= *(.*?) *\\](.*?)\\[ */ *\\1 *\\]");
    private static final Pattern LINK_OR_IMAGE = Pattern.compile(
            "(?:http|img)s?://[^ \\r\\n]+");
    private static final Pattern EMOJI = Pattern.compile("#[0-9]+#");
    private static final Pattern BASE64_RESPONSE = Pattern.compile(
            "\\[\"([^\\[\\]\"]+)\"\\]");
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "CoronaPoker-gdx-tts");
                thread.setDaemon(true);
                return thread;
            });
    private final DoubleSupplier volume;
    private final Consumer<Boolean> ducking;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private volatile Music activeMusic;
    private volatile CompletableFuture<Boolean> activeCompletion;

    GdxTextToSpeechPlayback(DoubleSupplier volume,
            Consumer<Boolean> ducking) {
        this.volume = Objects.requireNonNull(volume, "volume");
        this.ducking = Objects.requireNonNull(ducking, "ducking");
    }

    CompletableFuture<Boolean> enqueue(String chatMessage, String language) {
        return enqueue(chatMessage, language, () -> { });
    }

    CompletableFuture<Boolean> enqueue(String chatMessage, String language,
            Runnable playbackStarted) {
        Objects.requireNonNull(playbackStarted, "playbackStarted");
        String speech = serviceText(cleanChatMessage(chatMessage));
        if (speech.isEmpty() || speech.length() > MAX_TTS_LENGTH
                || closed.get() || !audioOutputAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        String speechLanguage = "es".equalsIgnoreCase(language) ? "es" : "en";
        long requestedGeneration = generation.get();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        worker.execute(() -> {
            boolean played = false;
            try {
                played = fetchAndPlay(speech, speechLanguage,
                        playbackStarted, requestedGeneration);
            } catch (RuntimeException ignored) {
                // Speech is best effort; the chat message remains available.
            }
            result.complete(played);
        });
        return result;
    }

    /** Re-applies Swing's live master-volume law to the current voice. */
    void refreshVolume() {
        Music music = activeMusic;
        if (music != null) {
            try {
                music.setVolume(ttsVolume(volume.getAsDouble()));
            } catch (RuntimeException unavailable) {
                stop();
            }
        }
    }

    static float ttsVolume(double masterVolume) {
        return (float) Math.max(0d, Math.min(1d, masterVolume * 2d));
    }

    private boolean fetchAndPlay(String speech, String language,
            Runnable playbackStarted, long requestedGeneration) {
        Path mp3 = null;
        CompletableFuture<Boolean> finished = null;
        try {
            if (requestedGeneration != generation.get()) return false;
            byte[] audio = download(speech, language);
            if (audio.length == 0 || closed.get()
                    || requestedGeneration != generation.get()
                    || Thread.currentThread().isInterrupted()) return false;
            mp3 = Files.createTempFile("coronapoker-gdx-tts-", ".mp3");
            Files.write(mp3, audio);
            finished = new CompletableFuture<>();
            Path playbackFile = mp3;
            CompletableFuture<Boolean> playbackFinished = finished;
            return GdxSpokenAudioGate.call(() -> {
                if (closed.get() || !audioOutputAvailable()
                        || requestedGeneration != generation.get()) {
                    return false;
                }
                Gdx.app.postRunnable(() -> startPlayback(playbackFile,
                        playbackFinished,
                        playbackStarted, requestedGeneration));
                return playbackFinished.get(
                        playbackWatchdogSeconds(speech), TimeUnit.SECONDS);
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException stalledBackend) {
            // OpenAL may accept play() with no physical output device and
            // never fire its completion listener. Release the serial spoken
            // audio gate and dispose the stalled stream on the render thread.
            if (finished != null) {
                abortPlayback(finished);
                try {
                    // Keep the temporary MP3 alive until render-thread
                    // disposal releases the streaming file handle.
                    finished.get(2, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // The watchdog remains a best-effort recovery path.
                }
            }
        } catch (Exception ignored) {
            // TTS is best effort: chat and its seat icon remain available.
        } finally {
            if (mp3 != null) {
                try {
                    Files.deleteIfExists(mp3);
                } catch (Exception ignored) {
                    // Best effort during teardown.
                }
            }
        }
        return false;
    }

    private void startPlayback(Path mp3, CompletableFuture<Boolean> finished,
            Runnable playbackStarted, long requestedGeneration) {
        if (closed.get() || !audioOutputAvailable()
                || requestedGeneration != generation.get()) {
            finished.complete(false);
            return;
        }
        try {
            Music music = Gdx.audio.newMusic(Gdx.files.absolute(
                    mp3.toAbsolutePath().toString()));
            activeMusic = music;
            activeCompletion = finished;
            ducking.accept(true);
            music.setVolume(ttsVolume(volume.getAsDouble()));
            music.setOnCompletionListener(ignored -> finishPlayback(
                    music, finished, true));
            music.play();
            try {
                playbackStarted.run();
            } catch (RuntimeException ignored) {
                // A visual notification must never abort audible playback.
            }
            // Verify on the following render turn. Some OpenAL backends do
            // not throw when no output device exists; isPlaying() is then the
            // only prompt failure signal available to us.
            Gdx.app.postRunnable(() -> {
                try {
                    if (activeMusic == music && activeCompletion == finished
                            && !finished.isDone() && !music.isPlaying()) {
                        finishPlayback(music, finished, false);
                    }
                } catch (RuntimeException unavailable) {
                    finishPlayback(music, finished, false);
                }
            });
        } catch (RuntimeException failure) {
            activeMusic = null;
            activeCompletion = null;
            ducking.accept(false);
            finished.complete(false);
        }
    }

    private synchronized void finishPlayback(Music music,
            CompletableFuture<Boolean> finished,
            boolean played) {
        if (finished.isDone()) return;
        if (activeMusic == music) activeMusic = null;
        if (activeCompletion == finished) activeCompletion = null;
        try {
            disposeMusic(music);
        } finally {
            ducking.accept(false);
            // Complete after disposal so the worker cannot delete the
            // temporary streaming file while OpenAL still owns it.
            finished.complete(played);
        }
    }

    private static void disposeMusic(Music music) {
        try {
            music.stop();
        } catch (RuntimeException ignored) {
            // A missing/replaced OpenAL device may already own no source.
        }
        try {
            music.dispose();
        } catch (RuntimeException ignored) {
            // Best effort during device loss and application teardown.
        }
    }

    private void abortPlayback(CompletableFuture<Boolean> finished) {
        if (Gdx.app == null) {
            finished.complete(false);
            return;
        }
        Gdx.app.postRunnable(() -> {
            Music music = activeMusic;
            if (music != null && activeCompletion == finished) {
                finishPlayback(music, finished, false);
            } else {
                finished.complete(false);
            }
        });
    }

    static long playbackWatchdogSeconds(String speech) {
        String value = Objects.requireNonNullElse(speech, "");
        int length = value.codePointCount(0, value.length());
        return Math.max(5L, Math.min(18L,
                (long) Math.ceil(length / 12d) + 3L));
    }

    private static boolean audioOutputAvailable() {
        GdxApplicationShell shell = GdxApplicationShell.active();
        return shell == null || shell.audioOutputAvailable();
    }

    /** Cancels active and queued speech without closing the reusable worker. */
    void stop() {
        generation.incrementAndGet();
        CompletableFuture<Boolean> completion = activeCompletion;
        if (completion == null) return;
        abortPlayback(completion);
    }

    static String cleanChatMessage(String message) {
        String cleaned = Objects.requireNonNullElse(message, "");
        cleaned = BBCODE_I.matcher(cleaned).replaceAll("$2");
        cleaned = BBCODE_B.matcher(cleaned).replaceAll("$2");
        cleaned = BBCODE_COLOR.matcher(cleaned).replaceAll("$3");
        cleaned = LINK_OR_IMAGE.matcher(cleaned).replaceAll("");
        cleaned = EMOJI.matcher(cleaned).replaceAll("");
        return cleaned.trim();
    }

    static String serviceText(String message) {
        return Objects.requireNonNullElse(message, "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9áéíóúñü@& ,.:;!?¡¿<>]", "")
                .replaceAll(" {2,}", " ").trim();
    }

    private static byte[] download(String text, String language)
            throws Exception {
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String[] services = "es".equals(language)
                ? new String[]{
                    "http://translate.google.com/translate_tts?ie=UTF-8&total=1&idx=0&textlen=32&client=tw-ob&tl=es&q=" + encoded,
                    "https://text-to-speech-demo.ng.bluemix.net/api/v3/synthesize?text=" + encoded
                    + "&voice=es-ES_LauraVoice&download=true&accept=audio%2Fmp3"}
                : new String[]{
                    "http://translate.google.com/translate_tts?ie=UTF-8&total=1&idx=0&textlen=32&client=tw-ob&tl=en&q=" + encoded,
                    "https://text-to-speech-demo.ng.bluemix.net/api/v3/synthesize?text=" + encoded
                    + "&voice=en-US_AllisonVoice&download=true&accept=audio%2Fmp3"};
        for (String service : services) {
            try {
                return read(service);
            } catch (Exception ignored) {
                if (Thread.currentThread().isInterrupted()) throw ignored;
            }
        }
        String punctuated = text.matches(".*[?!.]$") ? text : text + ".";
        String nested = URLEncoder.encode(URLEncoder.encode(punctuated,
                StandardCharsets.UTF_8).replace("+", "%20"),
                StandardCharsets.UTF_8);
        byte[] response = read("https://www.google.com/async/translate_tts?client=firefox-b-d&yv=3&ttsp=tl:"
                + language + ",txt:" + nested + ",spd:1&async=_fmt:jspb");
        Matcher matcher = BASE64_RESPONSE.matcher(new String(response,
                StandardCharsets.UTF_8));
        if (!matcher.find()) throw new IllegalStateException("Invalid TTS response");
        return Base64.getDecoder().decode(matcher.group(1));
    }

    private static byte[] read(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address)
                .openConnection();
        connection.addRequestProperty("User-Agent", USER_AGENT);
        connection.setUseCaches(false);
        connection.setConnectTimeout(IO_TIMEOUT_MILLIS);
        connection.setReadTimeout(IO_TIMEOUT_MILLIS);
        try (InputStream input = connection.getInputStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            for (int count; (count = input.read(buffer)) >= 0;) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("table teardown");
                }
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IllegalStateException("TTS response too large");
                }
                if (count > 0) output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        generation.incrementAndGet();
        worker.shutdownNow();
        Music music = activeMusic;
        CompletableFuture<Boolean> completion = activeCompletion;
        if (music != null && completion != null) {
            finishPlayback(music, completion, false);
        } else {
            activeMusic = null;
            activeCompletion = null;
            if (music != null) {
                disposeMusic(music);
                ducking.accept(false);
            }
        }
    }
}

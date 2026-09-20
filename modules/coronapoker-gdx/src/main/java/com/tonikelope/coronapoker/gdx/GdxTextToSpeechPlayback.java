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
import java.util.concurrent.atomic.AtomicBoolean;
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
    private volatile Music activeMusic;

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
                || closed.get()) return CompletableFuture.completedFuture(false);
        String speechLanguage = "es".equalsIgnoreCase(language) ? "es" : "en";
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        worker.execute(() -> {
            boolean played = false;
            try {
                played = GdxSpokenAudioGate.call(() -> fetchAndPlay(
                        speech, speechLanguage, playbackStarted));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
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
            music.setVolume(ttsVolume(volume.getAsDouble()));
        }
    }

    static float ttsVolume(double masterVolume) {
        return (float) Math.max(0d, Math.min(1d, masterVolume * 2d));
    }

    private boolean fetchAndPlay(String speech, String language,
            Runnable playbackStarted) {
        Path mp3 = null;
        try {
            byte[] audio = download(speech, language);
            if (audio.length == 0 || closed.get()
                    || Thread.currentThread().isInterrupted()) return false;
            mp3 = Files.createTempFile("coronapoker-gdx-tts-", ".mp3");
            Files.write(mp3, audio);
            CompletableFuture<Boolean> finished = new CompletableFuture<>();
            Path playbackFile = mp3;
            Gdx.app.postRunnable(() -> startPlayback(playbackFile, finished,
                    playbackStarted));
            return finished.get(2, TimeUnit.MINUTES);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
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
            Runnable playbackStarted) {
        if (closed.get()) {
            finished.complete(false);
            return;
        }
        try {
            Music music = Gdx.audio.newMusic(Gdx.files.absolute(
                    mp3.toAbsolutePath().toString()));
            activeMusic = music;
            ducking.accept(true);
            music.setVolume(ttsVolume(volume.getAsDouble()));
            AtomicBoolean ended = new AtomicBoolean();
            music.setOnCompletionListener(ignored -> finishPlayback(
                    music, finished, ended));
            music.play();
            try {
                playbackStarted.run();
            } catch (RuntimeException ignored) {
                // A visual notification must never abort audible playback.
            }
        } catch (RuntimeException failure) {
            activeMusic = null;
            ducking.accept(false);
            finished.complete(false);
        }
    }

    private void finishPlayback(Music music,
            CompletableFuture<Boolean> finished,
            AtomicBoolean ended) {
        if (!ended.compareAndSet(false, true)) return;
        if (activeMusic == music) activeMusic = null;
        try {
            music.stop();
            music.dispose();
        } finally {
            ducking.accept(false);
            finished.complete(true);
        }
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
        worker.shutdownNow();
        Music music = activeMusic;
        activeMusic = null;
        if (music != null) {
            try {
                music.stop();
                music.dispose();
            } finally {
                ducking.accept(false);
            }
        }
    }
}

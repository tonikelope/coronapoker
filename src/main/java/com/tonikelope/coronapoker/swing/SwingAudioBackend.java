package com.tonikelope.coronapoker.swing;

import com.tonikelope.coronapoker.Audio;
import com.tonikelope.coronapoker.CoronaMP3FilePlayer;
import com.tonikelope.coronapoker.GameFrame;
import com.tonikelope.coronapoker.Helpers;
import com.tonikelope.coronapoker.core.AudioService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Classic audio adapter invoked through the shared process lifecycle. */
final class SwingAudioBackend implements AudioService.Backend {

    private static final Logger LOGGER = Logger.getLogger(SwingAudioBackend.class.getName());
    private final AtomicBoolean closed = new AtomicBoolean();

    @Override
    public void start() {
        float masterVolume;
        try {
            masterVolume = Float.parseFloat(Helpers.PROPERTIES.getProperty("master_volume", "0.8"));
        } catch (NumberFormatException ex) {
            masterVolume = Float.NaN;
        }
        if (Float.isNaN(masterVolume) || masterVolume < 0f || masterVolume > 1f) {
            LOGGER.log(Level.WARNING, "Invalid master_volume property, falling back to default.");
            masterVolume = 0.8f;
        }

        Audio.MASTER_VOLUME = masterVolume;
        if (!GameFrame.SONIDOS) {
            Audio.muteAll();
        } else {
            Audio.unmuteAll();
        }

        Helpers.applicationTask(() -> {
            if (closed.get()) {
                return;
            }
            Audio.warmAudioDevice();
            if (closed.get()) {
                return;
            }
            Audio.playWavResourceAndWait("misc/init.wav", true, false, !GameFrame.arranqueSonidoOn());
            if (!closed.get()) {
                Audio.preloadWav("misc/uncover.wav");
            }
        }, "CoronaPoker-audio-warmup");

        Audio.playLoopMp3Resource("misc/background_music.mp3");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Audio.VOLUME_TIMER.stop();
        CoronaMP3FilePlayer ttsPlayer = Audio.TTS_PLAYER;
        if (ttsPlayer != null) {
            try {
                ttsPlayer.stop();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Could not stop TTS audio during shutdown", ex);
            }
        }
        Audio.stopPreview();
        Audio.stopDangerAlertLoop();
        Audio.stopAllCurrentLoopMp3Resource();
        Audio.stopAllWavResources();
        Audio.closeAllPreloadedWavs();
    }
}

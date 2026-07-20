/*
 * Copyright (C) 2026 tonikelope
 _              _ _        _
| |_ ___  _ __ (_) | _____| | ___  _ __   ___
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___
|___ \  / _ \|___ \  / _ \
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/

https://github.com/tonikelope/coronapoker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * Single acquisition point for every audio line in the game. Lines come from
 * the user-selected mixer (persisted by name in the properties file) or from
 * the system default when no selection exists or the selected device is gone.
 *
 * @author tonikelope
 */
public class AudioDeviceManager {

    public static final String DEFAULT_DEVICE = "";

    private static volatile String OUTPUT_DEVICE = Helpers.PROPERTIES.getProperty("audio_output_device", DEFAULT_DEVICE);
    private static volatile String CAPTURE_DEVICE = Helpers.PROPERTIES.getProperty("audio_capture_device", DEFAULT_DEVICE);
    private static volatile boolean MIC_ENABLED = micEnabledDefault();
    private static volatile boolean PLAY_OWN_VOICE_MESSAGES = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("audio_play_own_voice", "true"));
    private static volatile boolean BLOCK_VOICE_MESSAGES = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("audio_block_voice_messages", "false"));
    private static volatile boolean BLOCK_TTS_LOCAL = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("audio_block_tts_local", "false"));

    // Days a persisted voice note survives on disk before the startup purge
    // drops it. 0 means "keep forever" (never purged).
    public static final int VOICE_NOTE_RETENTION_KEEP_FOREVER = 0;
    public static final int[] VOICE_NOTE_RETENTION_OPTIONS = {7, 15, 30, 90, VOICE_NOTE_RETENTION_KEEP_FOREVER};
    private static volatile int VOICE_NOTE_RETENTION_DAYS = voiceNoteRetentionDaysDefault();

    private static int voiceNoteRetentionDaysDefault() {

        try {
            int days = Integer.parseInt(Helpers.PROPERTIES.getProperty("audio_voice_note_retention_days", "90"));

            for (int option : VOICE_NOTE_RETENTION_OPTIONS) {
                if (option == days) {
                    return days;
                }
            }
        } catch (NumberFormatException ex) {
        }

        return 90;
    }

    private static boolean micEnabledDefault() {

        String prop = Helpers.PROPERTIES.getProperty("audio_mic_enabled");

        if (prop != null) {
            // The user already made a choice: respect it
            return Boolean.parseBoolean(prop);
        }

        // Default: enabled whenever the system has a capture device
        return !getCaptureDevices().isEmpty();
    }

    private static volatile boolean OUTPUT_FALLBACK_WARNED = false;

    public static String getOutputDevice() {
        return OUTPUT_DEVICE;
    }

    public static String getCaptureDevice() {
        return CAPTURE_DEVICE;
    }

    public static boolean isMicEnabled() {
        return MIC_ENABLED;
    }

    public static void setOutputDevice(String device) {

        OUTPUT_DEVICE = device != null ? device : DEFAULT_DEVICE;

        OUTPUT_FALLBACK_WARNED = false;

        Helpers.PROPERTIES.setProperty("audio_output_device", OUTPUT_DEVICE);

        Helpers.savePropertiesFile();
    }

    public static void setCaptureDevice(String device) {

        CAPTURE_DEVICE = device != null ? device : DEFAULT_DEVICE;

        Helpers.PROPERTIES.setProperty("audio_capture_device", CAPTURE_DEVICE);

        Helpers.savePropertiesFile();
    }

    public static void setMicEnabled(boolean enabled) {

        MIC_ENABLED = enabled;

        Helpers.PROPERTIES.setProperty("audio_mic_enabled", String.valueOf(enabled));

        Helpers.savePropertiesFile();
    }

    public static boolean isPlayOwnVoiceMessages() {
        return PLAY_OWN_VOICE_MESSAGES;
    }

    public static boolean isBlockVoiceMessages() {
        return BLOCK_VOICE_MESSAGES;
    }

    public static void setBlockVoiceMessages(boolean blocked) {

        BLOCK_VOICE_MESSAGES = blocked;

        Helpers.PROPERTIES.setProperty("audio_block_voice_messages", String.valueOf(blocked));

        Helpers.savePropertiesFile();
    }

    public static boolean isBlockTtsLocal() {
        return BLOCK_TTS_LOCAL;
    }

    public static void setBlockTtsLocal(boolean blocked) {

        BLOCK_TTS_LOCAL = blocked;

        Helpers.PROPERTIES.setProperty("audio_block_tts_local", String.valueOf(blocked));

        Helpers.savePropertiesFile();
    }

    public static void setPlayOwnVoiceMessages(boolean enabled) {

        PLAY_OWN_VOICE_MESSAGES = enabled;

        Helpers.PROPERTIES.setProperty("audio_play_own_voice", String.valueOf(enabled));

        Helpers.savePropertiesFile();
    }

    public static int getVoiceNoteRetentionDays() {
        return VOICE_NOTE_RETENTION_DAYS;
    }

    public static void setVoiceNoteRetentionDays(int days) {

        VOICE_NOTE_RETENTION_DAYS = days;

        Helpers.PROPERTIES.setProperty("audio_voice_note_retention_days", String.valueOf(days));

        Helpers.savePropertiesFile();
    }

    public static List<Mixer.Info> getOutputDevices() {
        return getDevices(SourceDataLine.class);
    }

    public static List<Mixer.Info> getCaptureDevices() {
        return getDevices(TargetDataLine.class);
    }

    private static List<Mixer.Info> getDevices(Class<? extends Line> line_class) {

        ArrayList<Mixer.Info> devices = new ArrayList<>();

        for (Mixer.Info info : AudioSystem.getMixerInfo()) {

            try {
                if (AudioSystem.getMixer(info).isLineSupported(new Line.Info(line_class))) {
                    devices.add(info);
                }
            } catch (Exception ex) {
                Logger.getLogger(AudioDeviceManager.class.getName()).log(Level.FINE, "Skipping mixer {0}: {1}", new Object[]{info.getName(), ex.getMessage()});
            }
        }

        return devices;
    }

    public static Clip getClip() throws LineUnavailableException {

        Mixer.Info info = findMixerInfo(OUTPUT_DEVICE, SourceDataLine.class);

        if (info != null) {
            try {
                return AudioSystem.getClip(info);
            } catch (Exception ex) {
                warnOutputFallback(ex);
            }
        }

        return AudioSystem.getClip();
    }

    public static SourceDataLine getSourceDataLine(AudioFormat format) throws LineUnavailableException {

        Mixer.Info info = findMixerInfo(OUTPUT_DEVICE, SourceDataLine.class);

        if (info != null) {
            try {
                return AudioSystem.getSourceDataLine(format, info);
            } catch (Exception ex) {
                warnOutputFallback(ex);
            }
        }

        return AudioSystem.getSourceDataLine(format);
    }

    public static TargetDataLine getTargetDataLine(AudioFormat format) throws LineUnavailableException {

        return getTargetDataLine(format, true);
    }

    /**
     * The capture line for the device selected in the audio settings. Without
     * fallback it fails instead of silently capturing from another microphone,
     * which is what resuming a note mid-recording needs: the device going away
     * is precisely what makes the lookup fail, and finishing the note through
     * the laptop array without telling anybody is worse than not finishing it.
     */
    public static TargetDataLine getTargetDataLine(AudioFormat format, boolean allow_fallback) throws LineUnavailableException {

        Mixer.Info info = findMixerInfo(CAPTURE_DEVICE, TargetDataLine.class);

        if (info != null) {
            try {
                return AudioSystem.getTargetDataLine(format, info);
            } catch (Exception ex) {
                if (!allow_fallback) {
                    throw new LineUnavailableException("Selected capture device failed: " + ex.getMessage());
                }
                Logger.getLogger(AudioDeviceManager.class.getName()).log(Level.WARNING, "Selected capture device failed ({0}). Falling back to system default.", ex.getMessage());
            }
        } else if (CAPTURE_DEVICE != null && !CAPTURE_DEVICE.isEmpty()) {
            // Windows renames endpoints when a Bluetooth or USB mic reconnects,
            // so the configured name stops matching and the note is recorded
            // from whatever the system default is. Worth knowing about.
            if (!allow_fallback) {
                throw new LineUnavailableException("Selected capture device is gone: " + CAPTURE_DEVICE);
            }
            Logger.getLogger(AudioDeviceManager.class.getName()).log(Level.WARNING, "Selected capture device not found ({0}). Using the system default.", CAPTURE_DEVICE);
        }

        return AudioSystem.getTargetDataLine(format);
    }

    private static Mixer.Info findMixerInfo(String device, Class<? extends Line> line_class) {

        if (device == null || device.isEmpty()) {
            return null;
        }

        for (Mixer.Info info : AudioSystem.getMixerInfo()) {

            if (device.equals(info.getName())) {

                try {
                    if (AudioSystem.getMixer(info).isLineSupported(new Line.Info(line_class))) {
                        return info;
                    }
                } catch (Exception ex) {
                    Logger.getLogger(AudioDeviceManager.class.getName()).log(Level.FINE, "Mixer lookup failed for {0}: {1}", new Object[]{device, ex.getMessage()});
                }
            }
        }

        return null;
    }

    private static void warnOutputFallback(Exception ex) {

        // One warning per selection: a vanished device would otherwise spam the
        // log on every sound effect.
        if (!OUTPUT_FALLBACK_WARNED) {
            OUTPUT_FALLBACK_WARNED = true;
            Logger.getLogger(AudioDeviceManager.class.getName()).log(Level.WARNING, "Selected output device failed ({0}). Falling back to system default.", ex.getMessage());
        }
    }

    private AudioDeviceManager() {
    }

}

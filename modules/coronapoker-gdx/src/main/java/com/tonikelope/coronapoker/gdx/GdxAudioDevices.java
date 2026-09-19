/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/** OpenAL output and Java Sound microphone selection for GDX. */
final class GdxAudioDevices {

    static final String OUTPUT_KEY = "gdx_audio_output_device";
    static final String CAPTURE_KEY = "audio_capture_device";
    private static final String DEFAULT = "";
    private static volatile List<String> captureDevices = List.of();

    static {
        refreshCaptureDevicesAsync();
    }

    static void refreshCaptureDevicesAsync() {
        Thread worker = new Thread(
                () -> captureDevices = discoverCaptureDevices(),
                "CoronaPoker-GDX-audio-devices");
        worker.setDaemon(true);
        worker.start();
    }

    static boolean hasCaptureDevices() {
        return !captureDevices.isEmpty();
    }

    static String outputLabel(Properties properties, GdxGameText text) {
        return label(properties.getProperty(OUTPUT_KEY, DEFAULT), text);
    }

    static String captureLabel(Properties properties, GdxGameText text) {
        return label(properties.getProperty(CAPTURE_KEY, DEFAULT), text);
    }

    static boolean cycleOutput(Properties properties) {
        return adjustOutput(properties, 1);
    }

    static boolean adjustOutput(Properties properties, int direction) {
        String next = adjacentDevice(availableOutputDevices(),
                properties.getProperty(OUTPUT_KEY, DEFAULT), direction);
        try {
            if (Gdx.audio == null || !Gdx.audio.switchOutputDevice(
                    next.isEmpty() ? null : next)) return false;
            properties.setProperty(OUTPUT_KEY, next);
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    static void applyConfiguredOutput(Properties properties) {
        String selected = properties.getProperty(OUTPUT_KEY, DEFAULT);
        try {
            if (Gdx.audio != null && !Gdx.audio.switchOutputDevice(
                    selected.isEmpty() ? null : selected)) {
                properties.setProperty(OUTPUT_KEY, DEFAULT);
                Gdx.audio.switchOutputDevice(null);
            }
        } catch (RuntimeException unavailable) {
            properties.setProperty(OUTPUT_KEY, DEFAULT);
        }
    }

    /**
     * OpenAL restarts every active stream when its output device is switched.
     * Keep this comparison in one place so transactional Settings cancel paths
     * never reopen the same device and introduce an avoidable music dropout.
     */
    static boolean outputSelectionChanged(String preview, String restored) {
        return !Objects.equals(normalizeDevice(preview),
                normalizeDevice(restored));
    }

    static void cycleCapture(Properties properties) {
        adjustCapture(properties, 1);
    }

    static void adjustCapture(Properties properties, int direction) {
        properties.setProperty(CAPTURE_KEY, adjacentDevice(captureDevices,
                properties.getProperty(CAPTURE_KEY, DEFAULT), direction));
    }

    static TargetDataLine openCapture(Properties properties,
            AudioFormat format) throws Exception {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(format, "format");
        String selected = properties.getProperty(CAPTURE_KEY, DEFAULT);
        if (selected.isEmpty()) return AudioSystem.getTargetDataLine(format);
        DataLine.Info requested = new DataLine.Info(TargetDataLine.class,
                format);
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (selected.equals(info.getName())) {
                Mixer mixer = AudioSystem.getMixer(info);
                if (!mixer.isLineSupported(requested)) break;
                return (TargetDataLine) mixer.getLine(requested);
            }
        }
        throw new javax.sound.sampled.LineUnavailableException(
                "Selected capture device is unavailable: " + selected);
    }

    static String nextDevice(List<String> devices, String current) {
        return adjacentDevice(devices, current, 1);
    }

    static String adjacentDevice(List<String> devices, String current,
            int direction) {
        List<String> safe = devices == null ? List.of() : devices;
        ArrayList<String> choices = new ArrayList<>(safe.size() + 1);
        choices.add(DEFAULT);
        safe.stream().filter(Objects::nonNull).map(String::strip)
                .filter(name -> !name.isEmpty()).distinct()
                .forEach(choices::add);
        String selected = normalizeDevice(current);
        int index = choices.indexOf(selected);
        // A device may disappear while Settings is open. In that case always
        // recover to the system default instead of guessing another output.
        if (index < 0) return DEFAULT;
        return choices.get(Math.floorMod(index + Integer.signum(direction),
                choices.size()));
    }

    private static String normalizeDevice(String device) {
        return device == null ? DEFAULT : device.strip();
    }

    private static String label(String device, GdxGameText text) {
        return device == null || device.isBlank()
                ? text.translate("gdx.settings.value.system_default")
                        .toUpperCase(java.util.Locale.ROOT)
                : device;
    }

    private static List<String> availableOutputDevices() {
        try {
            if (Gdx.audio == null) return List.of();
            String[] devices = Gdx.audio.getAvailableOutputDevices();
            return devices == null ? List.of() : Arrays.stream(devices)
                    .filter(Objects::nonNull).map(String::trim)
                    .filter(name -> !name.isEmpty()).distinct().toList();
        } catch (RuntimeException unavailable) {
            return List.of();
        }
    }

    private static List<String> discoverCaptureDevices() {
        ArrayList<String> devices = new ArrayList<>();
        DataLine.Info requested = new DataLine.Info(TargetDataLine.class,
                GdxVoiceRecorder.PCM_FORMAT);
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(info).isLineSupported(requested)) {
                    devices.add(info.getName());
                }
            } catch (RuntimeException ignored) {
            }
        }
        return List.copyOf(devices);
    }

    private GdxAudioDevices() {
    }
}

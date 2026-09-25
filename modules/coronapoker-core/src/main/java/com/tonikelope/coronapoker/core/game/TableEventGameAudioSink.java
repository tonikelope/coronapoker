/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.table.TableEventBridge;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Routes dealer-owned audio to the attached renderer.
 *
 * <p>Sounds whose timing belongs to a visual animation can be excluded: GDX
 * then plays those exactly on card/chip contact, while every other canonical
 * cue (including funny mod sounds) still follows the shared dealer flow.</p>
 */
public final class TableEventGameAudioSink implements GameAudioSink {

    private final TableEventBridge events;
    private final Set<String> rendererOwned;

    public TableEventGameAudioSink(TableEventBridge events,
            Set<String> rendererOwned) {
        this.events = Objects.requireNonNull(events, "events");
        this.rendererOwned = Set.copyOf(Objects.requireNonNull(
                rendererOwned, "rendererOwned"));
    }

    @Override
    public void playRandomWavResource(Map<String, String[]> sounds) {
        String selected = randomResource(sounds);
        if (selected != null) publish(TableVisualEvent.AudioCue.Operation.PLAY,
                selected, false, true, false, false);
    }

    @Override
    public void playRandomWavResourceAndWait(Map<String, String[]> sounds) {
        String selected = randomResource(sounds);
        if (selected != null) publish(TableVisualEvent.AudioCue.Operation.PLAY,
                selected, true, true, false, false);
    }

    @Override
    public boolean playWavResourceAndWait(String sound) {
        return publish(TableVisualEvent.AudioCue.Operation.PLAY, sound, true,
                true, false, false);
    }

    @Override
    public boolean playWavResourceAndWait(String sound, boolean forceClose,
            boolean bypassMuted, boolean forceSilent) {
        return publish(TableVisualEvent.AudioCue.Operation.PLAY, sound, true,
                forceClose, bypassMuted, forceSilent);
    }

    @Override
    public void playLoopMp3Resource(String sound) {
        publish(TableVisualEvent.AudioCue.Operation.PLAY_LOOP, sound, false,
                true, false, false);
    }

    @Override
    public void playWavResource(String sound) {
        publish(TableVisualEvent.AudioCue.Operation.PLAY, sound, false,
                true, false, false);
    }

    @Override
    public void playWavResource(String sound, boolean forceClose) {
        publish(TableVisualEvent.AudioCue.Operation.PLAY, sound, false,
                forceClose, false, false);
    }

    @Override
    public void stopWavResource(String sound) {
        publish(TableVisualEvent.AudioCue.Operation.STOP, sound, false,
                true, false, false);
    }

    @Override
    public void startDangerAlertLoop(String sound) {
        publish(TableVisualEvent.AudioCue.Operation.START_DANGER_LOOP, sound,
                false, true, false, false);
    }

    @Override
    public void stopDangerAlertLoop() {
        publish(TableVisualEvent.AudioCue.Operation.STOP_DANGER_LOOP, "",
                false, true, false, false);
    }

    @Override
    public void playPreloadedWav(String sound) {
        playWavResource(sound);
    }

    @Override
    public void stopPreloadedWav(String sound) {
        stopWavResource(sound);
    }

    @Override
    public void stopLoopMp3(String sound) {
        publish(TableVisualEvent.AudioCue.Operation.STOP_LOOP, sound, false,
                true, false, false);
    }

    @Override
    public void unmuteLoopMp3(String sound) {
        publish(TableVisualEvent.AudioCue.Operation.PLAY_LOOP, sound, false,
                true, false, false);
    }

    @Override
    public void muteAllLoopMp3() {
        publish(TableVisualEvent.AudioCue.Operation.MUTE_LOOPS, "", false,
                true, false, false);
    }

    @Override
    public void unmuteAllLoopMp3() {
        publish(TableVisualEvent.AudioCue.Operation.UNMUTE_LOOPS, "", false,
                true, false, false);
    }

    private boolean publish(TableVisualEvent.AudioCue.Operation operation,
            String resource, boolean wait, boolean forceClose,
            boolean bypassMuted, boolean forceSilent) {
        String normalized = normalize(resource);
        if (operation.requiresResource()
                && (normalized.isEmpty() || rendererOwns(normalized))) {
            return true;
        }
        try {
            var barrier = events.publish(sequence ->
                    new TableVisualEvent.AudioCue(sequence, operation,
                            normalized, wait, forceClose, bypassMuted,
                            forceSilent));
            if (wait) barrier.toCompletableFuture().join();
            return true;
        } catch (CompletionException | IllegalStateException failure) {
            return false;
        }
    }

    private boolean rendererOwns(String resource) {
        return rendererOwned.contains(resource)
                || resource.startsWith("allin/");
    }

    private static String randomResource(Map<String, String[]> sounds) {
        if (sounds == null || sounds.isEmpty()) return null;
        ArrayList<String> resources = new ArrayList<>();
        sounds.forEach((folder, files) -> {
            String normalizedFolder = normalize(folder);
            if (!normalizedFolder.isEmpty() && !normalizedFolder.endsWith("/")) {
                normalizedFolder += "/";
            }
            if (files == null) return;
            for (String file : files) {
                String normalizedFile = normalize(file);
                if (!normalizedFile.isEmpty()) {
                    resources.add(normalizedFolder + normalizedFile);
                }
            }
        });
        return resources.isEmpty() ? null : resources.get(
                ThreadLocalRandom.current().nextInt(resources.size()));
    }

    private static String normalize(String resource) {
        if (resource == null) return "";
        String normalized = resource.trim().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }
}

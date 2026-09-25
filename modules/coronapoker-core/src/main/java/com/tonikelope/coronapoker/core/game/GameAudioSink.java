/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.Map;

/** Audio operations requested by the canonical game, independent of backend. */
public interface GameAudioSink {

    default void playRandomWavResource(Map<String, String[]> sounds) { }
    default void playRandomWavResourceAndWait(Map<String, String[]> sounds) { }
    default boolean playWavResourceAndWait(String sound) { return true; }
    default boolean playWavResourceAndWait(String sound, boolean forceClose,
            boolean bypassMuted, boolean forceSilent) { return true; }
    default void playLoopMp3Resource(String sound) { }
    default void playWavResource(String sound) { }
    default void playWavResource(String sound, boolean forceClose) { }
    default void stopWavResource(String sound) { }
    default void startDangerAlertLoop(String sound) { }
    default void stopDangerAlertLoop() { }
    default void playPreloadedWav(String sound) { }
    default void stopPreloadedWav(String sound) { }
    default void stopLoopMp3(String sound) { }
    default void unmuteLoopMp3(String sound) { }
    default void muteAllLoopMp3() { }
    default void unmuteAllLoopMp3() { }

    static GameAudioSink silent() {
        return new GameAudioSink() { };
    }
}

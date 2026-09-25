/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameAudioSink;
import java.util.Map;

/** Classic audio backend retained by the Swing frontend. */
final class SwingGameAudio implements GameAudioSink {

    @Override public void playRandomWavResource(Map<String, String[]> sounds) {
        Audio.playRandomWavResource(sounds);
    }
    @Override public void playRandomWavResourceAndWait(Map<String, String[]> sounds) {
        Audio.playRandomWavResourceAndWait(sounds);
    }
    @Override public boolean playWavResourceAndWait(String sound) {
        return Audio.playWavResourceAndWait(sound);
    }
    @Override public boolean playWavResourceAndWait(String sound, boolean forceClose,
            boolean bypassMuted, boolean forceSilent) {
        return Audio.playWavResourceAndWait(sound, forceClose, bypassMuted, forceSilent);
    }
    @Override public void playLoopMp3Resource(String sound) {
        Audio.playLoopMp3Resource(sound);
    }
    @Override public void playWavResource(String sound) {
        Audio.playWavResource(sound);
    }
    @Override public void playWavResource(String sound, boolean forceClose) {
        Audio.playWavResource(sound, forceClose);
    }
    @Override public void stopWavResource(String sound) {
        Audio.stopWavResource(sound);
    }
    @Override public void startDangerAlertLoop(String sound) {
        Audio.startDangerAlertLoop(sound);
    }
    @Override public void stopDangerAlertLoop() {
        Audio.stopDangerAlertLoop();
    }
    @Override public void playPreloadedWav(String sound) {
        Audio.playPreloadedWav(sound);
    }
    @Override public void stopPreloadedWav(String sound) {
        Audio.stopPreloadedWav(sound);
    }
    @Override public void stopLoopMp3(String sound) {
        Audio.stopLoopMp3(sound);
    }
    @Override public void unmuteLoopMp3(String sound) {
        Audio.unmuteLoopMp3(sound);
    }
    @Override public void muteAllLoopMp3() {
        Audio.muteAllLoopMp3();
    }
    @Override public void unmuteAllLoopMp3() {
        Audio.unmuteAllLoopMp3();
    }
}

/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;

/** Frontend-owned lookup for optional all-in cinematic media. */
public interface GameCinematicAssets {

    long durationMillis(String filename) throws Exception;
    boolean hasCinematic(String filename);
    boolean hasCompanionAudio(String filename);
    /** Ordered local all-in cinematic catalog; empty keeps the legacy catalog. */
    default List<String> filenames() { return List.of(); }

    static GameCinematicAssets none() {
        return EmptyAssets.INSTANCE;
    }

    final class EmptyAssets implements GameCinematicAssets {
        private static final EmptyAssets INSTANCE = new EmptyAssets();
        private EmptyAssets() { }
        @Override public long durationMillis(String filename) { return 0L; }
        @Override public boolean hasCinematic(String filename) { return false; }
        @Override public boolean hasCompanionAudio(String filename) { return false; }
    }
}

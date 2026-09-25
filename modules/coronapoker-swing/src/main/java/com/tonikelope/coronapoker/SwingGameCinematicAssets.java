/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameCinematicAssets;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/** Exact MOD-first cinematic lookup used by the classic frontend. */
final class SwingGameCinematicAssets implements GameCinematicAssets {

    private Path modAsset(String filename) {
        return Path.of(Helpers.getCurrentJarParentPath(), "mod", "cinematics", "allin", filename);
    }

    private URL bundledAsset(String filename) {
        return SwingGameCinematicAssets.class.getResource("/cinematics/allin/" + filename);
    }

    @Override
    public long durationMillis(String filename) throws Exception {
        Path mod = modAsset(filename);
        if (Files.exists(mod)) {
            return Helpers.getGIFLength(mod.toUri().toURL());
        }
        URL bundled = bundledAsset(filename);
        return bundled == null ? 0L : Helpers.getGIFLength(bundled.toURI().toURL());
    }

    @Override
    public boolean hasCinematic(String filename) {
        return (Init.MOD != null && Files.exists(modAsset(filename)))
                || bundledAsset(filename) != null;
    }

    @Override
    public boolean hasCompanionAudio(String filename) {
        String wav = filename.replaceAll("\\.gif$", ".wav");
        return Files.exists(modAsset(wav)) || bundledAsset(wav) != null;
    }
}

/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** One MOD-aware resolver for frontend and live-table audio streams. */
final class GdxGameAudioResources {

    private static final List<String> JOKE_CATEGORIES = List.of(
            "allin", "fold", "showdown", "loser", "winner");

    private GdxGameAudioResources() {
    }

    static FileHandle resolve(GdxGamePresentationSettings settings,
            String resource) {
        String normalized = normalize(resource);
        if (normalized == null) return null;
        Optional<Path> external = modOverride(settings, normalized);
        if (external.isPresent()) {
            return Gdx.files.absolute(external.orElseThrow().toString());
        }
        FileHandle bundledSound = Gdx.files.internal("sounds/" + normalized);
        if (bundledSound.exists()) return bundledSound;
        FileHandle bundledCinematic = Gdx.files.internal(
                "cinematics/" + normalized);
        return bundledCinematic.exists() ? bundledCinematic : null;
    }

    static Optional<Path> modOverride(GdxGamePresentationSettings settings,
            String resource) {
        String normalized = normalize(resource);
        if (settings == null || normalized == null) return Optional.empty();
        Optional<Path> sound = settings.modAsset("sounds/" + normalized);
        return sound.isPresent() ? sound
                : settings.modAsset("cinematics/" + normalized);
    }

    /** Whether the setting will use media supplied by the active MOD. */
    static boolean modOverridesSetting(GdxGamePresentationSettings settings,
            GdxSettingsContract.ToggleOption option, String language) {
        if (settings == null || option == null || !settings.modActive()) {
            return false;
        }
        if ("sonidos_chorra".equals(option.key())) {
            String selected = "en".equalsIgnoreCase(language) ? "en" : "es";
            return JOKE_CATEGORIES.stream().anyMatch(category ->
                    !settings.modFiles("sounds/joke/" + selected + "/"
                            + category, ".wav").isEmpty());
        }
        GdxSettingsContract.AudioPreview preview =
                GdxSettingsContract.audioPreview(option,
                        language == null ? "es"
                                : language.toLowerCase(Locale.ROOT));
        return preview != null
                && modOverride(settings, preview.resource()).isPresent();
    }

    private static String normalize(String resource) {
        String normalized = resource == null ? ""
                : resource.replace('\\', '/');
        if (normalized.startsWith("sounds/")) {
            normalized = normalized.substring("sounds/".length());
        } else if (normalized.startsWith("cinematics/")) {
            normalized = normalized.substring("cinematics/".length());
        }
        if (normalized.isBlank() || normalized.startsWith("/")
                || normalized.contains("../")) return null;
        return normalized;
    }
}

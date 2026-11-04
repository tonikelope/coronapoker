/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.tonikelope.coronapoker.core.game.GamePresentationSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** One MOD-aware resolver for frontend and live-table audio streams. */
final class GdxGameAudioResources {

    enum JokeBankMode {
        OFFICIAL,
        MOD_ONLY,
        FUSED
    }

    private static final List<String> JOKE_CATEGORIES = List.of(
            "allin", "fold", "showdown", "loser", "winner");

    private static final List<String> JOKE_CATEGORY_KEYS = List.of(
            "sonido_chorra_allin", "sonido_chorra_fold",
            "sonido_chorra_showdown", "sonido_chorra_winner",
            "sonido_chorra_loser");

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

    /**
     * Builds the language-specific joke-sound catalogue. Every event bank is
     * followed by its concrete WAVs in deterministic filename order.
     */
    static GdxSettingsContract.TogglePage jokeSoundsPage(
            GdxGamePresentationSettings settings, String language,
            GdxSettingsContract.TogglePage base) {
        if (settings == null || base == null) return base;
        ArrayList<GdxSettingsContract.ToggleOption> options =
                new ArrayList<>();
        if (!base.options().isEmpty()) options.add(base.options().get(0));
        for (String categoryKey : JOKE_CATEGORY_KEYS) {
            GdxSettingsContract.ToggleOption parent = base.options().stream()
                    .filter(option -> categoryKey.equals(option.key()))
                    .findFirst().orElse(null);
            if (parent == null) continue;
            String category = jokeCategory(categoryKey);
            List<String> resources = settings.jokeSoundResources(category);
            if (resources.isEmpty()) continue;
            options.add(parent);
            String childLabel = jokeCategoryLabel(category, language);
            for (int index = 0; index < resources.size(); index++) {
                String resource = resources.get(index);
                options.add(new GdxSettingsContract.ToggleOption(
                        GamePresentationSettings.jokeSoundPreferenceKey(
                                resource),
                        childLabel + "-" + (index + 1) + " · "
                                + jokeFilename(resource), true,
                        GdxSettingsContract.Gate.FUNNY_SOUNDS, false,
                        categoryKey));
            }
        }
        base.options().stream()
                .filter(option -> !"sonidos_chorra".equals(option.key()))
                .filter(option -> !JOKE_CATEGORY_KEYS.contains(option.key()))
                .forEach(options::add);
        return new GdxSettingsContract.TogglePage(base.title(),
                List.copyOf(options));
    }

    private static String jokeCategoryLabel(String category,
            String language) {
        boolean english = "en".equalsIgnoreCase(language);
        return switch (category) {
            case "allin" -> "ALL-IN";
            case "fold" -> english ? "FOLD" : "RETIRARSE";
            case "showdown" -> "SHOWDOWN";
            case "winner" -> english ? "WINNER" : "GANADOR";
            case "loser" -> english ? "LOSER" : "PERDEDOR";
            default -> english ? "SOUND" : "SONIDO";
        };
    }

    static String jokeResource(GdxSettingsContract.ToggleOption option) {
        return option == null ? null
                : GamePresentationSettings.jokeSoundResource(option.key());
    }

    static String jokeFilename(String resource) {
        String normalized = normalize(resource);
        if (normalized == null) return "";
        int separator = normalized.lastIndexOf('/');
        return separator < 0 ? normalized : normalized.substring(separator + 1);
    }

    static String jokeBankFolder(String settingKey, String language) {
        String category = jokeCategory(settingKey);
        if (category == null) return null;
        String selected = "en".equalsIgnoreCase(language) ? "en" : "es";
        return "sounds/joke/" + selected + "/" + category + "/";
    }

    static String jokeBankFolderForResource(String resource) {
        String normalized = normalize(resource);
        if (normalized == null || !normalized.startsWith("joke/")) {
            return null;
        }
        int separator = normalized.lastIndexOf('/');
        return separator < 0 ? null
                : "sounds/" + normalized.substring(0, separator + 1);
    }

    /** Actual source contract for one language-specific joke-sound bank. */
    static JokeBankMode jokeBankMode(GdxGamePresentationSettings settings,
            String settingKey, String language) {
        String category = jokeCategory(settingKey);
        if (settings == null || category == null || !settings.modActive()) {
            return JokeBankMode.OFFICIAL;
        }
        String selected = "en".equalsIgnoreCase(language) ? "en" : "es";
        boolean hasExternalBank = !settings.modFiles(
                "sounds/joke/" + selected + "/" + category, ".wav")
                .isEmpty();
        if (!hasExternalBank) return JokeBankMode.OFFICIAL;
        return settings.fuseModSounds()
                ? JokeBankMode.FUSED : JokeBankMode.MOD_ONLY;
    }

    /** Actual source contract shared by all joke banks for one language. */
    static JokeBankMode jokeBanksMode(GdxGamePresentationSettings settings,
            String language) {
        if (settings == null || !settings.modActive()) {
            return JokeBankMode.OFFICIAL;
        }
        String selected = "en".equalsIgnoreCase(language) ? "en" : "es";
        boolean hasExternalBank = JOKE_CATEGORIES.stream().anyMatch(category ->
                !settings.modFiles("sounds/joke/" + selected + "/"
                        + category, ".wav").isEmpty());
        if (!hasExternalBank) return JokeBankMode.OFFICIAL;
        return settings.fuseModSounds()
                ? JokeBankMode.FUSED : JokeBankMode.MOD_ONLY;
    }

    static String jokeModStatus(GdxGamePresentationSettings settings,
            String language, GdxGameText text) {
        JokeBankMode mode = jokeBanksMode(settings, language);
        if (mode == JokeBankMode.OFFICIAL || text == null) return "";
        return text.translate(mode == JokeBankMode.FUSED
                ? "gdx.settings.joke_mod_status_fused"
                : "gdx.settings.joke_mod_status_exclusive",
                settings.modDisplayName());
    }

    static boolean isJokeSoundsPage(
            GdxSettingsContract.TogglePage page) {
        return page != null && page.options().stream().anyMatch(option ->
                "sonidos_chorra".equals(option.key()));
    }

    /** Whether the setting will use media supplied by the active MOD. */
    static boolean modOverridesSetting(GdxGamePresentationSettings settings,
            GdxSettingsContract.ToggleOption option, String language) {
        if (settings == null || option == null || !settings.modActive()) {
            return false;
        }
        String concreteJoke = jokeResource(option);
        if (concreteJoke != null) {
            return modOverride(settings, concreteJoke).isPresent();
        }
        if ("sonidos_chorra".equals(option.key())) {
            String selected = "en".equalsIgnoreCase(language) ? "en" : "es";
            boolean categoryOverride = JOKE_CATEGORIES.stream().anyMatch(category ->
                    !settings.modFiles("sounds/joke/" + selected + "/"
                            + category, ".wav").isEmpty());
            return categoryOverride || List.of(
                    "sonido_chorra_raise", "sonido_chorra_norebuy",
                    "sonido_chorra_indivisible", "sonido_chorra_lastcard",
                    "sonido_chorra_badbeat", "sonido_chorra_lucky",
                    "sonido_chorra_special_card")
                    .stream().map(GdxGameAudioResources::syntheticOption)
                    .anyMatch(child -> modOverridesSetting(settings, child,
                            language));
        }
        if ("sonido_cinematicas".equals(option.key())) {
            List<String> cinematicNames = settings.modFiles(
                    "cinematics/allin", ".gif").stream()
                    .map(path -> path.getFileName().toString()
                            .replaceFirst("(?i)\\.gif$", ""))
                    .toList();
            return settings.modFiles("cinematics/allin", ".wav").stream()
                    .map(path -> path.getFileName().toString()
                            .replaceFirst("(?i)\\.wav$", ""))
                    .anyMatch(name -> cinematicNames.stream()
                            .anyMatch(name::equalsIgnoreCase));
        }
        String jokeCategory = jokeCategory(option.key());
        if (jokeCategory != null) {
            String selected = "en".equalsIgnoreCase(language) ? "en" : "es";
            return !settings.modFiles("sounds/joke/" + selected + "/"
                    + jokeCategory, ".wav").isEmpty();
        }
        if ("sonido_chorra_special_card".equals(option.key())) {
            return !settings.modFiles("sounds/decks/" + settings.deck(),
                    ".wav").isEmpty();
        }
        String funnyMiscResource = funnyMiscResource(option.key());
        if (funnyMiscResource != null) {
            return modOverride(settings, funnyMiscResource).isPresent();
        }
        return settingResources(option.key(), language).stream()
                .anyMatch(resource -> modOverride(settings, resource)
                        .isPresent());
    }

    /** Every concrete media file governed by one audio setting. */
    static List<String> settingResources(String settingKey, String language) {
        if (settingKey == null || settingKey.isBlank()) return List.of();
        String concreteJoke = GamePresentationSettings.jokeSoundResource(
                settingKey);
        if (concreteJoke != null) return List.of(concreteJoke);
        if ("sonido_interruptor".equals(settingKey)) {
            return List.of("misc/button_on.wav", "misc/button_off.wav");
        }
        GdxSettingsContract.AudioPreview preview =
                GdxSettingsContract.audioPreview(syntheticOption(settingKey),
                        language == null ? "es"
                                : language.toLowerCase(Locale.ROOT));
        return preview == null ? List.of() : List.of(preview.resource());
    }

    /**
     * Returns the preview only when its resolved media really exists.  A MOD
     * may provide optional sounds that have no bundled fallback, so their
     * play button must not be rendered for installations that do not supply
     * the file.
     */
    static GdxSettingsContract.AudioPreview availablePreview(
            GdxGamePresentationSettings settings,
            GdxSettingsContract.ToggleOption option, String language) {
        GdxSettingsContract.AudioPreview preview =
                GdxSettingsContract.audioPreview(option, language);
        return preview != null && resolve(settings, preview.resource()) != null
                ? preview : null;
    }

    static String jokeCategory(String key) {
        return switch (key) {
            case "sonido_chorra_allin" -> "allin";
            case "sonido_chorra_fold" -> "fold";
            case "sonido_chorra_showdown" -> "showdown";
            case "sonido_chorra_winner" -> "winner";
            case "sonido_chorra_loser" -> "loser";
            default -> null;
        };
    }

    /**
     * Resources controlled by individual joke switches outside category
     * directories. Some of them may be supplied only by a MOD, so MOD
     * provenance must not depend on the availability of a bundled preview.
     */
    private static String funnyMiscResource(String key) {
        return switch (key) {
            case "sonido_chorra_raise" -> GdxSoundFeedback.FUNNY_RAISE;
            case "sonido_chorra_norebuy" ->
                GdxSoundFeedback.FUNNY_NO_REBUY;
            case "sonido_chorra_indivisible" -> "misc/indivisible.wav";
            case "sonido_chorra_lastcard" -> "misc/lastcard.wav";
            case "sonido_chorra_badbeat" -> "misc/badbeat.wav";
            case "sonido_chorra_lucky" -> "misc/youarelucky.wav";
            default -> null;
        };
    }

    private static GdxSettingsContract.ToggleOption syntheticOption(
            String key) {
        return new GdxSettingsContract.ToggleOption(key, key, true,
                GdxSettingsContract.Gate.FUNNY_SOUNDS, false);
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

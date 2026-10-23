/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Resolves hand-generator faces from the deck selected in Settings. */
record GdxHandGeneratorCardSource(String deck, String cardCode,
        Path externalPath, String internalPath) {

    GdxHandGeneratorCardSource {
        Objects.requireNonNull(deck, "deck");
        Objects.requireNonNull(cardCode, "cardCode");
        if ((externalPath == null) == (internalPath == null)) {
            throw new IllegalArgumentException(
                    "Exactly one card source must be configured");
        }
    }

    static GdxHandGeneratorCardSource resolve(
            GdxGamePresentationSettings settings, String cardCode) {
        Objects.requireNonNull(settings, "settings");
        String code = Objects.requireNonNull(cardCode, "cardCode");
        String deck = settings.deck();
        String filename = code + ".jpg";
        if (settings.modDeck(deck)) {
            Optional<Path> external = settings.modAsset(
                    "decks/" + deck + "/hq/" + filename)
                    .or(() -> settings.modAsset(
                            "decks/" + deck + "/" + filename));
            if (external.isPresent()) {
                return new GdxHandGeneratorCardSource(deck, code,
                        external.orElseThrow(), null);
            }
        }
        String bundled = GdxGamePresentationSettings.OFFICIAL_DECKS
                .contains(deck) ? deck : "goliat";
        return new GdxHandGeneratorCardSource(deck, code, null,
                "images/decks/" + bundled + "/hq/" + filename);
    }

    String cacheKey() {
        return deck + "/" + cardCode;
    }

    boolean external() {
        return externalPath != null;
    }
}

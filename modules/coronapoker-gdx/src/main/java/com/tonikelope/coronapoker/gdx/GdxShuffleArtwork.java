/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

/** Both filters sample the same native-resolution textures and mip levels. */
final class GdxShuffleArtwork implements Disposable {
    static final String[] DECKS = {"goliat", "goliat4", "coronapoker", "coronapoker4",
        "interstate60", "interstate60-goliat"};
    private static final String[] RANKS = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
    private static final String[] SUITS = {"P", "T", "D", "C"};
    private static final String[] SUIT_NAMES = {"picas", "tréboles", "diamantes", "corazones"};
    private final Texture[] faces = new Texture[GdxShuffleGeometry.CARDS];

    static FileHandle directory(String deck) {
        return Gdx.files.internal("images/decks/" + deck);
    }

    record FaceSize(int width, int height) {
        float aspect() { return (float) width / height; }
    }

    /** Read the deck's physical format without uploading a card texture. */
    static FaceSize faceSize(FileHandle file) {
        try (var source = file.read();
                var input = new javax.imageio.stream.MemoryCacheImageInputStream(source)) {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (readers.hasNext()) {
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    return new FaceSize(reader.getWidth(0), reader.getHeight(0));
                } finally {
                    reader.dispose();
                }
            }
        } catch (java.io.IOException | com.badlogic.gdx.utils.GdxRuntimeException ex) {
            Gdx.app.error("Cards", "Could not read the card dimensions from " + file, ex);
        }
        return new FaceSize(630, 880);
    }

    static Texture back(FileHandle file) {
        return new Texture(file, true);
    }

    GdxShuffleArtwork(FileHandle hq) {
        try {
            for (int id = 0; id < faces.length; id++) {
                faces[id] = new Texture(hq.child(code(id) + ".jpg"), true);
            }
        } catch (RuntimeException failure) {
            dispose();
            throw failure;
        }
    }

    static String code(int id) {
        return RANKS[id % 13] + "_" + SUITS[id / 13];
    }

    static String label(int id) {
        return RANKS[id % 13] + " de " + SUIT_NAMES[id / 13];
    }

    Texture face(int id) {
        return faces[id];
    }

    static void bind(Texture texture, int unit, boolean gameFilter) {
        GdxCardSampling.bind(texture, unit, gameFilter
                ? GdxCardSmaaRenderer.RENDERING_SMOOTH : GdxCardSmaaRenderer.RENDERING_BALANCED);
    }

    @Override public void dispose() {
        for (Texture face : faces) if (face != null) face.dispose();
    }
}

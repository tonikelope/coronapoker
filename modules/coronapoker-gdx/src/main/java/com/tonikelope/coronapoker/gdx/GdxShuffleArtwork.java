/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;

/** Builds the face atlas once when the viewer changes decks. */
final class GdxShuffleArtwork {
    static final String[] DECKS = {"goliat", "goliat4", "coronapoker", "coronapoker4",
        "interstate60", "interstate60-goliat"};
    private static final String[] RANKS = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
    private static final String[] SUITS = {"P", "T", "D", "C"};
    private static final int TILE_W = 256;
    private static final int TILE_H = 352;

    static FileHandle directory(String deck) {
        return Gdx.files.internal("images/decks/" + deck);
    }

    static Texture back(FileHandle file) {
        return filtered(new Texture(file, true));
    }

    static Texture faces(FileHandle hq) {
        Pixmap atlas = new Pixmap(8 * TILE_W, 8 * TILE_H, Pixmap.Format.RGB888);
        atlas.setBlending(Pixmap.Blending.None);
        atlas.setFilter(Pixmap.Filter.BiLinear);
        try {
            for (int id = 0; id < GdxShuffleGeometry.CARDS; id++) {
                Pixmap face = new Pixmap(hq.child(RANKS[id % 13] + "_" + SUITS[id / 13] + ".jpg"));
                try {
                    atlas.drawPixmap(face, 0, 0, face.getWidth(), face.getHeight(),
                            (id % 8) * TILE_W, (id / 8) * TILE_H, TILE_W, TILE_H);
                } finally {
                    face.dispose();
                }
            }
            return filtered(new Texture(atlas, true));
        } finally {
            atlas.dispose();
        }
    }

    private static Texture filtered(Texture texture) {
        texture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
        texture.setAnisotropicFilter(16f);
        return texture;
    }

    private GdxShuffleArtwork() {}
}

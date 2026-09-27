package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

/** Loads the original encrypted secret felt without any Swing dependency. */
final class GdxSecretFelt {

    private GdxSecretFelt() {
    }

    static byte[] decode() throws IOException, GeneralSecurityException {
        try (InputStream splash = resource("/images/splash.gif");
                InputStream encrypted = resource("/images/d")) {
            return GdxAboutEasterEgg.decode(splash, encrypted);
        }
    }

    static Texture texture() throws IOException, GeneralSecurityException {
        byte[] decoded = decode();
        Pixmap pixmap = new Pixmap(decoded, 0, decoded.length);
        try {
            Texture texture = new Texture(pixmap, true);
            texture.setFilter(TextureFilter.MipMapLinearLinear,
                    TextureFilter.Linear);
            return texture;
        } finally {
            pixmap.dispose();
        }
    }

    private static InputStream resource(String path) throws IOException {
        InputStream stream = GdxSecretFelt.class.getResourceAsStream(path);
        if (stream == null) throw new IOException("Missing resource: " + path);
        return stream;
    }
}

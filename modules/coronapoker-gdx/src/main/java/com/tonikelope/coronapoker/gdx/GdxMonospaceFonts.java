/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import java.nio.file.Path;
import java.util.ArrayList;

/** Resolves the native fixed-pitch face shared by logs and FPS overlays. */
final class GdxMonospaceFonts {

    private GdxMonospaceFonts() {
    }

    static FreeTypeFontGenerator generator(boolean bold) {
        ArrayList<String> candidates = new ArrayList<>();
        String windows = System.getenv("WINDIR");
        if (windows != null && !windows.isBlank()) {
            candidates.add(Path.of(windows, "Fonts",
                    bold ? "consolab.ttf" : "consola.ttf").toString());
        }
        candidates.add("/usr/share/fonts/truetype/dejavu/"
                + (bold ? "DejaVuSansMono-Bold.ttf"
                        : "DejaVuSansMono.ttf"));
        candidates.add("/System/Library/Fonts/Menlo.ttc");
        for (String candidate : candidates) {
            FileHandle file = Gdx.files.absolute(candidate);
            if (file.exists()) return new FreeTypeFontGenerator(file);
        }
        // Portable final fallback for uncommon systems without a native
        // fixed-pitch family. Windows, Linux and macOS use the faces above.
        return new FreeTypeFontGenerator(
                Gdx.files.internal("fonts/Inter-Medium.ttf"));
    }
}

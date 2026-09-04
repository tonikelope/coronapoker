/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Loads optional Swing MOD media without coupling the canonical dealer to Init. */
final class SwingModMediaCatalog {

    private SwingModMediaCatalog() {
    }

    static void loadAllInCinematics() {
        if (Init.MOD == null) {
            return;
        }
        ArrayList<Object[]> cinematics = new ArrayList<>();
        File folder = new File(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin");
        if (folder.isDirectory() && folder.canRead()
                && folder.listFiles(File::isFile).length > 0) {
            for (File file : folder.listFiles(File::isFile)) {
                if (file.getName().toLowerCase().endsWith(".gif")) {
                    cinematics.add(new Object[]{file.getName()});
                }
            }
            if (Crupier.FUSION_MOD_CINEMATICS) {
                cinematics.addAll(Arrays.asList(Crupier.ALLIN_CINEMATICS.getValue()));
            }
            Crupier.ALLIN_CINEMATICS_MOD = new HashMap.SimpleEntry<>(
                    "allin/", cinematics.toArray(new Object[0][]));
        } else {
            Crupier.ALLIN_CINEMATICS_MOD = Crupier.ALLIN_CINEMATICS;
        }
    }

    static void loadSounds(String language) {
        if (Init.MOD == null) {
            return;
        }
        Crupier.ALLIN_SOUNDS_MOD = loadSoundCategory(language, "allin",
                Crupier.ALLIN_SOUNDS.get(language));
        Crupier.FOLD_SOUNDS_MOD = loadSoundCategory(language, "fold",
                Crupier.FOLD_SOUNDS.get(language));
        Crupier.SHOWDOWN_SOUNDS_MOD = loadSoundCategory(language, "showdown",
                Crupier.SHOWDOWN_SOUNDS.get(language));
        Crupier.LOSER_SOUNDS_MOD = loadSoundCategory(language, "loser",
                Crupier.LOSER_SOUNDS.get(language));
        Crupier.WINNER_SOUNDS_MOD = loadSoundCategory(language, "winner",
                Crupier.WINNER_SOUNDS.get(language));
    }

    private static Map.Entry<String, String[]> loadSoundCategory(String language,
            String category, Map.Entry<String, String[]> bundled) {
        String relative = "joke/" + language + "/" + category + "/";
        Path path = Path.of(Helpers.getCurrentJarParentPath(), "mod", "sounds", relative);
        if (!Files.exists(path)) {
            return bundled;
        }
        File[] files = path.toFile().listFiles(File::isFile);
        ArrayList<String> filenames = new ArrayList<>();
        for (File file : files) {
            filenames.add(file.getName());
        }
        if (!Crupier.FUSION_MOD_SOUNDS) {
            return new HashMap.SimpleEntry<>(relative, filenames.toArray(new String[0]));
        }
        ArrayList<String> sounds = new ArrayList<>(Arrays.asList(bundled.getValue()));
        sounds.addAll(filenames);
        return new HashMap.SimpleEntry<>(relative, sounds.toArray(new String[0]));
    }
}

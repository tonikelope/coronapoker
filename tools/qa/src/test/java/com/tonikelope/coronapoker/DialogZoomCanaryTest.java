/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * CANARIO del zoom de diálogos (feat-global-dialog-zoom).
 *
 * Los tamaños de los diálogos {@code .form} viven en el bloque generado por NetBeans
 * ({@code //GEN-BEGIN:initComponents}). Si se reabre un form en el diseñador y se toca el layout,
 * NetBeans REGENERA ese bloque desde el {@code .form} (que solo guarda enteros planos) y BORRA los
 * {@code Math.round(N * Helpers.DIALOG_ZOOM)} que hacen que el diálogo escale. No hay forma de impedirlo,
 * pero este test lee el FUENTE de cada diálogo cableado y falla si su número de escalados baja del
 * mínimo esperado: así una regeneración que se lleve el escalado se detecta al momento en el build en
 * vez de romperse en silencio.
 *
 * Si cambias a PROPÓSITO el escalado de un diálogo (añades/quitas tamaños), actualiza aquí su número.
 */
public class DialogZoomCanaryTest {

    // Diálogo -> nº MÍNIMO de ocurrencias de "DIALOG_ZOOM" que debe tener su fuente (contando el zoom
    // de layout del bloque generado + fuentes/iconos del constructor). Valores tomados del estado
    // cableado y verificado; el test exige >= (añadir escalado no rompe, quitarlo sí).
    private static final Map<String, Integer> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put("ExitDialog", 5);
        EXPECTED.put("AutoActionDialog", 2);
        EXPECTED.put("RunItTwiceDialog", 3);
        EXPECTED.put("VoluntaryStraddleDialog", 2);
        EXPECTED.put("RebuyDialog", 7);
        EXPECTED.put("Reconnect2ServerDialog", 6);
        EXPECTED.put("BlindStructureManagerDialog", 11);
        EXPECTED.put("WaitingRoomFrame", 43);
        EXPECTED.put("NewGameDialog", 53);
        EXPECTED.put("StatsDialog", 48);
        EXPECTED.put("GameLogDialog", 21);
        EXPECTED.put("SettingsDialog", 7);
        EXPECTED.put("AppearanceSettingsPanel", 32);
        EXPECTED.put("AudioSettingsPanel", 12);
        EXPECTED.put("GameSettingsPanel", 16);
        EXPECTED.put("ChatImageDialog", 2);
        EXPECTED.put("CoronaHTMLEditorKit", 1);
        EXPECTED.put("IdenticonDialog", 9);
        EXPECTED.put("SessionIdenticonMosaicDialog", 3);
        EXPECTED.put("VolumeControlDialog", 2);
        EXPECTED.put("RecoverDialog", 4);
        EXPECTED.put("PauseDialog", 1);
    }

    @Test
    public void dialogZoomScalingSurvives() throws IOException {
        Path srcDir = locateSourceDir();

        StringBuilder problems = new StringBuilder();

        for (Map.Entry<String, Integer> e : EXPECTED.entrySet()) {
            Path file = srcDir.resolve(e.getKey() + ".java");
            if (!Files.isRegularFile(file)) {
                problems.append("\n  - FALTA el fuente: ").append(file);
                continue;
            }
            int count = countOccurrences(Files.readString(file), "DIALOG_ZOOM");
            if (count < e.getValue()) {
                problems.append("\n  - ").append(e.getKey())
                        .append(": DIALOG_ZOOM = ").append(count)
                        .append(" (esperado >= ").append(e.getValue()).append("). ")
                        .append("Probable regeneracion del .form en NetBeans que borro el escalado: ")
                        .append("re-aplica Math.round(N * Helpers.DIALOG_ZOOM) en el bloque generado, ")
                        .append("o actualiza el numero esperado si el cambio es intencional.");
            }
        }

        if (problems.length() > 0) {
            fail("CANARIO zoom de dialogos: escalado perdido en:" + problems + "\n");
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int n = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    // Sube desde user.dir hasta el primer src/main/java/com/tonikelope/coronapoker que contenga las
    // clases reales (se valida con NewGameDialog.java), para funcionar tanto si el test corre desde la
    // raiz como desde el modulo tools/qa.
    private static Path locateSourceDir() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path p = start; p != null; p = p.getParent()) {
            Path candidate = p.resolve("src/main/java/com/tonikelope/coronapoker");
            if (Files.isDirectory(candidate) && Files.isRegularFile(candidate.resolve("NewGameDialog.java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "No encuentro src/main/java/com/tonikelope/coronapoker con las clases reales desde " + start);
    }
}

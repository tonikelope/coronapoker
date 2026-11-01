package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxSceneTransitionContractTest {

    @Test
    void everyExternalReturnUsesTheSameFrontendRecoveryBoundary()
            throws Exception {
        String shell = source("GdxApplicationShell.java");

        assertTrue(shell.contains("private void restoreFrontendInput()"));
        assertTrue(shell.contains("menu.resize(Gdx.graphics.getWidth(),"
                + " Gdx.graphics.getHeight());"));
        assertTrue(shell.contains("menu.reclaimAfterExternalScene();"));
        assertEquals(1, occurrences(shell,
                "Gdx.input.setInputProcessor(menu);"),
                "frontend ownership must only be restored by the common boundary");
    }

    @Test
    void frontendSurfaceChangesCannotBypassTransientStateReset()
            throws Exception {
        String frontend = source("GdxFrontendScreen.java");

        assertTrue(frontend.contains("void reclaimAfterExternalScene()"));
        assertTrue(frontend.contains("hoverAnimations.clear();"));
        assertTrue(frontend.contains("prepareFrontendGraphicsState();"));
        assertTrue(frontend.contains("clearLobbyScreenState();"));
        assertTrue(frontend.contains("return (surface == Surface.MENU"
                + " && startupMenuWaitingForUpdate)\n"
                + "                || hasVisibleFrontendModal();"));
        assertEquals(2, occurrences(frontend, "\n        surface = "),
                "only construction and activateSurface may assign the surface");
    }

    @Test
    void sharedMusicStartsOnlyAfterTheFrontendStopsRendering()
            throws Exception {
        String shell = source("GdxApplicationShell.java");
        String table = source("CoronaPokerGdxTable.java");
        int suspended = shell.indexOf("case 0 -> menu.suspendForTable(true);");
        int installed = shell.indexOf("table = pending.table;", suspended);
        int pendingCleared = shell.indexOf("pendingTableOpen = null;",
                installed);
        int activated = shell.indexOf("table.activateBackgroundMusic();",
                pendingCleared);

        assertTrue(suspended >= 0);
        assertTrue(installed > suspended);
        assertTrue(pendingCleared > installed);
        assertTrue(activated > pendingCleared,
                "the frontend must not get another render turn after activation");
        assertEquals(1, occurrences(shell, "activateBackgroundMusic();"));

        int finalizationStart = table.indexOf(
                "private boolean createNextTableFinalizationStep()");
        int finalizationEnd = table.indexOf(
                "void shareBackgroundMusic(Music music)", finalizationStart);
        assertTrue(finalizationStart >= 0 && finalizationEnd > finalizationStart);
        assertFalse(table.substring(finalizationStart, finalizationEnd)
                        .contains("backgroundMusic.play()"),
                "table construction must not start the frontend-owned decoder");
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/" + file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}

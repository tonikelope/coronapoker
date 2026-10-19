/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.GameDialogSink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxNestedFramebufferContractTest {

    @Test
    void disabledCardPassRestoresAnActiveModalBackdrop() throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        int nestedEnd = table.indexOf("disabledHoleCardsLayer.end();");
        int restore = table.indexOf(
                "restoreParentFramebufferAfterNestedPass();", nestedEnd);
        int projectionRestore = table.indexOf(
                "batch.setProjectionMatrix(camera.combined);", restore);

        assertTrue(nestedEnd >= 0, "disabled-card framebuffer pass missing");
        assertTrue(restore > nestedEnd,
                "nested pass must restore whichever outer framebuffer owns it");
        assertTrue(projectionRestore > restore,
                "the parent target must be restored before drawing continues");
    }

    @Test
    void cardLayerTakesPriorityOverTheModalCaptureWhenPassesNest()
            throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        int method = table.indexOf(
                "private void restoreParentFramebufferAfterNestedPass()");
        int card = table.indexOf(
                "cardSsaaRenderer.resumeAfterNestedPass();", method);
        int modal = table.indexOf(
                "modalBackdropBlur.resumeCaptureAfterNestedPass();", method);
        int physical = table.indexOf("viewport.apply();", method);

        assertTrue(method >= 0);
        assertTrue(card > method && modal > card && physical > modal,
                "nested passes must restore card SSAA, modal capture and then the backbuffer");
    }

    @Test
    void ssaaLayerRebindsItsFramebufferAndHighResolutionViewport()
            throws IOException {
        String ssaa = Files.readString(source("GdxCardSsaaRenderer.java"));
        int method = ssaa.indexOf("void resumeAfterNestedPass()");
        int bind = ssaa.indexOf("layer.bind();", method);
        int viewport = ssaa.indexOf("Gdx.gl.glViewport(", bind);

        assertTrue(method >= 0);
        assertTrue(bind > method);
        assertTrue(viewport > bind);
    }

    @Test
    void modalBackdropExposesAnExplicitNestedPassRestore() throws IOException {
        String blur = Files.readString(source("GdxModalBackdropBlur.java"));
        int method = blur.indexOf("void resumeCaptureAfterNestedPass()");
        int bind = blur.indexOf("backdrop.bind();", method);
        int viewport = blur.indexOf("Gdx.gl.glViewport(", bind);

        assertTrue(method >= 0);
        assertTrue(bind > method);
        assertTrue(viewport > bind,
                "rebinding must also restore the downsampled viewport");
    }

    @Test
    void everyRealTableDialogOwnsTheBackdropButAutoModeDoesNot() {
        for (GdxTableDialog.Kind kind : GdxTableDialog.Kind.values()) {
            GdxTableDialog dialog = new GdxTableDialog(kind, "TEST",
                    GameDialogSink.Icon.NONE, 720, 0);
            if (kind == GdxTableDialog.Kind.AUTO_ACTION) {
                assertFalse(CoronaPokerGdxTable.blocksTableUtilities(dialog));
            } else {
                assertTrue(CoronaPokerGdxTable.blocksTableUtilities(dialog),
                        () -> kind + " must retain the blurred table backdrop");
            }
        }
    }

    private static Path source(String filename) {
        return Path.of("src", "main", "java", "com", "tonikelope",
                "coronapoker", "gdx", filename);
    }
}

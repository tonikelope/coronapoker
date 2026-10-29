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
                "cardSmaaRenderer.resumeAfterNestedPass();", method);
        int modal = table.indexOf(
                "modalBackdropBlur.resumeCaptureAfterNestedPass();", method);
        int physical = table.indexOf("viewport.apply();", method);

        assertTrue(method >= 0);
        assertTrue(card > method && modal > card && physical > modal,
                "nested passes must restore card SMAA, modal capture and then the backbuffer");
    }

    @Test
    void disabledCardOpacityPreservesPremultipliedColour() throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        int method = table.indexOf(
                "private void finishHoleCardsLayer(float alpha)");
        int premultiplied = table.indexOf(
                "usePremultipliedComposite(batch);", method);
        int fade = table.indexOf(
                "batch.setColor(alpha, alpha, alpha, alpha);", method);

        assertTrue(method >= 0 && premultiplied > method);
        assertTrue(fade > premultiplied,
                "fold/showdown opacity must scale premultiplied RGB and alpha together");
    }

    @Test
    void allInPotFadesItsCompleteCrispRackOnlyAfterComposition()
            throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        int method = table.indexOf(
                "private void drawPotPanelSurface(float alpha)");
        int begin = table.indexOf(
                "beginChipRackComposite(iconCenterX, iconBaseY)",
                method);
        int opaqueRack = table.indexOf(
                "physicalCounts, composite ? 1f : opacity", begin);
        int finish = table.indexOf(
                "finishChipRackComposite(iconCenterX, iconBaseY,",
                opaqueRack);

        assertTrue(method >= 0 && begin > method,
                "the translucent all-in pot must capture its chip rack");
        assertTrue(opaqueRack > begin && finish > opaqueRack,
                "the rack must remain opaque until the completed layer fades");
    }

    @Test
    void smaaLayerRebindsItsFramebufferAndNativeResolutionViewport()
            throws IOException {
        String smaa = Files.readString(source("GdxCardSmaaRenderer.java"));
        int method = smaa.indexOf("void resumeAfterNestedPass()");
        int bind = smaa.indexOf("colorLayer.bind();", method);
        int viewport = smaa.indexOf("Gdx.gl.glViewport(", bind);

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

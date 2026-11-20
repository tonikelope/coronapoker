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
    void nestedPassRestoresTheNearestActiveCardLayerBeforeTheBackbuffer()
            throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        int method = table.indexOf(
                "private void restoreParentFramebufferAfterNestedPass()");
        int card = table.indexOf(
                "cardSmaaRenderer.resumeAfterNestedPass();", method);
        int fade = table.indexOf("handCardFadeLayer.bind();", method);
        int physical = table.indexOf("viewport.apply();", method);

        assertTrue(method >= 0);
        assertTrue(card > method && fade > card && physical > fade,
                "nested passes must restore card SMAA, hand-card fading or the backbuffer in that order");
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
    void seatAndPotRacksFadeOnlyAfterComposition() throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        assertTrue(table.contains("drawFadingChipRack(seat.stackX, seat.stackY,"));
        assertTrue(table.contains("drawFadingChipRack(iconCenterX, iconBaseY,"));
        int method = table.indexOf("private void drawFadingChipRack(");
        int begin = table.indexOf("beginChipRackComposite(centerX, centerY)", method);
        int opaqueRack = table.indexOf("composite ? 1f : alpha", begin);
        int finish = table.indexOf("finishChipRackComposite(alpha)", opaqueRack);

        assertTrue(method >= 0 && begin > method);
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
    void modalBackdropFiltersOnlyAfterTheTableSceneIsComposed() throws IOException {
        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        int scene = table.indexOf("drawTableScene();");
        int blur = table.indexOf("modalBackdropBlur.blurPresentedScene(", scene);
        int overlay = table.indexOf("drawUnifiedModalBackdrop();", blur);

        assertTrue(scene >= 0 && blur > scene && overlay > blur,
                "the filter must sample the completed table before drawing modal chrome");
    }

    @Test
    void everyForegroundDialogOwnsTheBackdropAndAutoUsesIndependentState()
            throws IOException {
        for (GdxTableDialog.Kind kind : GdxTableDialog.Kind.values()) {
            GdxTableDialog dialog = new GdxTableDialog(kind, "TEST",
                    GameDialogSink.Icon.NONE, 720, 0);
            if (kind == GdxTableDialog.Kind.AUTO_ACTION) {
                assertFalse(CoronaPokerGdxTable.blocksTableUtilities(dialog),
                        "AUTO_ACTION uses independent state and must not block utilities");
            } else {
                assertTrue(CoronaPokerGdxTable.blocksTableUtilities(dialog),
                        () -> kind + " must block utilities when used as a foreground dialog");
            }
        }

        String table = Files.readString(source("CoronaPokerGdxTable.java"));
        assertTrue(table.contains("private GdxTableDialog activeDialog;"));
        assertTrue(table.contains("private GdxTableDialog autoActionDialog;"));
        assertTrue(table.contains("if (request.isAutoAction())"),
                "AUTO must be routed to its independent state before foreground dialogs");
    }

    private static Path source(String filename) {
        return Path.of("src", "main", "java", "com", "tonikelope",
                "coronapoker", "gdx", filename);
    }
}

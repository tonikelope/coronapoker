/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("certification")
final class GdxRebuyAmountGpuTest {

    @Test
    void rebuyAmountRequiresFocusBeforeEditing() {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(960, 540);
        config.disableAudio(true);
        var failure = new AtomicReference<Throwable>();
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override public void render() {
                if (Gdx.graphics.getDeltaTime() <= 0f) return;
                try {
                    verifyRebuyAmount();
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    Gdx.app.exit();
                }
            }
        }, config);
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void verifyRebuyAmount() throws ReflectiveOperationException {
        var table = GdxFinalSummaryNoticeTest.table();
        try {
            table.create();
            table.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            var field = CoronaPokerGdxTable.class.getDeclaredField("dialogAmountEdit");
            field.setAccessible(true);
            var edit = (GdxTextEditState) field.get(table);
            var viewportField = CoronaPokerGdxTable.class.getDeclaredField("viewport");
            viewportField.setAccessible(true);
            var viewport = (Viewport) viewportField.get(table);
            var dialog = new GdxTableDialog("REBUY", "", 820, 0, false,
                    "CANCEL", 0.01d, 10d, 10d);
            dialog.useRebuyChipDenomination(10);
            table.showDialog(dialog);
            var input = table.inputProcessor();

            table.render();
            int firstBorder = rebuyTopBorder(viewport);
            table.render();
            assertEquals(firstBorder, rebuyTopBorder(viewport),
                    "the panel must stay in place after it first appears");
            assertEquals(0, edit.caret(dialog.amountText()));
            assertFalse(edit.hasSelection(dialog.amountText()));
            input.keyDown(Input.Keys.LEFT);
            input.keyUp(Input.Keys.LEFT);
            assertTrue(input.keyTyped('|'));
            assertTrue(input.keyTyped('5'));
            assertEquals("10", dialog.amountText());
            assertEquals(0, edit.caret(dialog.amountText()));
            input.keyDown(Input.Keys.TAB);
            input.keyUp(Input.Keys.TAB);
            assertTrue(edit.hasSelection(dialog.amountText()));

            for (char character : "5,70".toCharArray()) {
                assertTrue(input.keyTyped(character));
            }
            assertEquals("5,70", dialog.amountText());
            assertEquals(5.70d, dialog.amount());
            assertEquals(4, edit.caret(dialog.amountText()));
            input.keyDown(Input.Keys.LEFT);
            input.keyUp(Input.Keys.LEFT);
            assertEquals(3, edit.caret(dialog.amountText()));
            table.render();

            assertTrue(table.resolveActiveDialogChoice(true));
            table.advanceDialogState();
            var reopened = new GdxTableDialog("REBUY", "", 820, 0, false,
                    "CANCEL", 0.01d, 10d, 10d);
            reopened.useRebuyChipDenomination(10);
            table.showDialog(reopened);
            assertEquals(0, edit.caret(reopened.amountText()));
            input.keyDown(Input.Keys.TAB);
            input.keyUp(Input.Keys.TAB);
            for (char character : "1.99".toCharArray()) {
                assertTrue(input.keyTyped(character));
            }
            assertFalse(table.resolveActiveDialogChoice(true));
            assertFalse(reopened.complete());
            table.render();
            assertEquals(firstBorder, rebuyTopBorder(viewport),
                    "the validation hint must not move the reopened panel");
            input.keyDown(Input.Keys.TAB);
            input.keyUp(Input.Keys.TAB);
            input.keyDown(Input.Keys.TAB);
            input.keyUp(Input.Keys.TAB);
            for (char character : "5.70".toCharArray()) {
                assertTrue(input.keyTyped(character));
            }
            assertTrue(input.keyTyped('\u00b7'));
            assertEquals("5.70", reopened.amountText());
            assertEquals(5.70d, reopened.amount());
            assertTrue(table.resolveActiveDialogChoice(true));
        } finally {
            table.dispose();
        }
    }
    private static int rebuyTopBorder(Viewport viewport) {
        Vector2 top = viewport.project(new Vector2(viewport.getWorldWidth() / 2f,
                (viewport.getWorldHeight() + GdxTableDialogLayout.REBUY_HEIGHT) / 2f));
        int x = HdpiUtils.toBackBufferX(Math.round(top.x));
        int y = HdpiUtils.toBackBufferY(Math.round(top.y)) - 4;
        byte[] pixels = ScreenUtils.getFrameBufferPixels(x - 12, y, 24, 9, false);
        StringBuilder samples = new StringBuilder();
        for (int row = 8; row >= 0; row--) {
            for (int column = 0; column < 24; column++) {
                int i = 4 * (row * 24 + column);
                int red = pixels[i] & 255;
                int green = pixels[i + 1] & 255;
                int blue = pixels[i + 2] & 255;
                if (column == 12) samples.append(red).append('/').append(green)
                        .append('/').append(blue).append(' ');
                if (red >= 100 && green >= 75 && green - blue >= 20) return y + row;
            }
        }
        throw new AssertionError("Gold rebuy border missing at " + x + "," + y + ": " + samples);
    }

}

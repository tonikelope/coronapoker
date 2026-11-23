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
            var dialog = new GdxTableDialog("REBUY", "", 820, 0, false,
                    "CANCEL", 0.01d, 10d, 10d);
            dialog.useRebuyChipDenomination(10);
            table.showDialog(dialog);
            var input = table.inputProcessor();

            table.render();
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
}

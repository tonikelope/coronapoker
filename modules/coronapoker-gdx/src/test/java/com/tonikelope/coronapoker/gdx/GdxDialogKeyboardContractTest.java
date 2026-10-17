package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;

final class GdxDialogKeyboardContractTest {

    @Test
    void bothEnterKeysAreCanonicalDialogConfirmationKeys() {
        assertTrue(GdxDialogKeyboard.isConfirm(Input.Keys.ENTER));
        assertTrue(GdxDialogKeyboard.isConfirm(Input.Keys.NUMPAD_ENTER));
        assertFalse(GdxDialogKeyboard.isConfirm(Input.Keys.SPACE));
    }

    @Test
    void escapeIsTheOnlyCanonicalDialogCancellationKey() {
        assertTrue(GdxDialogKeyboard.isCancel(Input.Keys.ESCAPE));
        assertFalse(GdxDialogKeyboard.isCancel(Input.Keys.BACKSPACE));
        assertFalse(GdxDialogKeyboard.isCancel(Input.Keys.ENTER));
    }

    @Test
    void rootModalWorkflowKeepsInputWhileAChildDialogOwnsIt() {
        assertFalse(GdxFrontendScreen.childModalOwnsInput(true, true));
        assertTrue(GdxFrontendScreen.childModalOwnsInput(true, false));
        assertFalse(GdxFrontendScreen.childModalOwnsInput(false, false));
    }
}

package com.tonikelope.coronapoker.gdx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GdxShowdownVerificationDialogTest {
    @Test
    void verificationWaitUsesZeroTrustAndHasNoUserDismissalControls() {
        GdxTableDialog dialog = GdxTableDialog.zeroTrustWait("Verificando la mano", 980);

        assertEquals("ZERO-TRUST", dialog.title());
        assertEquals("Verificando la mano", dialog.message());
        assertTrue(dialog.isZeroTrust());
        assertTrue(dialog.isVerificationWait());
        assertTrue(dialog.isExternallyControlled());
        assertFalse(dialog.showsPositive());
        assertFalse(dialog.showsNegative());
        assertFalse(dialog.readyToClose());
        dialog.dismiss();
        assertTrue(dialog.readyToClose());
    }
}

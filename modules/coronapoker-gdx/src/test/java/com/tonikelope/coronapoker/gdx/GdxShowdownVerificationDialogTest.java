package com.tonikelope.coronapoker.gdx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GdxShowdownVerificationDialogTest {
    @Test
    void waitPopupsStayOutsideTheModalQueueAndCloseIndependently() {
        CoronaPokerGdxTable table = new CoronaPokerGdxTable(60, () -> { });
        GdxTableDialog flop = GdxTableDialog.zeroTrustWait("Abriendo el flop", 980);
        GdxTableDialog turn = GdxTableDialog.zeroTrustWait("Abriendo el turn", 980);

        table.showDialog(flop);
        table.showDialog(turn);
        assertSame(turn, table.currentZeroTrustWait());
        assertFalse(table.hasActiveDialog());
        flop.dismiss();
        assertSame(turn, table.currentZeroTrustWait());
        turn.dismiss();
        assertNull(table.currentZeroTrustWait());

        table.showDialog(flop); // The dealer may close it before the render thread handles it.
        assertNull(table.currentZeroTrustWait());
        assertFalse(table.hasActiveDialog());
    }

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

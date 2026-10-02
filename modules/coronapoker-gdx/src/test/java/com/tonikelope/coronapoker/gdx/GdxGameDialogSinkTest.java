package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tonikelope.coronapoker.core.game.GameDialogSink;
import org.junit.jupiter.api.Test;

final class GdxGameDialogSinkTest {

    @Test
    void genericDealerDialogsUseTheActiveLanguage() {
        GdxGameDialogSink sink = new GdxGameDialogSink(
                new GdxGameText("en"));

        GdxTableDialog confirmation = sink.request(
                GdxTableDialog.Kind.CONFIRM, "Continue?",
                GameDialogSink.Icon.NONE, 700, 0);
        assertEquals("CONFIRMATION", confirmation.title());
        assertEquals("CANCEL", confirmation.negativeLabel());
        assertEquals("OK", confirmation.positiveLabel());

        GdxTableDialog warning = sink.request(
                GdxTableDialog.Kind.TIMED_WARNING, "Warning",
                GameDialogSink.Icon.NONE, 700, 5);
        assertEquals("WARNING", warning.title());
        assertEquals("CLOSE", warning.positiveLabel());

        GdxTableDialog error = sink.request(
                GdxTableDialog.Kind.ERROR, "HAND VOIDED",
                GameDialogSink.Icon.NONE, 0, 0);
        assertFalse(error.showsNegative());
        assertEquals("CLOSE", error.positiveLabel());

        GdxTableDialog info = sink.request(
                GdxTableDialog.Kind.INFO, "Finished",
                GameDialogSink.Icon.EXIT, 0, 0);
        assertFalse(info.showsNegative());
        assertEquals("CLOSE", info.positiveLabel());

        GdxTableDialog zeroTrust = sink.request(
                GdxTableDialog.Kind.ZERO_TRUST, "Cryptographic anomaly",
                GameDialogSink.Icon.NONE, 700, 0);
        assertEquals("ZERO-TRUST", zeroTrust.title());
        assertFalse(zeroTrust.showsNegative());
        assertEquals("CLOSE", zeroTrust.positiveLabel());

        GdxTableDialog pendingRebuy = sink.request(
                GdxTableDialog.Kind.CONFIRM, "Pending rebuy",
                GameDialogSink.Icon.NONE, 0, 0,
                "BACK", "CANCEL REBUY");
        assertEquals("BACK", pendingRebuy.negativeLabel());
        assertEquals("CANCEL REBUY", pendingRebuy.positiveLabel());
    }
}

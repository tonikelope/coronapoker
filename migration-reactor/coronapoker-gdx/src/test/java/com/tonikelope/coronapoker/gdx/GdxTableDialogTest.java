package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.GameDialogSink;
import org.junit.jupiter.api.Test;

final class GdxTableDialogTest {

    @Test
    void convertsLegacyHtmlToReadableNativeText() {
        assertEquals("Primera línea\nSegunda & última",
                GdxTableDialog.plainText(
                        "<html><b>Primera línea</b><br>Segunda &amp; última</html>"));
    }

    @Test
    void confirmationCompletesOnlyFromAnExplicitChoice() {
        GdxTableDialog accepted = dialog(GdxTableDialog.Kind.CONFIRM, 0);
        assertFalse(accepted.complete());
        accepted.accept();
        assertTrue(accepted.result().toCompletableFuture().join());

        GdxTableDialog cancelled = dialog(GdxTableDialog.Kind.CONFIRM, 0);
        cancelled.dismiss();
        assertFalse(cancelled.result().toCompletableFuture().join());
    }

    @Test
    void timedWarningUsesItsOwnMonotonicDisplayClock() {
        GdxTableDialog warning = dialog(GdxTableDialog.Kind.TIMED_WARNING, 3);
        warning.opened(10f);
        assertFalse(warning.expired(12.99f));
        assertTrue(warning.expired(13f));
    }

    private static GdxTableDialog dialog(GdxTableDialog.Kind kind, int seconds) {
        return new GdxTableDialog(kind, "mensaje", GameDialogSink.Icon.NONE,
                0, seconds);
    }
}

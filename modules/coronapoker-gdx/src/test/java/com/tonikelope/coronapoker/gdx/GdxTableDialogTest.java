package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.GameDialogSink;
import org.junit.jupiter.api.Test;

final class GdxTableDialogTest {

    @Test
    void automaticInfoDwellIsHiddenAndHonorsPauseAccounting() {
        GdxTableDialog dialog = GdxTableDialog.autoDismissInfo(
                "GAME OVER", "", GameDialogSink.Icon.STOP, 860, 2.5f);
        dialog.opened(4f);

        assertEquals(0, dialog.seconds());
        assertFalse(dialog.expired(6.49f));
        dialog.setTimerPaused(6f, true);
        assertFalse(dialog.expired(20f));
        dialog.setTimerPaused(21f, false);
        assertFalse(dialog.expired(21.49f));
        assertTrue(dialog.expired(21.5f));
    }

    @Test
    void gameOverChoiceExposesItsVisualCountdownWithoutChangingDecision() {
        GdxTableDialog dialog = GdxTableDialog.gameOverChoice(10);
        dialog.opened(4f);

        assertTrue(dialog.isGameOver());
        assertEquals(10, dialog.remainingSeconds(4f));
        assertEquals(8, dialog.remainingSeconds(6.01f));
        assertFalse(dialog.expired(13.99f));
        assertTrue(dialog.expired(14f));
        dialog.timeout();
        assertFalse(dialog.result().toCompletableFuture().join());
    }

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
        warning.timeout();
        assertTrue(warning.result().toCompletableFuture().join());

        GdxTableDialog choice = dialog(GdxTableDialog.Kind.CONFIRM, 3);
        choice.opened(20f);
        assertTrue(choice.expired(23f));
        choice.timeout();
        assertFalse(choice.result().toCompletableFuture().join());
    }

    @Test
    void autoActionCountdownFreezesForTheWholeTablePause() {
        GdxTableDialog action = GdxTableDialog.autoAction("IGUALAR");
        assertTrue(action.isAutoAction());
        action.opened(10f);
        assertEquals(0.6f, action.remainingFraction(12f), 0.0001f);

        action.setTimerPaused(12f, true);
        assertFalse(action.expired(50f));
        assertEquals(0.6f, action.remainingFraction(50f), 0.0001f);

        action.setTimerPaused(50f, false);
        assertFalse(action.expired(52.99f));
        assertTrue(action.expired(53f));
        action.timeout();
        assertTrue(action.result().toCompletableFuture().join());
    }

    @Test
    void rebuyAmountIsClampedAndMovesInRangeDerivedSteps() {
        GdxTableDialog rebuy = new GdxTableDialog("RECOMPRAR", "", 0,
                15, true, "CANCELAR", 10, 1000, 5000);
        assertEquals(1000, rebuy.amount());
        rebuy.changeAmount(-1);
        assertEquals(990, rebuy.amount());
        for (int i = 0; i < 200; i++) rebuy.changeAmount(-1);
        assertEquals(10, rebuy.amount());
        rebuy.timeout();
        assertTrue(rebuy.result().toCompletableFuture().join());
    }

    @Test
    void autoCallDialogKeepsEnabledUnlimitedAndFiveCentStepDistinct() {
        GdxTableDialog dialog = GdxTableDialog.autoCall(false, 0d);
        assertTrue(dialog.isAutoCall());
        assertEquals("ACEPTAR", dialog.positiveLabel());
        assertFalse(dialog.optionEnabled());
        assertTrue(dialog.noLimit());
        assertEquals(5, dialog.amount());
        assertEquals(Integer.MAX_VALUE, dialog.maximumAmount(),
                "Swing Auto Call has no artificial upper cap");

        dialog.changeAmount(1);
        assertEquals(5, dialog.amount(),
                "disabled auto-call must not alter its hidden limit");
        dialog.toggleOptionEnabled();
        dialog.toggleNoLimit();
        dialog.changeAmount(1);
        assertTrue(dialog.optionEnabled());
        assertFalse(dialog.noLimit());
        assertEquals(10, dialog.amount());
    }

    @Test
    void autoCallEditableAmountMatchesSwingDecimalContract() {
        GdxTableDialog dialog = GdxTableDialog.autoCall(true, 1.25d);
        assertTrue(dialog.autoCallAmountEditable());
        assertEquals("1.25", dialog.amountText());

        assertTrue(dialog.acceptsAutoCallAmountText("12,345"));
        dialog.autoCallAmountText("12,345");
        assertEquals(12.35d, dialog.autoCallAmount(), 0.000_001d,
                "Swing accepts comma and rounds the accepted value to cents");

        dialog.autoCallAmountText("0");
        assertEquals(0.05d, dialog.autoCallAmount(), 0.000_001d,
                "zero remains reserved for the explicit no-limit switch");
    }

    @Test
    void autoCallInvalidIncompleteEditFallsBackToLastValidSpinnerValue() {
        GdxTableDialog dialog = GdxTableDialog.autoCall(true, 2.50d);
        dialog.autoCallAmountText("3.75");
        dialog.autoCallAmountText("-");

        assertEquals("-", dialog.amountText());
        assertEquals(3.75d, dialog.autoCallAmount(), 0.000_001d);
        assertFalse(dialog.acceptsAutoCallAmountText("1.2.3"));
        assertFalse(dialog.acceptsAutoCallAmountText("poker"));
    }

    @Test
    void autoCallTypedValueHasNoArtificialIntCentCap() {
        GdxTableDialog dialog = GdxTableDialog.autoCall(true,
                99_999_999_999.95d);

        assertTrue(dialog.autoCallAmount() > 99_000_000_000d);
        dialog.changeAmount(1);
        assertTrue(dialog.autoCallAmount() > 99_000_000_000d);
    }

    @Test
    void autoActionVetoOnlyOffersCancelAndTimeoutOrSpaceExecutes() {
        GdxTableDialog dialog = GdxTableDialog.autoAction("IGUALAR");
        assertTrue(dialog.showsNegative());
        assertFalse(dialog.showsPositive(),
                "Swing's veto overlay has no invented execute button");
    }

    @Test
    void handLimitStartsAfterCurrentHandAndKeepsUnlimitedExplicit() {
        GdxTableDialog unlimited = GdxTableDialog.handLimit(7, -1);
        assertTrue(unlimited.isHandLimit());
        assertTrue(unlimited.noLimit());
        assertEquals(8, unlimited.minimumAmount());
        assertEquals(8, unlimited.amount());
        unlimited.changeAmount(1);
        assertEquals(8, unlimited.amount());

        unlimited.toggleNoLimit();
        unlimited.changeAmount(1);
        assertFalse(unlimited.noLimit());
        assertEquals(9, unlimited.amount());

        GdxTableDialog limited = GdxTableDialog.handLimit(7, 20);
        assertFalse(limited.noLimit());
        assertEquals(20, limited.amount());
    }

    private static GdxTableDialog dialog(GdxTableDialog.Kind kind, int seconds) {
        return new GdxTableDialog(kind, "mensaje", GameDialogSink.Icon.NONE,
                0, seconds);
    }
}

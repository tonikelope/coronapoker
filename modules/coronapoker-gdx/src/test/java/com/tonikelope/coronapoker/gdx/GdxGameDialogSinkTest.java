package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    }
}

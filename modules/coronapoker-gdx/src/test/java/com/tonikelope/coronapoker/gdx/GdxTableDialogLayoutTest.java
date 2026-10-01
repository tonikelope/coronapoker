package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

final class GdxTableDialogLayoutTest {

    @Test
    void autoActionOwnsFourDisjointBandsInsideItsPanel() {
        Rectangle panel = new Rectangle(400f, 20f, 620f,
                GdxTableDialogLayout.AUTO_ACTION_HEIGHT);
        GdxTableDialogLayout.AutoAction layout =
                GdxTableDialogLayout.autoAction(panel.x, panel.y,
                        panel.width);

        assertInside(panel, layout.cancel());
        assertInside(panel, layout.progress());
        assertInside(panel, layout.action());
        assertInside(panel, layout.title());
        assertTrue(layout.cancel().y + layout.cancel().height
                < layout.progress().y);
        assertTrue(layout.progress().y + layout.progress().height
                < layout.action().y,
                "the countdown must never cross the action text panel");
        assertTrue(layout.action().y + layout.action().height
                < layout.title().y);
    }

    @Test
    void autoCallShowsUnlimitedAndMaximumAsSeparateControls() {
        GdxTableDialogLayout.AutoCall layout =
                GdxTableDialogLayout.autoCall(500f, 200f, 820f);

        assertTrue(layout.amount().bounds().y
                + layout.amount().bounds().height < layout.noLimit().y);
        assertTrue(layout.noLimit().y + layout.noLimit().height
                < layout.enabled().y);
        assertTrue(layout.amount().label().x
                + layout.amount().label().width
                <= layout.amount().controls().x);
        assertEquals(-1, layout.amount().directionAt(
                layout.amount().minusButton().x + 1f));
        assertEquals(1, layout.amount().directionAt(
                layout.amount().plusButton().x + 1f));
    }

    private static void assertInside(Rectangle outer, Rectangle inner) {
        assertTrue(inner.x >= outer.x && inner.y >= outer.y);
        assertTrue(inner.x + inner.width <= outer.x + outer.width);
        assertTrue(inner.y + inner.height <= outer.y + outer.height);
    }
}

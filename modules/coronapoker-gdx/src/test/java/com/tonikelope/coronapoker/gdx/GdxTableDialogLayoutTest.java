package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

final class GdxTableDialogLayoutTest {

    @Test
    void autoActionReplacesTheWholeHudActionArea() {
        Rectangle actionArea = new Rectangle(642f, 36f, 868f, 80f);
        GdxTableDialogLayout.AutoActionHud layout =
                GdxTableDialogLayout.autoActionHud(actionArea.x,
                        actionArea.y, actionArea.width, actionArea.height);

        assertEquals(actionArea, layout.bounds());
        assertInside(actionArea, layout.message());
        assertInside(actionArea, layout.cancel());
        assertInside(actionArea, layout.progress());
        assertEquals(actionArea.x, layout.progress().x);
        assertEquals(actionArea.width, layout.progress().width);
        assertTrue(layout.progress().y + layout.progress().height
                < layout.message().y,
                "the full-width countdown belongs below the AUTO status");
        assertTrue(layout.message().x + layout.message().width
                < layout.cancel().x);
    }

    @Test
    void autoCallShowsUnlimitedAndMaximumAsSeparateControls() {
        GdxTableDialogLayout.AutoCall layout =
                GdxTableDialogLayout.autoCall(500f, 200f, 1040f);

        assertTrue(layout.enabled().y + layout.enabled().height
                < layout.detail().y,
                "the description must not overlap the enabled control");
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

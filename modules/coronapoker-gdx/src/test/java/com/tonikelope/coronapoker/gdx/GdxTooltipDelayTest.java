/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxTooltipDelayTest {

    @Test
    void requiresAContinuousDwellOnTheSameTarget() {
        GdxTooltipDelay delay = new GdxTooltipDelay(500L);

        assertFalse(delay.ready("first", 1_000L));
        assertFalse(delay.ready("first", 1_499L));
        assertTrue(delay.ready("first", 1_500L));
        assertFalse(delay.ready("second", 1_600L));
        assertTrue(delay.ready("second", 2_100L));
    }

    @Test
    void leavingTheTargetRestartsTheDwell() {
        GdxTooltipDelay delay = new GdxTooltipDelay(500L);

        assertFalse(delay.ready("latency", 1_000L));
        delay.clear();
        assertFalse(delay.ready("latency", 2_000L));
        assertFalse(delay.ready("latency", 2_499L));
        assertTrue(delay.ready("latency", 2_500L));
    }

    @Test
    void openDropdownKeepsTheSelectorsTooltipOutsideTheOptions() {
        Rectangle anchor = new Rectangle(1182f, 340f, 461f, 72f);
        Rectangle popup = new Rectangle(1182f, 55f, 461f, 280f);
        Rectangle help = new Rectangle(1182f, 340f, 461f, 102f);
        Vector2 pointer = new Vector2(1230f, 372f);

        assertTrue(GdxFrontendScreen.dropdownTooltipOwnsPointer(help,
                anchor, popup, pointer));
        Rectangle tooltip = GdxSettingsTooltips.bounds(pointer,
                450f, 90f, 1920f, 1080f, popup);
        assertFalse(tooltip.overlaps(popup));
        assertFalse(tooltip.contains(pointer));
        assertTrue(new Rectangle(0f, 0f, 1920f, 1080f).contains(tooltip));

        Rectangle otherHelp = new Rectangle(1182f, 465f, 461f, 102f);
        assertFalse(GdxFrontendScreen.dropdownTooltipOwnsPointer(otherHelp,
                anchor, popup, new Vector2(1230f, 500f)));
        assertFalse(GdxFrontendScreen.dropdownTooltipOwnsPointer(help,
                anchor, popup, new Vector2(1230f, 305f)));
    }

    @Test
    void tooltipStaysClearOfAListThatOpensAboveItsSelector() {
        Vector2 pointer = new Vector2(1900f, 60f);
        Rectangle popup = new Rectangle(1450f, 94f, 450f, 280f);
        Rectangle tooltip = GdxSettingsTooltips.bounds(pointer,
                450f, 90f, 1920f, 1080f, popup);

        assertFalse(tooltip.overlaps(popup));
        assertTrue(new Rectangle(0f, 0f, 1920f, 1080f).contains(tooltip));
    }

    @Test
    void hoveredOptionPlacesHelpBesideTheListAtTheSameHeight() {
        Rectangle popup = new Rectangle(1182f, 55f, 461f, 280f);
        Vector2 pointer = new Vector2(1230f, 240f);
        Rectangle tooltip = GdxSettingsTooltips.bounds(pointer,
                450f, 90f, 1920f, 1080f, popup);

        assertTrue(tooltip.x + tooltip.width < popup.x);
        assertTrue(tooltip.y < pointer.y && tooltip.y + tooltip.height > pointer.y);
    }
}

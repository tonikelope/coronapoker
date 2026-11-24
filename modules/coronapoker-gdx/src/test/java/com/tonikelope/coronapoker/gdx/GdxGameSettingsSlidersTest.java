/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import com.tonikelope.coronapoker.gdx.GdxGameSettingsSliders.Control;
import org.junit.jupiter.api.Test;

final class GdxGameSettingsSlidersTest {

    @Test
    void buyinRangesKeepTheOtherEndpointAndReachLegacyValues() {
        var minimum = Control.MIN_BUYIN.between(10, 103);
        var maximum = Control.MAX_BUYIN.between(103, 500);
        assertEquals(10, minimum.valueAt(0d));
        assertEquals(103, minimum.valueAt(1d));
        assertEquals(103, maximum.valueAt(0d));
        assertEquals(500, maximum.valueAt(1d));
        assertEquals(103, minimum.adjust(100, 1));
        assertEquals(108, maximum.adjust(103, 1));
        var collapsed = Control.MIN_BUYIN.between(10, 10);
        assertEquals(0f, collapsed.progress(10));
        assertEquals(10, collapsed.valueAt(0.5d));
    }

    @Test
    void timeSlidersSnapToFiveSecondsAndKeepTheExactEndpoints() {
        assertEquals(65, Control.THINK_TIME.range.valueAt(0.5d));
        assertEquals(120, Control.THINK_TIME.range.valueAt(1d));
        assertEquals(5, Control.SHOWDOWN.range.valueAt(0d));
        assertEquals(20, Control.SHOWDOWN.range.valueAt(0.55d));
        assertEquals(30, Control.SHOWDOWN.range.valueAt(1d));
    }

    @Test
    void wideRangesGiveNormalCountsRoomWithoutChangingTheirLimits() {
        var interval = Control.BLIND_INTERVAL.range;
        var hands = Control.HAND_LIMIT.between(41, Integer.MAX_VALUE);
        var rebuys = Control.REBUYS.range;
        assertEquals(60, interval.valueAt(interval.progress(60)));
        assertEquals(41, hands.valueAt(0d));
        assertEquals(100, hands.valueAt(hands.progress(100)));
        assertEquals(3, rebuys.valueAt(rebuys.progress(3)));
        assertTrue(rebuys.progress(10) > 0.3f);
        assertEquals(Integer.MAX_VALUE, hands.valueAt(1d));
        assertEquals(Integer.MAX_VALUE, interval.adjust(Integer.MAX_VALUE, 1));
        assertEquals(1_000_000,
                Control.BLIND_INTERVAL.between(1, 1_000_000).valueAt(1d));
    }

    @Test
    void compactNewGameSlidersLeaveRoomForTheirValue() {
        var row = GdxSettingsLayout.gameSliderRow(
                new Rectangle(780f, 625f, 280f, GdxSettingsLayout.ROW_HEIGHT), false);
        assertTrue(row.track().width >= 120f);
        assertTrue(row.value().width >= 110f);
        assertTrue(row.track().x + row.track().width < row.value().x);
        assertTrue(row.value().x + row.value().width <= row.bounds().x + row.bounds().width);
    }

    @Test
    void valueFieldsAndReadoutsShareTheSameCenterAndTrackEnd() {
        var bounds = new Rectangle(20f, 90f, 600f, GdxSettingsLayout.ROW_HEIGHT);
        var standard = GdxSettingsLayout.sliderRow(bounds);
        var unlabelled = GdxSettingsLayout.gameSliderRow(bounds, false);
        var labelled = GdxSettingsLayout.gameSliderRow(bounds, true);
        var readout = GdxSettingsLayout.sliderReadout(bounds);
        float center = readout.x + readout.width / 2f;
        for (var row : java.util.List.of(standard, unlabelled, labelled)) {
            assertEquals(standard.value(), row.value());
            assertEquals(center, row.value().x + row.value().width / 2f);
            assertEquals(readout.x, row.track().x + row.track().width);
        }
    }

    @Test
    void exactValuesKeepIntegerAndMonetaryBounds() {
        var minutes = Control.BLIND_INTERVAL.range;
        assertEquals(245, minutes.parseExact("245", false));
        assertEquals(-1, minutes.parseExact("0", false));
        assertEquals(-1, minutes.parseExact("2147483648", false));
        assertEquals(-1, minutes.parseExact("1.5", false));
        assertEquals(37, Control.THINK_TIME.range.parseExact("37", false));
        assertEquals(-1, Control.THINK_TIME.range.parseExact("121", false));
        var blinds = Control.SMALL_BLIND.between(5, 20);
        assertEquals(15, blinds.parseExact("0,15", true));
        assertEquals(-1, blinds.parseExact("0.13", true));
        assertEquals(-1, blinds.parseExact("0.25", true));
        assertEquals(-1, blinds.parseExact("0.015", true));
    }

    @Test
    void blindIntervalsStayLinearThroughSixHundredMinutesAndOneThousandHands() {
        var minutes = GdxGameSettingsSliders.blindIntervalRange(false, Integer.MAX_VALUE);
        var hands = GdxGameSettingsSliders.blindIntervalRange(true, 1_000_000);
        assertEquals(600, minutes.valueAt(0.85d));
        assertEquals(1_000, hands.valueAt(0.85d));
        assertEquals(151, minutes.valueAt(0.85d / 4d));
        assertEquals(251, hands.valueAt(0.85d / 4d));
        assertEquals(Integer.MAX_VALUE, minutes.valueAt(1d));
        assertEquals(1_000_000, hands.valueAt(1d));
    }

    @Test
    void autoCallFieldRejectsInvalidValuesAndKeepsItsUnlimitedMode() {
        var dialog = GdxTableDialog.autoCall(true, 1d);
        assertTrue(dialog.selectAutoCallExactText("1,27"));
        assertEquals(1.27d, dialog.autoCallAmount());
        assertFalse(dialog.selectAutoCallExactText("0.02"));
        assertFalse(dialog.selectAutoCallExactText("1.234"));
        assertEquals(1.27d, dialog.autoCallAmount());
        dialog.selectAutoCallSliderCents(250);
        assertEquals(2.50d, dialog.autoCallAmount());
        dialog.toggleNoLimit();
        assertFalse(dialog.selectAutoCallExactText("3"));
        assertEquals(0d, dialog.autoCallAmount());
    }
}

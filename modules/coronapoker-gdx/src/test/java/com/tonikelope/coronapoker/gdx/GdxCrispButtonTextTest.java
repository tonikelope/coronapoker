package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import org.junit.jupiter.api.Test;

final class GdxCrispButtonTextTest {

    @Test
    void callCostDropsOnlyAsManyFractionalZerosAsTheWidthRequires() {
        BitmapFont large = amountFont(10);
        BitmapFont small = amountFont(5);
        GdxCrispButtonText.FontTiers tiers =
                new GdxCrispButtonText.FontTiers(large, small);
        GlyphLayout glyph = new GlyphLayout();
        GdxCrispButtonText.Result result = new GdxCrispButtonText.Result();

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "+0.30", 50, 10, tiers, result);
        assertEquals("+0.30", result.text());
        assertSame(large, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "+0.30", 40, 10, tiers, result);
        assertEquals("+0.3", result.text());
        assertSame(large, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "+10.00", 50, 10, tiers, result);
        assertEquals("+10", result.text());
        assertSame(large, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "+10.00", 30, 10, tiers, result);
        assertEquals("+10", result.text());
        assertSame(large, result.font());

        GdxCrispButtonText.resolveWithinBox(
                glyph, "+0.30", 40, 10, tiers, result);
        assertEquals("+0.30", result.text());
        assertSame(small, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "+0.30", 40, 10, tiers, result);
        assertEquals("+0.3", result.text());
        assertSame(large, result.font());
    }

    @Test
    void callCostKeepsMeaningfulDigitsAndFitsTheHeight() {
        BitmapFont large = amountFont(10);
        BitmapFont small = amountFont(5);
        GdxCrispButtonText.FontTiers tiers =
                new GdxCrispButtonText.FontTiers(large, small);
        GlyphLayout glyph = new GlyphLayout();
        GdxCrispButtonText.Result result = new GdxCrispButtonText.Result();

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "+0.03", 40, 10, tiers, result);
        assertEquals("+0.03", result.text());
        assertSame(small, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "+0.30", 40, 5, tiers, result);
        assertEquals("+0.30", result.text());
        assertSame(small, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "+0.30", 20, 5, tiers, result);
        assertEquals("+0.3", result.text());
        assertSame(small, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "+10", 20, 10, tiers, result);
        assertEquals("+10", result.text());
        assertSame(small, result.font());
    }

    @Test
    void fitsAmountsInsideActionLabelsAndCountersWithMultipleValues() {
        BitmapFont large = amountFont(10);
        BitmapFont small = amountFont(5);
        GdxCrispButtonText.FontTiers tiers =
                new GdxCrispButtonText.FontTiers(large, small);
        GlyphLayout glyph = new GlyphLayout();
        GdxCrispButtonText.Result result = new GdxCrispButtonText.Result();

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "IR (+0.30)", 100, 10, tiers, result);
        assertEquals("IR (+0.30)", result.text());
        assertSame(large, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "IR (+0.30)", 90, 10, tiers, result);
        assertEquals("IR (+0.3)", result.text());
        assertSame(large, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "0.10 / 0.20", 100, 10, tiers, result);
        assertEquals("0.10 / 0.2", result.text());
        assertSame(large, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "0.10 / 0.20", 90, 10, tiers, result);
        assertEquals("0.1 / 0.2", result.text());
        assertSame(large, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "9.50 + 10", 80, 10, tiers, result);
        assertEquals("9.5 + 10", result.text());
        assertSame(large, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(
                glyph, "9.50 + 10", 70, 10, tiers, result);
        assertEquals("9.50 + 10", result.text());
        assertSame(small, result.font());
    }

    @Test
    void appliesTheSelectedZeroPolicyBeforeChoosingTheFont() {
        BitmapFont large = amountFont(10);
        BitmapFont small = amountFont(5);
        var tiers = new GdxCrispButtonText.FontTiers(large, small);
        var glyph = new GlyphLayout();
        var result = new GdxCrispButtonText.Result();

        GdxCrispButtonText.resolveAmountWithinBox(glyph, "+0.30", 50, 10, tiers,
                result, GdxChipAmountText.ZeroMode.NEVER);
        assertEquals("+0.3", result.text());
        assertSame(large, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(glyph, "+0.30", 40, 10, tiers,
                result, GdxChipAmountText.ZeroMode.IF_SPACE);
        assertEquals("+0.3", result.text());
        assertSame(large, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(glyph, "+0.30", 40, 10, tiers,
                result, GdxChipAmountText.ZeroMode.ALWAYS);
        assertEquals("+0.30", result.text());
        assertSame(small, result.font());

        GdxCrispButtonText.resolveAmountWithinBox(glyph, "+10.00", 30, 10, tiers,
                result, GdxChipAmountText.ZeroMode.ALWAYS);
        assertEquals("+10", result.text());
        assertSame(large, result.font());
    }

    private static BitmapFont amountFont(int size) {
        BitmapFontData data = new BitmapFontData();
        data.capHeight = size;
        BitmapFont font = new BitmapFont(data, new TextureRegion(), false);
        for (char character : "+.0123456789 IR()/".toCharArray()) {
            Glyph glyph = new Glyph();
            glyph.id = character;
            glyph.width = size;
            glyph.xadvance = size;
            data.setGlyph(character, glyph);
        }
        return font;
    }

    @Test
    void snapsTheWorldCoordinateToAReal1440pFramebufferPixel() {
        float snapped = GdxCrispButtonText.snapWorldCoordinate(
                100.25f, 0f, 2560f, 1920f);

        assertEquals(100.5f, snapped, 0.0001f);
        assertEquals(134f, snapped * 2560f / 1920f, 0.0001f);
    }

    @Test
    void respectsViewportOffsetsAnd1080pOneToOneMapping() {
        assertEquals(100f, GdxCrispButtonText.snapWorldCoordinate(
                100.4f, 12f, 1920f, 1920f), 0.0001f);
    }

    @Test
    void snapsAgainstPhysicalPixelsWhenWindowsUsesDisplayScaling() {
        float snapped = GdxCrispButtonText.snapWorldCoordinate(
                100.25f, 0f, 1920f, 1920f, 4f / 3f);

        assertEquals(100.5f, snapped, 0.0001f);
        assertEquals(134f, snapped * 4f / 3f, 0.0001f);
    }

    @Test
    void invalidViewportMetricsLeaveTheCoordinateUntouched() {
        assertEquals(100.25f, GdxCrispButtonText.snapWorldCoordinate(
                100.25f, 0f, 0f, 1920f));
    }
}

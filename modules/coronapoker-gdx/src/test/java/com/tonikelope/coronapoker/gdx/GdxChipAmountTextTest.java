package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class GdxChipAmountTextTest {
    @Test
    void usesExactSuffixesAndPreservesSmallChipAmounts() {
        assertEquals("0.02", GdxChipAmountText.format(0.02d));
        assertEquals("999.99", GdxChipAmountText.format(999.99d));
        assertEquals("1K", GdxChipAmountText.format(1_000d));
        assertEquals("200K", GdxChipAmountText.format(200_000d));
        assertEquals("1M", GdxChipAmountText.format(1_000_000d));
        assertEquals("9.6M", GdxChipAmountText.format(9_600_000d));
        assertEquals("1.2B", GdxChipAmountText.format(1_200_000_000d));
        assertEquals("-2.5K", GdxChipAmountText.format(-2_500d));
        assertEquals("200.00005K", GdxChipAmountText.format(200_000.05d));
        assertEquals("1.000001M", GdxChipAmountText.format(1_000_001d));
    }

    @Test
    void keepsTheSignsAndSurroundingTextOfTheGameLabels() {
        assertEquals("+100K", GdxChipAmountText.compactLabel("+100000"));
        assertEquals("+2.50", GdxChipAmountText.compactLabel("+2.50"));
        assertEquals("SUBE (+200K)", GdxChipAmountText.actionLabel("SUBE (+200000)"));
        assertEquals("CHECK", GdxChipAmountText.actionLabel("CHECK"));
        assertEquals("", GdxChipAmountText.compactLabel(""));
    }

    @Test
    void tableAndSettingsUseTheSameSuffixes() {
        assertEquals("200K / 1M", GdxSettingsContract.blindLevelLabel(200_000d, 1_000_000d));
        assertEquals("0.10 / 0.20", GdxSettingsContract.blindLevelLabel(0.10d, 0.20d));
        GdxGameText spanish = new GdxGameText("es");
        assertEquals("500K", CoronaPokerGdxTable.communityPotText(spanish, "", 500_000d).amount());
        assertEquals("CIEGAS  (BBA) 100K / 200K",
                CoronaPokerGdxTable.communityBlindsText(spanish, 100_000d, 200_000d,
                        com.tonikelope.coronapoker.core.AnteMode.BIG_BLIND, 0, 1, 0));
    }
}

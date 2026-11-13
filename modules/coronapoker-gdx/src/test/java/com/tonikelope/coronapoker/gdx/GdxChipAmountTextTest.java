package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class GdxChipAmountTextTest {
    @Test
    void usesExactSuffixesAndPreservesSmallChipAmounts() {
        assertEquals("0.02", GdxChipAmountText.format(0.02d));
        assertEquals("0.10", GdxChipAmountText.format(0.10d));
        assertEquals("-2.50", GdxChipAmountText.format(-2.50d));
        assertEquals("2", GdxChipAmountText.format(2d));
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
    void buyinLabelsStayExactWithoutLongFractionalSuffixes() {
        assertEquals("200K", GdxChipAmountText.buyinLabel(200_000, "es"));
        assertEquals("9.99M", GdxChipAmountText.buyinLabel(9_990_000, "es"));
        assertEquals("9.999.830", GdxChipAmountText.buyinLabel(9_999_830, "es"));
        assertEquals("9.999.999", GdxChipAmountText.buyinLabel(9_999_999, "es"));
        assertEquals("9,999,999", GdxChipAmountText.buyinLabel(9_999_999, "en"));
        assertEquals("10M", GdxChipAmountText.buyinLabel(10_000_000, "es"));
    }

    @Test
    void parsesWholeChipsExactlyFromTypedAndPastedAmounts() {
        assertEquals(OptionalInt.of(200_000), GdxChipAmountText.parseWholeChips("200K", "es"));
        assertEquals(OptionalInt.of(10_000_000), GdxChipAmountText.parseWholeChips(" 10m ", "es"));
        assertEquals(OptionalInt.of(10_500_000), GdxChipAmountText.parseWholeChips("10,5M", "es"));
        assertEquals(OptionalInt.of(9_999_999), GdxChipAmountText.parseWholeChips("9.999999M", "es"));
        assertEquals(OptionalInt.of(2_000_000_000), GdxChipAmountText.parseWholeChips("2B", "es"));
        assertEquals(OptionalInt.of(9_999_830), GdxChipAmountText.parseWholeChips("9.999.830", "es"));
        assertEquals(OptionalInt.of(9_999_830), GdxChipAmountText.parseWholeChips("9,999,830", "en"));
        assertEquals(OptionalInt.of(9_999_830), GdxChipAmountText.parseWholeChips("9999830", "es"));
    }

    @Test
    void rejectsFractionalOverflowAndMalformedChipAmounts() {
        for (String input : List.of("", " ", "-10M", "1e6", "1.25", "0.0001K",
                "3B", "2147483648", "1,00,000", "1.234,567", "10M chips",
                "1 M", "1..2K", "9".repeat(40))) {
            assertTrue(GdxChipAmountText.parseWholeChips(input, "es").isEmpty(), input);
        }
        assertTrue(GdxChipAmountText.parseWholeChips("1.200", "en").isEmpty());
        assertTrue(GdxChipAmountText.parseWholeChips("1,200", "es").isEmpty());
    }

    @Test
    void keepsTheSignsAndSurroundingTextOfTheGameLabels() {
        assertEquals("+100K", GdxChipAmountText.compactLabel("+100000"));
        assertEquals("+2.50", GdxChipAmountText.compactLabel("+2.50"));
        assertEquals("+0.20", GdxChipAmountText.compactLabel("+0.2"));
        assertEquals("SUBE (+0.60)", GdxChipAmountText.actionLabel("SUBE (+0.6)"));
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

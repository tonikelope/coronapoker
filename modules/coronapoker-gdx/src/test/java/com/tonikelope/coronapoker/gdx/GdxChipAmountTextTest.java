package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.OptionalInt;
import java.util.Properties;
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
    void removesOnlyOneTrailingDecimalZeroFromTheRightmostEligibleAmount() {
        assertEquals("0.10 / 0.2 @ 20' (10)",
                GdxChipAmountText.removeLastFractionalZero("0.10 / 0.20 @ 20' (10)"));
        assertEquals("0.1 / 0.25",
                GdxChipAmountText.removeLastFractionalZero("0.10 / 0.25"));
        assertEquals("RAISE (+10.0)",
                GdxChipAmountText.removeLastFractionalZero("RAISE (+10.00)"));
        assertEquals("RAISE (+10)",
                GdxChipAmountText.removeLastFractionalZero("RAISE (+10.0)"));
        assertEquals("9.5 + 10",
                GdxChipAmountText.removeLastFractionalZero("9.50 + 10"));
        assertEquals("-1.02K", GdxChipAmountText.removeLastFractionalZero("-1.020K"));
        assertEquals("0.01 / 200",
                GdxChipAmountText.removeLastFractionalZero("0.01 / 200"));
        assertEquals("PAIR (65.50%)",
                GdxChipAmountText.removeLastFractionalZero("PAIR (65.50%)"));
        assertEquals("1,000.00",
                GdxChipAmountText.removeLastFractionalZero("1,000.00"));
    }

    @Test
    void amountZeroPreferenceUsesTheSameDefaultForStorageAndTheSelector() {
        Properties values = new Properties();
        var choice = GdxAppearanceOptions.AMOUNT_ZEROS;
        assertEquals(GdxChipAmountText.ZeroMode.NEVER,
                GdxChipAmountText.ZeroMode.configured(values));
        assertEquals(0, GdxAppearanceOptions.selectedIndex(choice, values));
        GdxAppearanceOptions.select(choice, values, 2);
        assertEquals(GdxChipAmountText.ZeroMode.ALWAYS,
                GdxChipAmountText.ZeroMode.configured(values));
        GdxSettingsContract.restoreAppearancePageDefaults(values, 0, true);
        assertEquals(0, GdxAppearanceOptions.selectedIndex(choice, values));
        values.setProperty(choice.key(), "invalid");
        assertEquals(0, GdxAppearanceOptions.selectedIndex(choice, values));
        assertEquals(GdxChipAmountText.ZeroMode.NEVER,
                GdxChipAmountText.ZeroMode.configured(values));
        assertEquals("0.03 / 10", GdxChipAmountText.ZeroMode.NEVER.display("0.03 / 10.00"));
        assertEquals("1.2K", GdxChipAmountText.ZeroMode.ALWAYS.display("1.2K"));
        assertEquals("0.10 / 0.20",
                GdxChipAmountText.ZeroMode.ALWAYS.display("0.1 / 0.2"));
    }

    @Test
    void everyModeKeepsWholeAmountsFreeOfDecimalZeros() {
        for (var mode : GdxChipAmountText.ZeroMode.values()) {
            assertEquals("9 / 0 / +3 / -7", mode.display("9.00 / 0.00 / +3.0 / -7.00"));
            assertEquals("RAISE (+10)", mode.display("RAISE (+10.00)"));
            assertEquals("10", mode.display("10"));
            assertEquals("1.00001K", mode.display("1.00001K"));
            assertEquals("6.02", mode.display("6.02"));
            assertEquals("PAIR (60.00%)", mode.display("PAIR (60.00%)"));
        }
        assertEquals("6.2", GdxChipAmountText.ZeroMode.NEVER.display("6.20"));
        assertEquals("6.20", GdxChipAmountText.ZeroMode.IF_SPACE.display("6.2"));
        assertEquals("6.20", GdxChipAmountText.ZeroMode.ALWAYS.display("6.2"));
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

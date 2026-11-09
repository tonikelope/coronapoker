/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** Exact chip amounts for display, including the fractional part of each unit. */
final class GdxChipAmountText {
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000L);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal BILLION = BigDecimal.valueOf(1_000_000_000L);
    private static final Pattern DECIMAL = Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?");

    private GdxChipAmountText() {
    }

    static String format(double amount) {
        return format(amount, 0);
    }

    static String format(double amount, int minimumDecimals) {
        BigDecimal value = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        if (value.abs().compareTo(THOUSAND) >= 0) return withSuffix(value);
        value = value.stripTrailingZeros();
        return value.setScale(Math.max(minimumDecimals, value.scale())).toPlainString();
    }

    static String compactLabel(String label) {
        if (!DECIMAL.matcher(label).matches()) return label;
        BigDecimal value = new BigDecimal(label);
        if (value.abs().compareTo(THOUSAND) < 0) return label;
        return (label.startsWith("+") ? "+" : "") + withSuffix(value);
    }

    static String actionLabel(String label) {
        int amount = label.lastIndexOf(" (+");
        if (amount < 0 || !label.endsWith(")")) return label;
        return label.substring(0, amount + 2)
                + compactLabel(label.substring(amount + 2, label.length() - 1)) + ")";
    }

    private static String withSuffix(BigDecimal value) {
        BigDecimal magnitude = value.abs();
        if (magnitude.compareTo(BILLION) >= 0) return scaled(value, 9) + "B";
        if (magnitude.compareTo(MILLION) >= 0) return scaled(value, 6) + "M";
        return scaled(value, 3) + "K";
    }

    private static String scaled(BigDecimal value, int places) {
        return value.movePointLeft(places).stripTrailingZeros().toPlainString();
    }
}

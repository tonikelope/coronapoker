/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/** Exact chip amounts for display, including the fractional part of each unit. */
final class GdxChipAmountText {
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000L);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal BILLION = BigDecimal.valueOf(1_000_000_000L);
    private static final Pattern DECIMAL = Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?");

    private static final Pattern WHOLE_CHIPS = Pattern.compile("[0-9]+");
    private static final Pattern DOT_GROUPED_CHIPS = Pattern.compile(
            "[1-9][0-9]{0,2}(?:\\.[0-9]{3})+");
    private static final Pattern COMMA_GROUPED_CHIPS = Pattern.compile(
            "[1-9][0-9]{0,2}(?:,[0-9]{3})+");
    private static final Pattern SUFFIXED_CHIPS = Pattern.compile(
            "([0-9]+(?:[.,][0-9]+)?)([KMB])", Pattern.CASE_INSENSITIVE);

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

    static String buyinLabel(int amount, String language) {
        String compact = format(amount);
        int decimal = compact.indexOf('.');
        if (decimal < 0 || compact.length() - decimal - 2 <= 2) {
            return compact;
        }
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag(language))
                .format(amount);
    }

    static OptionalInt parseWholeChips(String input, String language) {
        String value = input.strip();
        if (value.length() > 32) return OptionalInt.empty();
        try {
            if (WHOLE_CHIPS.matcher(value).matches()) {
                return OptionalInt.of(Integer.parseInt(value));
            }
            var suffixed = SUFFIXED_CHIPS.matcher(value);
            if (suffixed.matches()) {
                int places = switch (suffixed.group(2).toUpperCase(Locale.ROOT)) {
                    case "K" -> 3;
                    case "M" -> 6;
                    default -> 9;
                };
                return OptionalInt.of(new BigDecimal(
                        suffixed.group(1).replace(',', '.'))
                        .movePointRight(places).intValueExact());
            }
            char grouping = DecimalFormatSymbols.getInstance(Locale.forLanguageTag(language))
                    .getGroupingSeparator();
            if (grouping == '.' && DOT_GROUPED_CHIPS.matcher(value).matches()) {
                return OptionalInt.of(Integer.parseInt(value.replace(".", "")));
            }
            if (grouping == ',' && COMMA_GROUPED_CHIPS.matcher(value).matches()) {
                return OptionalInt.of(Integer.parseInt(value.replace(",", "")));
            }
        } catch (NumberFormatException | ArithmeticException invalid) {
            return OptionalInt.empty();
        }
        return OptionalInt.empty();
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

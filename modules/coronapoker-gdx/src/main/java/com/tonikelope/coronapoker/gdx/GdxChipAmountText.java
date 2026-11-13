/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.regex.Pattern;

/** Exact chip amounts for display, including the fractional part of each unit. */
final class GdxChipAmountText {
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000L);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal BILLION = BigDecimal.valueOf(1_000_000_000L);
    private static final Pattern DECIMAL = Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?");

    private static final Pattern FRACTIONAL_ZEROS = Pattern.compile(
            "(?<![\\p{L}\\p{N}.,])[0-9]+(\\.)[0-9]*?(0+)"
                    + "(?=[KMB]?(?![\\p{L}\\p{N}.,%]))");

    private static final Pattern WHOLE_CHIPS = Pattern.compile("[0-9]+");
    private static final Pattern DOT_GROUPED_CHIPS = Pattern.compile(
            "[1-9][0-9]{0,2}(?:\\.[0-9]{3})+");
    private static final Pattern COMMA_GROUPED_CHIPS = Pattern.compile(
            "[1-9][0-9]{0,2}(?:,[0-9]{3})+");
    private static final Pattern SUFFIXED_CHIPS = Pattern.compile(
            "([0-9]+(?:[.,][0-9]+)?)([KMB])", Pattern.CASE_INSENSITIVE);

    static final String ZEROS_KEY = "amount_decimal_zeros";
    private static final Pattern AMOUNT_TOKEN = Pattern.compile(
            "([+-]?[0-9]+(?:\\.[0-9]+)?)([KMB]?)");
    private static final Pattern FRACTIONAL_AMOUNT = Pattern.compile(
            "(?<![\\p{L}\\p{N}.,])[0-9]+\\.([0-9])(?=[KMB]?(?![\\p{L}\\p{N}.,%]))");

    enum ZeroMode {
        NEVER("never"), IF_SPACE("if_space"), ALWAYS("always");

        final String setting;

        ZeroMode(String setting) {
            this.setting = setting;
        }

        static ZeroMode configured(Properties properties) {
            return switch (properties.getProperty(ZEROS_KEY, IF_SPACE.setting)) {
                case "never" -> NEVER;
                case "always" -> ALWAYS;
                default -> IF_SPACE;
            };
        }

        String display(String label) {
            return switch (this) {
                case NEVER -> trimFractionalZeros(label);
                case IF_SPACE -> label;
                case ALWAYS -> minimumTwoDecimals(label);
            };
        }
    }

    static String displayChipLabel(String label, ZeroMode mode) {
        if (mode == ZeroMode.ALWAYS && !AMOUNT_TOKEN.matcher(label).matches()) {
            if (DOT_GROUPED_CHIPS.matcher(label).matches()) {
                return label + ",00";
            }
            if (COMMA_GROUPED_CHIPS.matcher(label).matches()) {
                return label + ".00";
            }
        }
        return mode.display(label);
    }

    private static String minimumTwoDecimals(String label) {
        var amount = AMOUNT_TOKEN.matcher(label);
        if (amount.matches()) {
            String number = amount.group(1);
            int dot = number.indexOf('.');
            if (dot < 0) return number + ".00" + amount.group(2);
            if (dot == number.length() - 2) return number + "0" + amount.group(2);
            return label;
        }
        return FRACTIONAL_AMOUNT.matcher(label).replaceAll(match -> match.group() + "0");
    }

    private static String trimFractionalZeros(String label) {
        String next = removeLastFractionalZero(label);
        while (!next.equals(label)) {
            label = next;
            next = removeLastFractionalZero(label);
        }
        return label;
    }

    private GdxChipAmountText() {
    }

    static String format(double amount) {
        return format(amount, 0);
    }

    static String format(double amount, int minimumDecimals) {
        BigDecimal value = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        if (value.abs().compareTo(THOUSAND) >= 0) return withSuffix(value);
        BigDecimal normalized = value.stripTrailingZeros();
        int decimals = normalized.scale() > 0 ? 2 : 0;
        return value.setScale(Math.max(minimumDecimals, decimals)).toPlainString();
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
        if (value.abs().compareTo(THOUSAND) >= 0) {
            return (label.startsWith("+") ? "+" : "") + withSuffix(value);
        }
        return value.scale() == 1 ? label + "0" : label;
    }

    /** Removes one zero from the rightmost amount that can be shortened. */
    static String removeLastFractionalZero(String label) {
        if (label.indexOf('.') < 0) return label;
        var amounts = FRACTIONAL_ZEROS.matcher(label);
        int from = -1;
        int to = -1;
        while (amounts.find()) {
            to = amounts.end(2);
            from = to - 1;
            if (from == amounts.start(1) + 1) from--;
        }
        return from < 0 ? label : label.substring(0, from) + label.substring(to);
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

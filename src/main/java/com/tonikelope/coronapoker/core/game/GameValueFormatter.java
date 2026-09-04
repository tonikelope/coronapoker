/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

/** Frontend-neutral formatting required by canonical game messages and overlays. */
public interface GameValueFormatter {

    String money(double amount);

    String elapsed(long seconds);

    String framedTitle(String text);

    float decimal(float value);

    static GameValueFormatter plain() {
        return PlainFormatter.INSTANCE;
    }

    final class PlainFormatter implements GameValueFormatter {
        private static final PlainFormatter INSTANCE = new PlainFormatter();

        private PlainFormatter() {
        }

        @Override
        public String money(double amount) {
            return BigDecimal.valueOf(MoneyMath.clean(amount)).stripTrailingZeros().toPlainString();
        }

        @Override
        public String elapsed(long seconds) {
            long days = TimeUnit.SECONDS.toDays(seconds);
            long remainder = seconds - TimeUnit.DAYS.toSeconds(days);
            long hours = TimeUnit.SECONDS.toHours(remainder);
            remainder -= TimeUnit.HOURS.toSeconds(hours);
            long minutes = TimeUnit.SECONDS.toMinutes(remainder);
            remainder -= TimeUnit.MINUTES.toSeconds(minutes);
            return (days > 0 ? String.format("%02dD ", days) : "")
                    + String.format("%02d:%02d:%02d", hours, minutes, remainder);
        }

        @Override
        public String framedTitle(String text) {
            return text;
        }

        @Override
        public float decimal(float value) {
            return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).floatValue();
        }
    }
}

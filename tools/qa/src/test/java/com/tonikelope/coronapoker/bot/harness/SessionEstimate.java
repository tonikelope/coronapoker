/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.bot.harness;

import java.util.Arrays;

/** Treats sessions as the independent samples because bots learn within a session. */
record SessionEstimate(double mean, double standardError) {

    static SessionEstimate of(double[] samples) {
        double mean = Arrays.stream(samples).average().orElse(Double.NaN);
        if (samples.length < 2) {
            return new SessionEstimate(mean, Double.NaN);
        }
        double squaredDeviations = 0;
        for (double sample : samples) {
            double deviation = sample - mean;
            squaredDeviations += deviation * deviation;
        }
        return new SessionEstimate(mean, Math.sqrt(squaredDeviations / (samples.length - 1) / samples.length));
    }

    double tStatistic() {
        return mean / standardError;
    }

    boolean clears(double minimumMargin, double minimumT) {
        return mean > minimumMargin && tStatistic() > minimumT;
    }
}

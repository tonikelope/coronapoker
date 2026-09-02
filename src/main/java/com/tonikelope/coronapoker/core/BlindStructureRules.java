package com.tonikelope.coronapoker.core;

/** Renderer-neutral validation and built-in ladder for blind structures. */
public final class BlindStructureRules {

    public static final int MAX_LEVELS = 64;
    public static final double MIN_BLIND = 0.05;
    public static final double BLIND_STEP = 0.05;
    public static final double MAX_BLIND = 4_000_000;

    public static final String ERR_NO_LEVELS = "blinds.err_no_levels";
    public static final String ERR_TOO_MANY_LEVELS = "blinds.err_too_many_levels";
    public static final String ERR_VALUE_RANGE = "blinds.err_value_range";
    public static final String ERR_PRECISION = "blinds.err_precision";
    public static final String ERR_BB_LT_SB = "blinds.err_bb_lt_sb";
    public static final String ERR_NOT_INCREASING = "blinds.err_not_increasing";

    private BlindStructureRules() {
    }

    public static String validateLevels(double[][] levels) {
        if (levels == null || levels.length == 0) {
            return ERR_NO_LEVELS;
        }
        if (levels.length > MAX_LEVELS) {
            return ERR_TOO_MANY_LEVELS;
        }
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == null || levels[i].length != 2) {
                return ERR_NO_LEVELS;
            }
            double small = levels[i][0];
            double big = levels[i][1];
            if (small < MIN_BLIND || big < MIN_BLIND
                    || small > MAX_BLIND || big > MAX_BLIND
                    || !Double.isFinite(small) || !Double.isFinite(big)) {
                return ERR_VALUE_RANGE;
            }
            if (!isBlindStep(small) || !isBlindStep(big)) {
                return ERR_PRECISION;
            }
            if (big < small) {
                return ERR_BB_LT_SB;
            }
            if (i > 0 && (small <= levels[i - 1][0] || big <= levels[i - 1][1])) {
                return ERR_NOT_INCREASING;
            }
        }
        return null;
    }

    public static double[][] defaultLevels() {
        double[] smallBlinds = {
            0.1, 0.2, 0.3, 0.5,
            1, 2, 3, 5,
            10, 20, 30, 50,
            100, 200, 300, 500,
            1000, 2000, 3000, 5000,
            10000, 20000, 30000, 50000,
            100000, 200000, 300000, 500000,
            1000000, 2000000
        };
        double[][] result = new double[smallBlinds.length][];
        for (int i = 0; i < smallBlinds.length; i++) {
            result[i] = new double[]{smallBlinds[i], smallBlinds[i] * 2};
        }
        return result;
    }

    private static boolean isBlindStep(double value) {
        double scaled = value / BLIND_STEP;
        return Math.abs(scaled - Math.rint(scaled)) < 0.01;
    }
}

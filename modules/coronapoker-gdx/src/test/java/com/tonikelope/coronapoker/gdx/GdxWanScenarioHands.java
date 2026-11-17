/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

/** WAN depth is independent of the ordinary topology soak. */
final class GdxWanScenarioHands {
    static final String SUSTAINED = "coronapoker.qa.gdx.wanSoakHands";
    static final String EXTREME = "coronapoker.qa.gdx.wanExtremeHands";
    static final String LEGACY_SOAK = "coronapoker.qa.gdx.soakHands";

    private GdxWanScenarioHands() { }

    static int sustained() {
        String configured = System.getProperty(SUSTAINED);
        return configured == null || configured.isBlank()
                ? read(LEGACY_SOAK, 3) : parse(SUSTAINED, configured);
    }

    static int extreme() {
        return read(EXTREME, 3);
    }

    private static int read(String property, int fallback) {
        String configured = System.getProperty(property);
        return configured == null || configured.isBlank() ? fallback : parse(property, configured);
    }

    private static int parse(String property, String configured) {
        int hands = Integer.parseInt(configured);
        if (hands < 2 || hands > 1_000) {
            throw new IllegalArgumentException(property + " must be 2..1000 to exercise a hand transition");
        }
        return hands;
    }
}

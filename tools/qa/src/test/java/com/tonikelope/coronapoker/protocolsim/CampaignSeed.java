package com.tonikelope.coronapoker.protocolsim;

import java.security.SecureRandom;
import java.util.Locale;

/** Resolves a replayable campaign seed without retaining historical defaults. */
public final class CampaignSeed {

    private CampaignSeed() {
    }

    public static long resolve(String propertyName, String campaign) {
        String text = System.getProperty(propertyName);
        boolean omitted = text == null || text.isBlank() || text.startsWith("${");
        long seed;
        if (omitted) {
            seed = Integer.toUnsignedLong(new SecureRandom().nextInt());
            if (seed == 0L) {
                seed = 1L;
            }
        } else {
            try {
                seed = Long.parseLong(text);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(propertyName + " must be a long: " + text, ex);
            }
        }
        System.out.println(formatMarker(campaign, seed, omitted));
        return seed;
    }

    static String formatMarker(String campaign, long seed, boolean random) {
        return String.format(Locale.ROOT,
                "CP_QA_SEED campaign=%s seed=%d source=%s",
                campaign, seed, random ? "random" : "explicit");
    }
}

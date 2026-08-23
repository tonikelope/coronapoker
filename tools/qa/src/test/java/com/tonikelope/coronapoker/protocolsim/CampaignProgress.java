package com.tonikelope.coronapoker.protocolsim;

/** Compact semantic progress emitted by long headless campaigns. */
public final class CampaignProgress {

    private CampaignProgress() {
    }

    public static boolean shouldReport(int completed, int requested) {
        if (requested < 1 || completed < 0 || completed > requested) {
            throw new IllegalArgumentException("invalid campaign progress");
        }
        int step = Math.max(1, requested / 10);
        return completed == 0 || completed == requested || completed % step == 0;
    }

    public static void report(String campaign, int completed, int requested) {
        if (!campaign.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("invalid campaign name");
        }
        if (shouldReport(completed, requested)) {
            System.out.printf("CP_HEADLESS_PROGRESS campaign=%s completed=%d requested=%d%n",
                    campaign, completed, requested);
        }
    }
}

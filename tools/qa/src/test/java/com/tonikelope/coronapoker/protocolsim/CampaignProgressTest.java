package com.tonikelope.coronapoker.protocolsim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CampaignProgressTest {

    @Test
    void reportsStartFinishAndBoundedIntermediateProgress() {
        assertTrue(CampaignProgress.shouldReport(0, 5_000));
        assertFalse(CampaignProgress.shouldReport(1, 5_000));
        assertTrue(CampaignProgress.shouldReport(500, 5_000));
        assertFalse(CampaignProgress.shouldReport(501, 5_000));
        assertTrue(CampaignProgress.shouldReport(5_000, 5_000));
    }

    @Test
    void smallCampaignsReportEveryCompletedCase() {
        assertTrue(CampaignProgress.shouldReport(0, 3));
        assertTrue(CampaignProgress.shouldReport(1, 3));
        assertTrue(CampaignProgress.shouldReport(2, 3));
        assertTrue(CampaignProgress.shouldReport(3, 3));
    }

    @Test
    void progressMarkerIsOneCompleteParseableLine() {
        assertEquals("CP_HEADLESS_PROGRESS campaign=protocol completed=50 requested=500",
                CampaignProgress.formatMarker("protocol", 50, 500));
    }
}

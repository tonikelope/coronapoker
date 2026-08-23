package com.tonikelope.coronapoker.protocolsim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignSeedTest {

    @Test
    void explicitSeedIsPreservedForReplay() {
        String property = "qa.test.seed.explicit";
        System.setProperty(property, "42");
        try {
            assertEquals(42L, CampaignSeed.resolve(property, "unit"));
        } finally {
            System.clearProperty(property);
        }
        assertEquals("CP_QA_SEED campaign=unit seed=42 source=explicit",
                CampaignSeed.formatMarker("unit", 42L, false));
    }

    @Test
    void omittedSeedComesFromFreshUnsignedEntropyRange() {
        long seed = CampaignSeed.resolve("qa.test.seed.omitted", "unit");
        assertTrue(seed >= 1L && seed <= 0xffff_ffffL);
    }
}

package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class VisualOnlyIdentityFailureClassificationTest {

    @Test
    public void substitutedRosterKeyIsGameplayImpactNotVisualOnly() throws Exception {
        assertTrue(IdentitySubstitutionPoc.currentRosterAndActionPipelineAccepts(
                IdentitySubstitutionPoc.keyPair()),
                "If this becomes false, the substitution can be classified as visual-only");
    }
}

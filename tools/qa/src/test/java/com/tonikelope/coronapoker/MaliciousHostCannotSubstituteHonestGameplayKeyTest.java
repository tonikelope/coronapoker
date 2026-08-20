package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class MaliciousHostCannotSubstituteHonestGameplayKeyTest {

    @Test
    public void substitutedActionCannotReachCleanSettlementConsensus() throws Exception {
        java.security.KeyPair attacker = IdentitySubstitutionPoc.keyPair();
        assertTrue(IdentitySubstitutionPoc.currentRosterAndActionPipelineAccepts(attacker),
                "The accepted residual risk requires exercising the actual substitution path");

        HandStateChain victimView = IdentitySubstitutionPoc.newChain();
        HandStateChain honestView = IdentitySubstitutionPoc.newChain();
        byte[] forged = IdentitySubstitutionPoc.actionRecord(
                victimView.getCurrentHash(), victimView.getHandId());
        byte[] forgedSig = IdentitySubstitutionPoc.sign(attacker, "ACTION\0", forged);
        assertTrue(IdentityManager.verifyAction(
                IdentitySubstitutionPoc.rawPublicKey(attacker), forged, forgedSig));

        victimView.absorb(forged, forgedSig);
        byte[] victimFinal = victimView.absorbSettlement(new byte[]{1});
        byte[] honestFinal = honestView.absorbSettlement(new byte[]{1});

        assertFalse(Crupier.consensusFinalHashMatches(victimFinal, honestFinal),
                "A substituted action must diverge H_final and block settlement consensus");
    }
}

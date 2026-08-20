package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class InvalidRosterEntryCannotVerifyActionTest {

    @Test
    public void invalidSelfSignatureIsRejectedBeforeGameplay() throws Exception {
        java.security.KeyPair announced = IdentitySubstitutionPoc.keyPair();
        byte[] raw = IdentitySubstitutionPoc.rawPublicKey(announced);

        assertFalse(IdentityManager.verifyJoin(IdentitySubstitutionPoc.SESSION_ID,
                IdentitySubstitutionPoc.HONEST_NICK, raw, new byte[64]));
    }
}

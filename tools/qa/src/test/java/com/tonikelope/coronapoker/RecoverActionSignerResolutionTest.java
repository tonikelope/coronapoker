package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;

import org.junit.jupiter.api.Test;

public class RecoverActionSignerResolutionTest {

    private static final byte[] LOCAL_KEY = new byte[]{1};
    private static final byte[] REMOTE_KEY = new byte[]{2};
    private static final byte[] HOST_KEY = new byte[]{3};

    @Test
    public void voluntaryHostActionUsesTheLocalIdentityDuringHostRecovery() {
        assertArrayEquals(LOCAL_KEY, Crupier.selectGameplaySignerPubkey(
                false, true, LOCAL_KEY, null, LOCAL_KEY));
    }

    @Test
    public void voluntaryClientActionUsesTheLocalIdentityDuringClientRecovery() {
        assertArrayEquals(LOCAL_KEY, Crupier.selectGameplaySignerPubkey(
                false, true, LOCAL_KEY, null, HOST_KEY));
    }

    @Test
    public void localShowdownProofUsesLocalIdentityWithoutParticipantEntry() {
        assertArrayEquals(LOCAL_KEY, Crupier.selectGameplaySignerPubkey(
                false, true, LOCAL_KEY, null, HOST_KEY));
    }

    @Test
    public void remoteHumanActionUsesThatParticipantsIdentity() {
        assertArrayEquals(REMOTE_KEY, Crupier.selectGameplaySignerPubkey(
                false, false, LOCAL_KEY, REMOTE_KEY, HOST_KEY));
    }

    @Test
    public void botAndSyntheticActionsUseTheHostIdentity() {
        assertArrayEquals(HOST_KEY, Crupier.selectGameplaySignerPubkey(
                true, false, LOCAL_KEY, null, HOST_KEY));
        assertArrayEquals(HOST_KEY, Crupier.selectGameplaySignerPubkey(
                true, false, LOCAL_KEY, REMOTE_KEY, HOST_KEY));
    }

    @Test
    public void unknownVoluntaryRemoteActorFailsClosed() {
        assertNull(Crupier.selectGameplaySignerPubkey(
                false, false, LOCAL_KEY, null, HOST_KEY));
    }

    @Test
    public void selectedLocalIdentityCryptographicallyVerifiesThePersistedAction() throws Exception {
        KeyPair local = IdentitySubstitutionPoc.keyPair();
        KeyPair host = IdentitySubstitutionPoc.keyPair();
        HandStateChain chain = IdentitySubstitutionPoc.newChain();
        byte[] record = IdentitySubstitutionPoc.actionRecord(
                chain.getCurrentHash(), chain.getHandId());
        byte[] signature = IdentitySubstitutionPoc.sign(local, "ACTION\0", record);
        byte[] selected = Crupier.selectGameplaySignerPubkey(
                false, true,
                IdentitySubstitutionPoc.rawPublicKey(local), null,
                IdentitySubstitutionPoc.rawPublicKey(host));

        assertTrue(Crupier.recoveredActionSignatureIsValid(selected, record, signature));
        assertFalse(Crupier.recoveredActionSignatureIsValid(
                IdentitySubstitutionPoc.rawPublicKey(host), record, signature));
    }
}

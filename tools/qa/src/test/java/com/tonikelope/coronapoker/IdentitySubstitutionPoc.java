package com.tonikelope.coronapoker;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Collections;

final class IdentitySubstitutionPoc {

    static final byte[] SESSION_ID = "malicious-host-session".getBytes(StandardCharsets.UTF_8);
    static final String HONEST_NICK = "honest-b";

    private IdentitySubstitutionPoc() {
    }

    static KeyPair keyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    static byte[] rawPublicKey(KeyPair pair) {
        return IdentityManager.x509PubKeyToRaw(pair.getPublic().getEncoded());
    }

    static byte[] sign(KeyPair pair, String domain, byte[] payload) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(pair.getPrivate());
        signature.update(domain.getBytes(StandardCharsets.UTF_8));
        signature.update(payload);
        return signature.sign();
    }

    static byte[] actionRecord(byte[] previousHash, byte[] handId) {
        return CanonicalActionRecord.encode(
                previousHash, handId,
                CanonicalActionRecord.playerIdFromNick(HONEST_NICK),
                0, CanonicalActionRecord.ACTION_CHECK, 0, false, true);
    }

    static HandStateChain newChain() {
        return HandStateChain.start(new byte[16],
                Collections.singletonList(CanonicalActionRecord.playerIdFromNick(HONEST_NICK)),
                Collections.singletonList(new byte[32]),
                Collections.singletonList(new byte[32]),
                new byte[]{1, 2, 3});
    }

    static boolean currentRosterAndActionPipelineAccepts(KeyPair announcedKey) throws Exception {
        byte[] raw = rawPublicKey(announcedKey);
        byte[] selfSig = sign(announcedKey, "JOIN\0",
                IdentityManager.joinPayload(SESSION_ID, HONEST_NICK, raw));
        HandStateChain chain = newChain();
        byte[] record = actionRecord(chain.getCurrentHash(), chain.getHandId());
        byte[] actionSig = sign(announcedKey, "ACTION\0", record);

        return IdentityManager.verifyJoin(SESSION_ID, HONEST_NICK, raw, selfSig)
                && IdentityManager.verifyAction(raw, record, actionSig);
    }
}

package com.tonikelope.coronapoker.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The hand and expected keys against which a live shuffle bundle is verified. */
public final class ShuffleProofContext {
    private final String sessionId;
    private final byte[] handId;
    private final List<String> ring;
    private final List<String> shuffleOrder;
    private final Map<String, byte[]> pocketKeys;
    private final Map<String, byte[]> communityKeys;

    public ShuffleProofContext(String sessionId, byte[] handId, String host, String[] ring,
            Map<String, byte[]> pocketKeys, Map<String, byte[]> communityKeys) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.handId = handId.clone();
        this.ring = List.of(ring);
        if (this.ring.size() < 2 || this.ring.size() > 10 || !this.ring.contains(host)) {
            throw new IllegalArgumentException("Invalid shuffle roster");
        }
        List<String> order = new ArrayList<>(this.ring);
        order.remove(host);
        order.add(0, host);
        this.shuffleOrder = List.copyOf(order);
        this.pocketKeys = copyKeys(this.ring, pocketKeys);
        this.communityKeys = copyKeys(this.ring, communityKeys);
    }

    private static Map<String, byte[]> copyKeys(List<String> ring, Map<String, byte[]> keys) {
        if (keys.size() != ring.size() || !keys.keySet().containsAll(ring)) {
            throw new IllegalArgumentException("Shuffle keys must cover the exact roster");
        }
        Map<String, byte[]> copy = new HashMap<>();
        for (String nick : ring) {
            byte[] encoded = keys.get(nick);
            if (decodeKey(encoded) == null || copy.put(nick, encoded.clone()) != null) {
                throw new IllegalArgumentException("Invalid key or duplicate contributor");
            }
        }
        return Map.copyOf(copy);
    }

    static EdwardsPoint decodeKey(byte[] encoded) {
        EdwardsPoint key = encoded == null || encoded.length != 32 ? null : Ristretto255.decode(encoded);
        return key == null || Ristretto255.isIdentity(key) ? null : key;
    }

    public int size() { return ring.size(); }

    public Step shuffleStep(int position) {
        return new Step(sessionId, handId, shuffleOrder.get(position), position);
    }

    public Step rotationStep(int position) {
        return new Step(sessionId, handId, ring.get(position), position);
    }

    public byte[] pocketKey(Step step) { return pocketKeys.get(step.participant()).clone(); }

    public byte[] communityKey(Step step) { return communityKeys.get(step.participant()).clone(); }

    /** Context shared by the prover and verifier for one contribution. */
    public record Step(String sessionId, byte[] handId, String participant, int position) {
        public Step {
            if (sessionId == null || sessionId.isEmpty() || handId == null || handId.length != 16
                    || participant == null || participant.isEmpty() || position < 0 || position >= 10) {
                throw new IllegalArgumentException("Invalid contribution context");
            }
            handId = handId.clone();
        }

        @Override public byte[] handId() { return handId.clone(); }

        Transcript transcript(String domain, EdwardsPoint[] before, EdwardsPoint[] after,
                byte[] pocketKey, byte[] communityKey) {
            Transcript tr = new Transcript(domain);
            tr.absorb("session", sessionId.getBytes(StandardCharsets.UTF_8));
            tr.absorb("hand", handId);
            tr.absorb("participant", participant.getBytes(StandardCharsets.UTF_8));
            tr.absorb("position", ByteBuffer.allocate(4).putInt(position).array());
            tr.absorb("before", ShuffleCascade.encodeDeck(before));
            tr.absorb("after", ShuffleCascade.encodeDeck(after));
            tr.absorb("pocket key", pocketKey);
            if (communityKey != null) tr.absorb("community key", communityKey);
            return tr;
        }
    }
}

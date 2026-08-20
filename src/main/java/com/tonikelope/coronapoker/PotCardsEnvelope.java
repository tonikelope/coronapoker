/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict parser for the single supported atomic POTCARDS wire. */
public final class PotCardsEnvelope {

    public static final int ENTRY_FIELDS = 5;

    public static final class Entry {
        private final String nick;
        private final int firstCard;
        private final int secondCard;
        private final byte[] pocketKey;
        private final byte[] signature;

        private Entry(String nick, int firstCard, int secondCard,
                byte[] pocketKey, byte[] signature) {
            this.nick = nick;
            this.firstCard = firstCard;
            this.secondCard = secondCard;
            this.pocketKey = pocketKey.clone();
            this.signature = signature.clone();
        }

        public String nick() { return nick; }
        public int firstCard() { return firstCard; }
        public int secondCard() { return secondCard; }
        public byte[] pocketKey() { return pocketKey.clone(); }
        public byte[] signature() { return signature.clone(); }
    }

    private final List<Entry> entries;

    private PotCardsEnvelope(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public List<Entry> entries() {
        return entries;
    }

    public static PotCardsEnvelope parse(String[] wire, Set<String> eligibleNicks) {
        if (wire == null || wire.length < 3 + ENTRY_FIELDS
                || !"GAME".equals(wire[0]) || !"POTCARDS".equals(wire[2])
                || (wire.length - 3) % ENTRY_FIELDS != 0) {
            throw new IllegalArgumentException("invalid POTCARDS envelope arity");
        }
        if (eligibleNicks == null || eligibleNicks.isEmpty()) {
            throw new IllegalArgumentException("eligible showdown roster is required");
        }
        int total = (wire.length - 3) / ENTRY_FIELDS;
        if (total != eligibleNicks.size()) {
            throw new IllegalArgumentException("POTCARDS must cover the exact showdown roster");
        }

        Set<String> seenNicks = new HashSet<>();
        Set<Integer> seenCards = new HashSet<>();
        List<Entry> parsed = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            int base = 3 + ENTRY_FIELDS * i;
            String nick = decodeStrictUtf8(Base64.getDecoder().decode(wire[base]));
            if (nick.isEmpty() || !nick.equals(Normalizer.normalize(nick, Normalizer.Form.NFC))
                    || !eligibleNicks.contains(nick) || !seenNicks.add(nick)) {
                throw new IllegalArgumentException("invalid or duplicate POTCARDS nick");
            }
            int first = cardIndex(wire[base + 1]);
            int second = cardIndex(wire[base + 2]);
            if (first == second || !seenCards.add(first) || !seenCards.add(second)) {
                throw new IllegalArgumentException("duplicate POTCARDS card");
            }
            byte[] key = Base64.getDecoder().decode(wire[base + 3]);
            byte[] sig = Base64.getDecoder().decode(wire[base + 4]);
            if (key.length != 32 || sig.length != 64) {
                throw new IllegalArgumentException("invalid POTCARDS key or signature length");
            }
            parsed.add(new Entry(nick, first, second, key, sig));
        }
        if (!seenNicks.equals(eligibleNicks)) {
            throw new IllegalArgumentException("POTCARDS showdown roster mismatch");
        }
        return new PotCardsEnvelope(parsed);
    }

    private static int cardIndex(String shortString) {
        if (shortString == null || shortString.isEmpty()) {
            throw new IllegalArgumentException("missing POTCARDS card");
        }
        for (int i = 0; i < 52; i++) {
            if (shortString.equals(Card.shortStringFromIndex(i))) {
                return i;
            }
        }
        throw new IllegalArgumentException("non-canonical POTCARDS card");
    }

    private static String decodeStrictUtf8(byte[] encoded) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded)).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("POTCARDS nick is not UTF-8", ex);
        }
    }
}

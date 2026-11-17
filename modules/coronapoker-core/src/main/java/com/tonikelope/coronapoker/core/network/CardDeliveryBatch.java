/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.CanonicalActionRecord;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** One authenticated, ordered delivery of single-locked residues for one phase. */
public final class CardDeliveryBatch {

    public static final String COMMAND = "CARD_DELIVERY_V1";
    private static final int MAX_NICK_BYTES = 256;
    private static final int MAX_BODY_CHARS = 5_120;

    public enum Phase {
        POCKET_CARDS(64),
        FLOP_PIECE(96), TURN_PIECE(32), RIVER_PIECE(32),
        RIT2_FLOP_PIECE(96), RIT2_TURN_PIECE(32), RIT2_RIVER_PIECE(32);

        private final int residueBytes;

        Phase(int residueBytes) {
            this.residueBytes = residueBytes;
        }
    }

    private final byte[] handId;
    private final Phase phase;
    private final Map<String, byte[]> residues;

    private CardDeliveryBatch(byte[] handId, Phase phase, Map<String, byte[]> residues) {
        this.handId = handId;
        this.phase = phase;
        this.residues = Collections.unmodifiableMap(residues);
    }

    public static String encode(byte[] handId, Phase phase, Map<String, byte[]> residues) {
        if (handId == null || handId.length != CanonicalActionRecord.HAND_ID_BYTES
                || residues.isEmpty() || residues.size() > LobbySnapshot.MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("Invalid card delivery header");
        }
        Base64.Encoder base64 = Base64.getEncoder();
        StringBuilder body = new StringBuilder(COMMAND).append('#')
                .append(base64.encodeToString(handId)).append('#').append(phase.name());
        residues.forEach((nick, residue) -> {
            byte[] encodedNick = nick.getBytes(StandardCharsets.UTF_8);
            if (encodedNick.length == 0 || encodedNick.length > MAX_NICK_BYTES
                    || !Normalizer.isNormalized(nick, Normalizer.Form.NFC)
                    || residue.length != phase.residueBytes) {
                throw new IllegalArgumentException("Invalid card delivery entry");
            }
            body.append('#').append(base64.encodeToString(encodedNick))
                    .append('#').append(base64.encodeToString(residue));
        });
        return body.toString();
    }

    public static CardDeliveryBatch decode(String body) {
        if (body == null || body.length() > MAX_BODY_CHARS) {
            throw new IllegalArgumentException("Card delivery exceeds wire limit");
        }
        String[] fields = body.split("#", -1);
        if (fields.length < 5 || fields.length > 3 + 2 * LobbySnapshot.MAX_PARTICIPANTS
                || fields.length % 2 == 0 || !COMMAND.equals(fields[0])) {
            throw new IllegalArgumentException("Invalid card delivery framing");
        }
        Base64.Decoder base64 = Base64.getDecoder();
        byte[] handId = base64.decode(fields[1]);
        if (handId.length != CanonicalActionRecord.HAND_ID_BYTES) {
            throw new IllegalArgumentException("Invalid card delivery hand ID");
        }
        Phase phase;
        try {
            phase = Phase.valueOf(fields[2]);
        } catch (IllegalArgumentException invalidPhase) {
            throw new IllegalArgumentException("Invalid card delivery phase");
        }
        Map<String, byte[]> residues = new LinkedHashMap<>();
        for (int i = 3; i < fields.length; i += 2) {
            String nick = decodeNick(base64.decode(fields[i]));
            byte[] residue = base64.decode(fields[i + 1]);
            if (residue.length != phase.residueBytes || residues.putIfAbsent(nick, residue) != null) {
                throw new IllegalArgumentException("Invalid or repeated card delivery entry");
            }
        }
        return new CardDeliveryBatch(handId, phase, residues);
    }

    private static String decodeNick(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_NICK_BYTES) {
            throw new IllegalArgumentException("Invalid card delivery nickname length");
        }
        try {
            String nick = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            if (!Normalizer.isNormalized(nick, Normalizer.Form.NFC)) {
                throw new IllegalArgumentException("Invalid card delivery nickname normalization");
            }
            return nick;
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("Invalid card delivery nickname encoding", ex);
        }
    }

    public Map<String, byte[]> requireDelivery(byte[] currentHandId, Phase expectedPhase,
            Set<String> expectedRecipients) {
        if (!Arrays.equals(handId, currentHandId) || phase != expectedPhase
                || !residues.keySet().equals(expectedRecipients)) {
            throw new IllegalArgumentException("Card delivery does not match hand, phase or recipients");
        }
        return residues;
    }

    public Phase phase() {
        return phase;
    }
}

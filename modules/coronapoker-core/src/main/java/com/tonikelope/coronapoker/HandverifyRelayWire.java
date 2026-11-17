/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.LobbySnapshot;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Bounded host relay of independently signed receipts that are already available. */
public final class HandverifyRelayWire {
    public static final String COMMAND = "HANDVERIFY_RELAY_V1";
    private static final int MAX_BODY_CHARS = 5_120;

    private HandverifyRelayWire() { }

    /** Preserve the original relay audience: a signer does not receive their own receipt. */
    static String encodeFor(String recipient, List<HandverifyReceiptEnvelope> ready) {
        if (ready.isEmpty() || ready.size() > LobbySnapshot.MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("Invalid receipt relay size");
        }
        Base64.Encoder base64 = Base64.getEncoder();
        StringBuilder body = new StringBuilder(COMMAND);
        for (HandverifyReceiptEnvelope envelope : ready) {
            if (!envelope.nick().equals(recipient)) {
                body.append('#').append(base64.encodeToString(envelope.nick().getBytes(StandardCharsets.UTF_8)))
                        .append('#').append(base64.encodeToString(envelope.receipt()));
            }
        }
        return body.length() == COMMAND.length() ? null : body.toString();
    }

    public static List<HandverifyReceiptEnvelope> decode(String body) {
        if (body == null || body.length() > MAX_BODY_CHARS) {
            throw new IllegalArgumentException("Receipt relay exceeds wire limit");
        }
        String[] fields = body.split("#", -1);
        if (fields.length < 3 || fields.length > 1 + 2 * LobbySnapshot.MAX_PARTICIPANTS
                || fields.length % 2 == 0 || !COMMAND.equals(fields[0])) {
            throw new IllegalArgumentException("Invalid receipt relay framing");
        }
        List<HandverifyReceiptEnvelope> envelopes = new ArrayList<>();
        Set<String> signers = new HashSet<>();
        for (int i = 1; i < fields.length; i += 2) {
            HandverifyReceiptEnvelope envelope = HandverifyReceiptEnvelope.parse(
                    new String[]{"GAME", "0", "HANDVERIFY", fields[i], fields[i + 1]});
            if (!signers.add(envelope.nick())) {
                throw new IllegalArgumentException("Duplicate signer in receipt relay");
            }
            envelopes.add(envelope);
        }
        return envelopes;
    }

    static Map<String, byte[]> validate(String body, byte[] handId, Set<String> expected,
            Set<String> alreadyReceived, Predicate<HandverifyReceiptEnvelope> verifySignature) {
        Map<String, byte[]> staged = new LinkedHashMap<>();
        for (HandverifyReceiptEnvelope envelope : decode(body)) {
            if (!expected.contains(envelope.nick()) || alreadyReceived.contains(envelope.nick())
                    || !Arrays.equals(handId, envelope.handId()) || !verifySignature.test(envelope)) {
                throw new IllegalArgumentException("Unexpected or invalid receipt in settlement relay");
            }
            staged.put(envelope.nick(), envelope.receipt());
        }
        return staged;
    }
}

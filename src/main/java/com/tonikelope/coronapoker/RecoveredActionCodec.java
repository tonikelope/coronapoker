package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Total, side-effect-free codec for untrusted recovery action rows. */
public final class RecoveredActionCodec {

    private static final String VERSION = "V1";
    private static final int MAX_WIRE_CHARS = 512;
    private static final int MAX_ACTOR_BYTES = 128;
    private static final int MAX_NUMBER_CHARS = 20;
    private static final long MAX_MONEY_CENTS = 9_000_000_000_000_000L;

    public enum Error {
        NULL_OR_OVERSIZED,
        FIELD_COUNT,
        BAD_ACTOR,
        BAD_DECISION,
        BAD_AMOUNT,
        BAD_EVIDENCE
    }

    public static final class Wire {
        private final String actor;
        private final int decision;
        private final long amountCents;
        private final byte[] record;
        private final byte[] signature;

        private Wire(String actor, int decision, long amountCents,
                byte[] record, byte[] signature) {
            this.actor = actor;
            this.decision = decision;
            this.amountCents = amountCents;
            this.record = record == null ? null : record.clone();
            this.signature = signature == null ? null : signature.clone();
        }

        public String actor() {
            return actor;
        }

        public int decision() {
            return decision;
        }

        public long amountCents() {
            return amountCents;
        }

        public double amount() {
            return amountCents / 100d;
        }

        public byte[] record() {
            return record == null ? null : record.clone();
        }

        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }

    }

    public static final class Result {
        private final Wire value;
        private final Error error;

        private Result(Wire value, Error error) {
            this.value = value;
            this.error = error;
        }

        public boolean isOk() {
            return value != null;
        }

        public Wire value() {
            return value;
        }

        public Error error() {
            return error;
        }
    }

    private RecoveredActionCodec() {
    }

    public static Result decode(String encoded) {
        try {
            if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_WIRE_CHARS) {
                return error(Error.NULL_OR_OVERSIZED);
            }
            String[] fields = encoded.split("#", -1);
            if (fields.length != 6 || !VERSION.equals(fields[0])) {
                return error(Error.FIELD_COUNT);
            }
            final int actorIndex = 1;
            final int decisionIndex = 2;
            final int amountIndex = 3;
            final int recordIndex = 4;

            String actor = decodeActor(fields[actorIndex]);
            if (actor == null) {
                return error(Error.BAD_ACTOR);
            }
            Integer decision = parseDecision(fields[decisionIndex]);
            if (decision == null) {
                return error(Error.BAD_DECISION);
            }
            Long parsedCents = parseIntegerCents(fields[amountIndex]);
            if (parsedCents == null) {
                return error(Error.BAD_AMOUNT);
            }
            long amountCents = decision == Player.BET ? parsedCents : 0L;

            byte[] record = null;
            byte[] signature = null;
            if (recordIndex >= 0) {
                String recordField = fields[recordIndex];
                String signatureField = fields[recordIndex + 1];
                boolean bothMissing = "*".equals(recordField) && "*".equals(signatureField);
                if (!bothMissing) {
                    if ("*".equals(recordField) || "*".equals(signatureField)) {
                        return error(Error.BAD_EVIDENCE);
                    }
                    record = Base64.getDecoder().decode(recordField);
                    signature = Base64.getDecoder().decode(signatureField);
                    if (record.length != CanonicalActionRecord.RECORD_BYTES
                            || signature.length != HandStateChain.SIG_BYTES) {
                        return error(Error.BAD_EVIDENCE);
                    }
                }
            }
            return new Result(new Wire(actor, decision, amountCents,
                    record, signature), null);
        } catch (RuntimeException ex) {
            return error(Error.BAD_EVIDENCE);
        }
    }

    public static String encodeV1(String actor, int decision, double amount,
            String recordB64, String signatureB64) {
        long cents = CanonicalActionRecord.amountToCents(amount);
        String actorB64 = Base64.getEncoder().encodeToString(
                actor.getBytes(StandardCharsets.UTF_8));
        String record = recordB64 == null || recordB64.isEmpty() ? "*" : recordB64;
        String signature = signatureB64 == null || signatureB64.isEmpty() ? "*" : signatureB64;
        String encoded = VERSION + "#" + actorB64 + "#" + decision + "#" + cents
                + "#" + record + "#" + signature;
        Result validation = decode(encoded);
        if (!validation.isOk()) {
            throw new IllegalArgumentException("Cannot encode recovered action: " + validation.error());
        }
        return encoded;
    }

    private static Result error(Error error) {
        return new Result(null, error);
    }

    private static String decodeActor(String encoded) {
        if (encoded.isEmpty() || encoded.length() > 4 * ((MAX_ACTOR_BYTES + 2) / 3)) {
            return null;
        }
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length == 0 || bytes.length > MAX_ACTOR_BYTES) {
            return null;
        }
        try {
            String actor = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            for (int i = 0; i < actor.length(); i++) {
                if (Character.isISOControl(actor.charAt(i))) {
                    return null;
                }
            }
            return actor;
        } catch (CharacterCodingException ex) {
            return null;
        }
    }

    private static Integer parseDecision(String value) {
        if (value.length() == 0 || value.length() > 2 || !value.matches("[0-9]+")) {
            return null;
        }
        int parsed = Integer.parseInt(value);
        return parsed == Player.FOLD || parsed == Player.CHECK
                || parsed == Player.BET || parsed == Player.ALLIN ? parsed : null;
    }

    private static Long parseIntegerCents(String value) {
        if (value.length() == 0 || value.length() > MAX_NUMBER_CHARS || !value.matches("[0-9]+")) {
            return null;
        }
        try {
            return checkedCents(Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long checkedCents(long cents) {
        return cents >= 0L && cents <= MAX_MONEY_CENTS ? cents : null;
    }
}

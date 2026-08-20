package com.tonikelope.coronapoker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A single-version, deterministic and fully validated recovery snapshot. */
public final class RecoverySnapshotV1 {
    private static final int MAGIC = 0x43505253; // CPRS
    private static final int VERSION = 1;
    private static final int MAX_WIRE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_TEXT_BYTES = 1024 * 1024;
    private static final int MAX_SHORT_TEXT_BYTES = 4096;
    private static final int MAX_PLAYERS = 64;
    private static final long MAX_MONEY_CENTS = 100_000_000_000_000L;
    private static final Set<String> REQUIRED_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "start", "hand_id", "hand_end", "server", "preflop_players", "hand_id_b64",
            "buyin", "rebuy", "play_time", "conta_mano", "sbval", "bbval",
            "blinds_time", "blinds_time_type", "blinds_double", "dealer", "sb", "bb")));

    public enum Error {
        NULL_OR_OVERSIZED, BAD_MAGIC_OR_VERSION, TRUNCATED_OR_TRAILING,
        WRONG_SESSION, WRONG_SCHEMA, BAD_VALUE, BAD_ROSTER, BAD_MONEY
    }

    public static final class Result {
        private final RecoverySnapshotV1 value;
        private final Error error;

        private Result(RecoverySnapshotV1 value, Error error) {
            this.value = value;
            this.error = error;
        }

        public boolean isOk() {
            return value != null;
        }

        public RecoverySnapshotV1 value() {
            return value;
        }

        public Error error() {
            return error;
        }
    }

    private final String sessionId;
    private final Map<String, Object> values;

    private RecoverySnapshotV1(String sessionId, Map<String, Object> values) {
        this.sessionId = sessionId;
        this.values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    public static Result fromMap(Map<String, Object> input, String sessionId) {
        try {
            if (!validShortText(sessionId) || input == null) {
                return error(Error.WRONG_SCHEMA);
            }
            Set<String> keys = new HashSet<>(input.keySet());
            boolean hasBalance = keys.remove("balance");
            if (!keys.equals(REQUIRED_KEYS)) {
                return error(Error.WRONG_SCHEMA);
            }
            if (!exact(input, "start", Long.class) || !exact(input, "hand_id", Integer.class)
                    || !exact(input, "hand_end", Long.class) || !exact(input, "server", String.class)
                    || !exact(input, "preflop_players", String.class) || !exact(input, "hand_id_b64", String.class)
                    || !exact(input, "buyin", Integer.class) || !exact(input, "rebuy", Boolean.class)
                    || !exact(input, "play_time", Long.class) || !exact(input, "conta_mano", Integer.class)
                    || !exact(input, "sbval", Double.class) || !exact(input, "bbval", Double.class)
                    || !exact(input, "blinds_time", Integer.class) || !exact(input, "blinds_time_type", Integer.class)
                    || !exact(input, "blinds_double", Integer.class) || !exact(input, "dealer", String.class)
                    || !exact(input, "sb", String.class) || !exact(input, "bb", String.class)
                    || (hasBalance && !exact(input, "balance", String.class))) {
                return error(Error.WRONG_SCHEMA);
            }

            long start = (Long) input.get("start");
            int handId = (Integer) input.get("hand_id");
            long handEnd = (Long) input.get("hand_end");
            long playTime = (Long) input.get("play_time");
            int buyin = (Integer) input.get("buyin");
            int handCounter = (Integer) input.get("conta_mano");
            int blindsTime = (Integer) input.get("blinds_time");
            int blindsTimeType = (Integer) input.get("blinds_time_type");
            int blindsDouble = (Integer) input.get("blinds_double");
            if (start <= 0 || handId <= 0 || handEnd < 0 || playTime < 0 || buyin <= 0
                    || handCounter <= 0 || blindsTime < 0 || blindsTimeType < 0 || blindsDouble < 0) {
                return error(Error.BAD_VALUE);
            }
            String server = (String) input.get("server");
            String dealer = (String) input.get("dealer");
            String sb = (String) input.get("sb");
            String bb = (String) input.get("bb");
            if (!validShortText(server) || !validShortText(dealer) || !validShortText(sb)
                    || !validShortText(bb) || bb.equals(sb) || bb.equals(dealer)) {
                return error(Error.BAD_VALUE);
            }
            Set<String> roster = decodeRoster((String) input.get("preflop_players"));
            if (roster == null || !roster.contains(dealer)
                    || !roster.contains(sb) || !roster.contains(bb)) {
                return error(Error.BAD_ROSTER);
            }
            byte[] cryptoHandId;
            try {
                cryptoHandId = Base64.getDecoder().decode((String) input.get("hand_id_b64"));
            } catch (IllegalArgumentException ex) {
                return error(Error.BAD_VALUE);
            }
            if (cryptoHandId.length != CanonicalActionRecord.HAND_ID_BYTES) {
                return error(Error.BAD_VALUE);
            }
            Long sbCents = cents((Double) input.get("sbval"));
            Long bbCents = cents((Double) input.get("bbval"));
            if (sbCents == null || bbCents == null || sbCents <= 0 || bbCents < sbCents) {
                return error(Error.BAD_MONEY);
            }
            if (hasBalance && !validateBalance((String) input.get("balance"))) {
                return error(Error.BAD_MONEY);
            }
            return ok(new RecoverySnapshotV1(sessionId, input));
        } catch (RuntimeException ex) {
            return error(Error.WRONG_SCHEMA);
        }
    }

    public static Result decode(byte[] encoded, String expectedSessionId) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_WIRE_BYTES) {
            return error(Error.NULL_OR_OVERSIZED);
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                return error(Error.BAD_MAGIC_OR_VERSION);
            }
            String session = readString(in, MAX_SHORT_TEXT_BYTES);
            if (!session.equals(expectedSessionId)) {
                return error(Error.WRONG_SESSION);
            }
            HashMap<String, Object> map = new HashMap<>();
            map.put("start", in.readLong());
            map.put("hand_id", in.readInt());
            map.put("hand_end", in.readLong());
            map.put("server", readString(in, MAX_SHORT_TEXT_BYTES));
            map.put("preflop_players", readString(in, MAX_TEXT_BYTES));
            map.put("hand_id_b64", readString(in, MAX_SHORT_TEXT_BYTES));
            map.put("buyin", in.readInt());
            map.put("rebuy", in.readBoolean());
            map.put("play_time", in.readLong());
            map.put("conta_mano", in.readInt());
            map.put("sbval", Double.longBitsToDouble(in.readLong()));
            map.put("bbval", Double.longBitsToDouble(in.readLong()));
            map.put("blinds_time", in.readInt());
            map.put("blinds_time_type", in.readInt());
            map.put("blinds_double", in.readInt());
            map.put("dealer", readString(in, MAX_SHORT_TEXT_BYTES));
            map.put("sb", readString(in, MAX_SHORT_TEXT_BYTES));
            map.put("bb", readString(in, MAX_SHORT_TEXT_BYTES));
            if (in.readBoolean()) {
                map.put("balance", readString(in, MAX_TEXT_BYTES));
            }
            if (in.read() != -1) {
                return error(Error.TRUNCATED_OR_TRAILING);
            }
            return fromMap(map, session);
        } catch (EOFException ex) {
            return error(Error.TRUNCATED_OR_TRAILING);
        } catch (IOException | RuntimeException ex) {
            return error(Error.BAD_VALUE);
        }
    }

    public static Result decodeAndApply(byte[] encoded, String expectedSessionId, Map<String, Object> target) {
        Result parsed = decode(encoded, expectedSessionId);
        if (!parsed.isOk() || target == null) {
            return parsed.isOk() ? error(Error.WRONG_SCHEMA) : parsed;
        }
        synchronized (target) {
            target.clear();
            target.putAll(parsed.value.values);
        }
        return parsed;
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                writeString(out, sessionId);
                out.writeLong((Long) values.get("start"));
                out.writeInt((Integer) values.get("hand_id"));
                out.writeLong((Long) values.get("hand_end"));
                writeString(out, (String) values.get("server"));
                writeString(out, (String) values.get("preflop_players"));
                writeString(out, (String) values.get("hand_id_b64"));
                out.writeInt((Integer) values.get("buyin"));
                out.writeBoolean((Boolean) values.get("rebuy"));
                out.writeLong((Long) values.get("play_time"));
                out.writeInt((Integer) values.get("conta_mano"));
                out.writeLong(Double.doubleToLongBits((Double) values.get("sbval")));
                out.writeLong(Double.doubleToLongBits((Double) values.get("bbval")));
                out.writeInt((Integer) values.get("blinds_time"));
                out.writeInt((Integer) values.get("blinds_time_type"));
                out.writeInt((Integer) values.get("blinds_double"));
                writeString(out, (String) values.get("dealer"));
                writeString(out, (String) values.get("sb"));
                writeString(out, (String) values.get("bb"));
                out.writeBoolean(values.containsKey("balance"));
                if (values.containsKey("balance")) {
                    writeString(out, (String) values.get("balance"));
                }
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_WIRE_BYTES) {
                throw new IllegalStateException("recovery snapshot too large");
            }
            return encoded;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public HashMap<String, Object> toMap() {
        return new HashMap<>(values);
    }

    private static boolean exact(Map<String, Object> map, String key, Class<?> type) {
        Object value = map.get(key);
        return value != null && value.getClass() == type;
    }

    private static Set<String> decodeRoster(String encodedRoster) {
        if (encodedRoster == null || encodedRoster.isEmpty()
                || encodedRoster.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            return null;
        }
        String[] tokens = encodedRoster.split("#", -1);
        if (tokens.length == 0 || tokens.length > MAX_PLAYERS) {
            return null;
        }
        Set<String> roster = new HashSet<>();
        for (String token : tokens) {
            if (token.isEmpty()) {
                return null;
            }
            String player = decodeUtf8Base64(token);
            if (!validShortText(player) || !roster.add(player)) {
                return null;
            }
        }
        return roster;
    }

    private static boolean validateBalance(String balance) {
        if (balance == null || balance.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            return false;
        }
        if (balance.isEmpty()) {
            return true;
        }
        String[] entries = balance.split("@", -1);
        if (entries.length > MAX_PLAYERS) {
            return false;
        }
        Set<String> players = new HashSet<>();
        for (String entry : entries) {
            String[] fields = entry.split("\\|", -1);
            if (fields.length != 4) {
                return false;
            }
            String player = decodeUtf8Base64(fields[0]);
            if (!validShortText(player) || !players.add(player)
                    || parseDecimalCents(fields[1]) == null
                    || parseNonNegativeInt(fields[2]) == null
                    || parseNonNegativeInt(fields[3]) == null) {
                return false;
            }
        }
        return true;
    }

    private static Long parseDecimalCents(String value) {
        if (value == null || value.length() == 0 || value.length() > 32
                || !value.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            return null;
        }
        try {
            long result = new BigDecimal(value).movePointRight(2).longValueExact();
            return result <= MAX_MONEY_CENTS ? result : null;
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static Integer parseNonNegativeInt(String value) {
        if (value == null || !value.matches("[0-9]{1,10}")) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long cents(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return null;
        }
        try {
            long result = BigDecimal.valueOf(value).movePointRight(2).longValueExact();
            return result <= MAX_MONEY_CENTS ? result : null;
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static String decodeUtf8Base64(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (IllegalArgumentException | CharacterCodingException ex) {
            return null;
        }
    }

    private static boolean validShortText(String value) {
        return value != null && !value.isEmpty()
                && value.getBytes(StandardCharsets.UTF_8).length <= MAX_SHORT_TEXT_BYTES;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private static String readString(DataInputStream in, int maxBytes) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > maxBytes || length > in.available()) {
            throw new IOException("invalid string length");
        }
        byte[] encoded = new byte[length];
        in.readFully(encoded);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded)).toString();
        } catch (CharacterCodingException ex) {
            throw new IOException("invalid UTF-8", ex);
        }
    }

    private static Result ok(RecoverySnapshotV1 value) {
        return new Result(value, null);
    }

    private static Result error(Error error) {
        return new Result(null, error);
    }
}

/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Parses a recovery balance once and reconciles protected rows before apply. */
public final class RecoveryBalanceReconciler {

    private static final int MAX_PLAYERS = 64;
    private static final int MAX_NICK_BYTES = 256;

    public enum Error {
        NONE,
        BAD_FORMAT,
        BAD_VALUE,
        DUPLICATE_PLAYER,
        MISSING_HOST_PLAYER,
        MISSING_LOCAL_PLAYER,
        BALANCE_MISMATCH
    }

    public static final class Balance {
        private final MoneyCents stack;
        private final int buyin;
        private final BuyinCount rebuyCount;

        private Balance(MoneyCents stack, int buyin, BuyinCount rebuyCount) {
            this.stack = stack;
            this.buyin = buyin;
            this.rebuyCount = rebuyCount;
        }

        public MoneyCents stack() { return stack; }
        public int buyin() { return buyin; }
        public BuyinCount rebuyCount() { return rebuyCount; }

        private boolean sameValue(Balance other) {
            return other != null && stack.equals(other.stack)
                    && buyin == other.buyin
                    && rebuyCount.value() == other.rebuyCount.value();
        }
    }

    public static final class Result {
        private final Error error;
        private final Map<String, Balance> balances;

        private Result(Error error, Map<String, Balance> balances) {
            this.error = error;
            this.balances = balances;
        }

        public boolean isOk() { return error == Error.NONE; }
        public Error error() { return error; }
        public Map<String, Balance> balances() { return balances; }
    }

    private RecoveryBalanceReconciler() {
    }

    public static Result reconcile(String hostWire, Map<String, double[]> localRows,
            Set<String> protectedPlayers) {
        return reconcile(hostWire, localRows, protectedPlayers, false);
    }

    public static Result reconcileExact(String hostWire, Map<String, double[]> localRows) {
        return reconcile(hostWire, localRows,
                localRows != null ? localRows.keySet() : null, true);
    }

    public static Result parseObserver(String hostWire, Set<String> requiredPlayers) {
        Result host = parseHost(hostWire);
        if (!host.isOk()) {
            return host;
        }
        if (requiredPlayers == null) {
            return failed(Error.BAD_VALUE);
        }
        for (String player : requiredPlayers) {
            if (!host.balances.containsKey(player)) {
                return failed(Error.MISSING_HOST_PLAYER);
            }
        }
        return host;
    }

    public static Set<String> decodeRoster(String encodedRoster) {
        if (encodedRoster == null || encodedRoster.isEmpty()
                || encodedRoster.getBytes(StandardCharsets.UTF_8).length > 16_384) {
            throw new IllegalArgumentException("missing recovery roster");
        }
        String[] tokens = encodedRoster.split("#", -1);
        if (tokens.length == 0 || tokens.length > MAX_PLAYERS) {
            throw new IllegalArgumentException("invalid recovery roster size");
        }
        java.util.LinkedHashSet<String> roster = new java.util.LinkedHashSet<>();
        for (String token : tokens) {
            String nick = decodeNick(token);
            if (!roster.add(nick)) {
                throw new IllegalArgumentException("duplicate recovery roster player");
            }
        }
        return Collections.unmodifiableSet(roster);
    }

    public static boolean sameOpenHand(String localHandIdB64, Set<String> localRoster,
            long hostHandEnd, String hostHandIdB64, Set<String> hostRoster) {
        return hostHandEnd == 0L && localHandIdB64 != null
                && localHandIdB64.equals(hostHandIdB64)
                && localRoster != null && localRoster.equals(hostRoster);
    }

    public static boolean passiveObserverContextIsSafe(long hostHandEnd,
            Set<String> hostRoster, String localNick) {
        return hostRoster != null && localNick != null
                && (hostHandEnd != 0L || !hostRoster.contains(localNick));
    }

    private static Result reconcile(String hostWire, Map<String, double[]> localRows,
            Set<String> protectedPlayers, boolean exactRoster) {
        Result host = parseHost(hostWire);
        if (!host.isOk()) {
            return host;
        }
        if (protectedPlayers == null) {
            return failed(Error.BAD_VALUE);
        }
        if (protectedPlayers.isEmpty() && !exactRoster) {
            return host;
        }
        Result local = parseLocal(localRows);
        if (!local.isOk()) {
            return local;
        }
        if (exactRoster && !host.balances.keySet().equals(local.balances.keySet())) {
            return failed(host.balances.keySet().containsAll(local.balances.keySet())
                    ? Error.MISSING_LOCAL_PLAYER : Error.MISSING_HOST_PLAYER);
        }
        if (protectedPlayers.isEmpty()) {
            return host;
        }
        for (String player : protectedPlayers) {
            Balance hostBalance = host.balances.get(player);
            if (hostBalance == null) {
                return failed(Error.MISSING_HOST_PLAYER);
            }
            Balance localBalance = local.balances.get(player);
            if (localBalance == null) {
                return failed(Error.MISSING_LOCAL_PLAYER);
            }
            if (!hostBalance.sameValue(localBalance)) {
                return failed(Error.BALANCE_MISMATCH);
            }
        }
        return host;
    }

    private static Result parseHost(String wire) {
        if (wire == null || wire.getBytes(StandardCharsets.UTF_8).length > 16_384) {
            return failed(Error.BAD_FORMAT);
        }
        Map<String, Balance> parsed = new LinkedHashMap<>();
        if (wire.isEmpty()) {
            return ok(parsed);
        }
        String[] entries = wire.split("@", -1);
        if (entries.length > MAX_PLAYERS) {
            return failed(Error.BAD_FORMAT);
        }
        try {
            for (String entry : entries) {
                String[] fields = entry.split("\\|", -1);
                if (fields.length != 4) {
                    return failed(Error.BAD_FORMAT);
                }
                String nick = decodeNick(fields[0]);
                if (parsed.containsKey(nick)) {
                    return failed(Error.DUPLICATE_PLAYER);
                }
                if (!fields[1].matches("[0-9]+(?:\\.[0-9]{1,2})?")
                        || fields[1].length() > 32) {
                    return failed(Error.BAD_VALUE);
                }
                MoneyCents stack = MoneyCents.parse(fields[1]);
                int buyin = parseNonNegativeInt(fields[2], Integer.MAX_VALUE);
                BuyinCount rebuy = BuyinCount.of(
                        parseNonNegativeInt(fields[3], BuyinCount.MAX_VALUE));
                parsed.put(nick, new Balance(stack, buyin, rebuy));
            }
            return ok(parsed);
        } catch (IllegalArgumentException ex) {
            return failed(Error.BAD_VALUE);
        }
    }

    private static Result parseLocal(Map<String, double[]> rows) {
        if (rows == null || rows.size() > MAX_PLAYERS) {
            return failed(Error.MISSING_LOCAL_PLAYER);
        }
        Map<String, Balance> parsed = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, double[]> row : rows.entrySet()) {
                String nick = canonicalNick(row.getKey());
                double[] values = row.getValue();
                if (values == null || values.length != 3
                        || !isExactNonNegativeInt(values[1], Integer.MAX_VALUE)
                        || !isExactNonNegativeInt(values[2], BuyinCount.MAX_VALUE)) {
                    return failed(Error.BAD_VALUE);
                }
                parsed.put(nick, new Balance(MoneyCents.fromDouble(values[0]),
                        (int) values[1], BuyinCount.of((int) values[2])));
            }
            return ok(parsed);
        } catch (IllegalArgumentException ex) {
            return failed(Error.BAD_VALUE);
        }
    }

    private static boolean isExactNonNegativeInt(double value, int max) {
        return Double.isFinite(value) && value >= 0d && value <= max
                && value == Math.rint(value);
    }

    private static int parseNonNegativeInt(String value, int max) {
        if (value == null || !value.matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException("invalid integer");
        }
        long parsed = Long.parseLong(value);
        if (parsed > max) {
            throw new IllegalArgumentException("integer outside domain");
        }
        return (int) parsed;
    }

    private static String decodeNick(String encoded) {
        try {
            return canonicalNick(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Base64.getDecoder().decode(encoded))).toString());
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("nick is not UTF-8", ex);
        }
    }

    private static String canonicalNick(String nick) {
        if (nick == null || nick.isEmpty()
                || nick.getBytes(StandardCharsets.UTF_8).length > MAX_NICK_BYTES
                || !nick.equals(Normalizer.normalize(nick, Normalizer.Form.NFC))) {
            throw new IllegalArgumentException("invalid nick");
        }
        return nick;
    }

    private static Result ok(Map<String, Balance> balances) {
        return new Result(Error.NONE,
                Collections.unmodifiableMap(new LinkedHashMap<>(balances)));
    }

    private static Result failed(Error error) {
        return new Result(error, Collections.emptyMap());
    }
}

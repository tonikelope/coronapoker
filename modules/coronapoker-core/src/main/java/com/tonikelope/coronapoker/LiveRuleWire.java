/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

/** Exact current formats for host-authoritative rule changes during a table. */
final class LiveRuleWire {

    private LiveRuleWire() {
    }

    static boolean parseBoolean(String[] parts, String expectedCommand) {
        requireExact(parts, expectedCommand);
        if ("0".equals(parts[3])) {
            return false;
        }
        if ("1".equals(parts[3])) {
            return true;
        }
        throw new IllegalArgumentException(expectedCommand + " requires 0 or 1");
    }

    static int parseRabbit(String[] parts) {
        int mode = parseCanonicalInt(parts, "RABBITRULE");
        if (mode < 0 || mode > 3) {
            throw new IllegalArgumentException("RABBITRULE mode out of range");
        }
        return mode;
    }

    static int parseMaxHands(String[] parts) {
        int hands = parseCanonicalInt(parts, "MAXHANDS");
        if (hands != -1 && hands < 1) {
            throw new IllegalArgumentException("MAXHANDS must be -1 or positive");
        }
        return hands;
    }

    private static int parseCanonicalInt(String[] parts, String expectedCommand) {
        requireExact(parts, expectedCommand);
        try {
            int value = Integer.parseInt(parts[3]);
            if (!Integer.toString(value).equals(parts[3])) {
                throw new IllegalArgumentException(expectedCommand + " integer is non-canonical");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(expectedCommand + " integer is invalid", ex);
        }
    }

    private static void requireExact(String[] parts, String expectedCommand) {
        if (parts == null || parts.length != 4 || !"GAME".equals(parts[0])
                || !expectedCommand.equals(parts[2])) {
            throw new IllegalArgumentException("invalid " + expectedCommand + " frame");
        }
    }
}

/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

/** Strict parser for the canonical one-board/two-board decision. */
public final class RitVoteCloseEnvelope {

    private RitVoteCloseEnvelope() {
    }

    public static Result parse(String[] fields) {
        if (fields == null || fields.length != 4
                || !"GAME".equals(fields[0])
                || !"RIT_VOTE_CLOSE".equals(fields[2])) {
            return Result.error("RIT_VOTE_CLOSE requires exactly four fields");
        }
        try {
            Integer.parseInt(fields[1]);
        } catch (NumberFormatException ex) {
            return Result.error("invalid GAME id");
        }
        if ("0".equals(fields[3])) {
            return Result.ok(false);
        }
        if ("1".equals(fields[3])) {
            return Result.ok(true);
        }
        return Result.error("RIT result must be 0 or 1");
    }

    public static final class Result {
        private final boolean ok;
        private final boolean agreed;
        private final String error;

        private Result(boolean ok, boolean agreed, String error) {
            this.ok = ok;
            this.agreed = agreed;
            this.error = error;
        }

        public boolean isOk() {
            return ok;
        }

        public boolean agreed() {
            if (!ok) {
                throw new IllegalStateException(error);
            }
            return agreed;
        }

        public String error() {
            return error;
        }

        private static Result ok(boolean agreed) {
            return new Result(true, agreed, null);
        }

        private static Result error(String error) {
            return new Result(false, false, error);
        }
    }
}

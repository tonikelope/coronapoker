/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

/** Atomic conversion of the current recovery SQL row into snapshot fields. */
final class RecoveryGameKeyDataReader {

    private RecoveryGameKeyDataReader() {
    }

    static HashMap<String, Object> read(ResultSet row) throws SQLException {
        if (row == null) {
            throw new IllegalArgumentException("recovery row is required");
        }
        HashMap<String, Object> built = new HashMap<>();
        built.put("start", row.getLong("start"));
        built.put("hand_id", row.getInt("hand_id"));
        long handEnd = row.getLong("hand_end");
        built.put("hand_end", handEnd);
        built.put("server", row.getString("server"));
        built.put("preflop_players", row.getString("preflop_players"));
        String handIdB64 = row.getString("hand_id_b64");
        if (handIdB64 != null) {
            built.put("hand_id_b64", handIdB64);
        } else if (handEnd != 0L) {
            // A hand aborted before its cryptographic context was initialized
            // is nevertheless a valid, already-closed recovery boundary. Keep
            // the field explicit on the one current wire schema; an open hand
            // with no id remains invalid and fails closed.
            built.put("hand_id_b64", "");
        }
        built.put("buyin", row.getInt("buyin"));
        built.put("rebuy", row.getBoolean("rebuy"));
        built.put("play_time", row.getLong("play_time"));
        built.put("conta_mano", row.getInt("conta_mano"));
        built.put("sbval", row.getDouble("sbval"));
        built.put("bbval", row.getDouble("bbval"));
        built.put("blinds_time", row.getInt("blinds_time"));
        built.put("blinds_time_type", row.getInt("blinds_time_type"));
        built.put("blinds_double", row.getInt("blinds_double"));
        built.put("dealer", row.getString("dealer"));
        built.put("sb", row.getString("sb"));
        built.put("bb", row.getString("bb"));
        return built;
    }
}

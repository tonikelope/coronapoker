package com.tonikelope.coronapoker;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;

final class RecoverySnapshotFixtures {
    private RecoverySnapshotFixtures() {
    }

    static HashMap<String, Object> validMap() {
        String alice = Base64.getEncoder().encodeToString("alice".getBytes(StandardCharsets.UTF_8));
        String bob = Base64.getEncoder().encodeToString("bob".getBytes(StandardCharsets.UTF_8));
        HashMap<String, Object> map = new HashMap<>();
        map.put("start", 1_700_000_000_000L);
        map.put("hand_id", 42);
        map.put("hand_end", 0L);
        map.put("server", "alice");
        map.put("preflop_players", alice + "#" + bob);
        map.put("hand_id_b64", Base64.getEncoder().encodeToString(new byte[CanonicalActionRecord.HAND_ID_BYTES]));
        map.put("buyin", 100);
        map.put("rebuy", true);
        map.put("play_time", 120L);
        map.put("conta_mano", 7);
        map.put("sbval", 0.50d);
        map.put("bbval", 1.00d);
        map.put("blinds_time", 10);
        map.put("blinds_time_type", 1);
        map.put("blinds_double", 0);
        map.put("dealer", "alice");
        map.put("sb", "alice");
        map.put("bb", "bob");
        map.put("balance", alice + "|99.50|100|0@" + bob + "|101.00|100|0");
        return map;
    }
}

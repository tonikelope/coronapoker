package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class GameCommandIdMonotonicTest {

    @Test
    public void currentProtocolIdsDoNotRandomlyCollide() {
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            ids.add(GameCommandId.next());
        }
        assertEquals(100_000, ids.size());
    }
}

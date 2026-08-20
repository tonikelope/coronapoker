package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CrossSessionSqlIsolationTest {
    @Test
    public void staleSqlCallbackCannotWriteIntoNewSession() {
        SessionGuard guard = new SessionGuard();
        SessionGuard.Generation old = guard.beginSession();
        guard.invalidate(old);
        SessionGuard.Generation current = guard.beginSession();
        List<String> rows = new ArrayList<>();

        guard.runIfCurrent(old, () -> rows.add("old-settlement"));
        guard.runIfCurrent(current, () -> rows.add("current-settlement"));

        assertEquals(List.of("current-settlement"), rows);
    }
}

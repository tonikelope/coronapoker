package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GdxHotJoinNotificationTest {

    @Test
    void announcesEachNewHumanOnceButNotBotsLocalOrReentries() {
        Set<String> known = new HashSet<>(List.of("host"));
        List<TableSnapshot.PlayerSnapshot> firstRoster = List.of(
                player("host", false), player("late", false),
                player("CustomBot$", false));

        assertEquals(List.of("late"),
                CoronaPokerGdxTable.recordNewHumanParticipants(
                        known, "host", firstRoster));
        assertEquals(List.of(),
                CoronaPokerGdxTable.recordNewHumanParticipants(
                        known, "host", firstRoster),
                "a repeated roster and a reconnect must not duplicate the toast");
        assertEquals(List.of(),
                CoronaPokerGdxTable.recordNewHumanParticipants(
                        known, "host", List.of(player("late", true))),
                "an exit/re-entry transition is not a new participant");
    }

    private static TableSnapshot.PlayerSnapshot player(String nickname,
            boolean exited) {
        return new TableSnapshot.PlayerSnapshot(nickname, 10d, 0d, 0d,
                !exited, false, exited, false, -1, -1, 0, 0L,
                false, TableSnapshot.Position.NONE, "", "", List.of());
    }
}

package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class LegalActionsUiParityTest {
    @Test
    public void sameLegalActionsGateUiAndModifiedRemoteRaise() {
        BettingRoundState state = BettingRoundFixtures.fourSeats();
        state = BettingRoundFixtures.apply(state, "A", BettingRoundState.Action.RAISE, 100L);
        state = BettingRoundFixtures.apply(state, "B", BettingRoundState.Action.CHECK_CALL, 100L);
        state = BettingRoundFixtures.apply(state, "C", BettingRoundState.Action.ALL_IN, 150L);

        boolean entitlement = state.legalActions("B").canRaise();
        assertFalse(entitlement);
        assertFalse(Crupier.isLegalRemoteAction(Player.BET, 250d,
                100d, 500d, 150d, 100d, 100d, entitlement));
        assertTrue(Crupier.isLegalRemoteAction(Player.CHECK, 0d,
                100d, 500d, 150d, 100d, 100d, entitlement));
        assertFalse(Crupier.isLegalRemoteAction(Player.ALLIN, 0d,
                100d, 500d, 150d, 100d, 100d, entitlement));
    }

    @Test
    public void uiLiveWireAndRecoveryConsumeDealerEntitlementGate() throws IOException {
        Path root = locateRoot();
        String localPlayer = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/LocalPlayer.java"));
        String crupier = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        assertTrue(localPlayer.contains("getCrupier().canPlayerRaise(nickname)"));
        assertTrue(crupier.contains("canPlayerRaise(jugador.getNickname())"));
        assertTrue(crupier.contains("canPlayerRaise(name)"));
        assertFalse(crupier.contains("partial_raise_cum"));
    }

    private static Path locateRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("tools/qa/pom.xml"))) {
                return path;
            }
        }
        throw new IllegalStateException("CoronaPoker root not found from " + start);
    }
}

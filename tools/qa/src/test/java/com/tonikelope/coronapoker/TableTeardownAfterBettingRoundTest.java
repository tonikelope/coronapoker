package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class TableTeardownAfterBettingRoundTest {

    @Test
    public void stoppingMidRoundCannotAdvanceToAnotherStreet() {
        assertFalse(Crupier.shouldAdvanceBettingStreet(true, false, 3,
                Crupier.PREFLOP, 3));
        assertFalse(Crupier.shouldAdvanceBettingStreet(false, true, 3,
                Crupier.PREFLOP, 3));
        assertTrue(Crupier.shouldAdvanceBettingStreet(false, false, 3,
                Crupier.PREFLOP, 3));
    }

    @Test
    public void bothMenuAndRecoverTeardownSkipSettlementAfterRoundCancellation() throws Exception {
        String source = crupierSource();
        Pattern immediateExit = Pattern.compile(
                "ArrayList<Player>\\s+resisten\\s*=\\s*this\\.rondaApuestas\\(PREFLOP,.*?;\\s*"
                + "(?:if\\s*\\(\\s*this\\.termination_pending.*?awaitCommittedTermination\\(\\);\\s*\\}\\s*)?"
                + "if\\s*\\(\\s*shouldAbortAfterBettingRound\\(isFin_de_la_transmision\\(\\),\\s*"
                + "this\\.termination_pending\\)\\s*\\)\\s*\\{\\s*continue;\\s*\\}",
                Pattern.DOTALL);

        assertTrue(immediateExit.matcher(source).find(),
                "Once table teardown starts, Crupier must leave the hand before UI/settlement work");
    }

    private static String crupierSource() throws Exception {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path source = current.resolve(
                    "src/main/java/com/tonikelope/coronapoker/Crupier.java");
            if (Files.isRegularFile(source)) {
                return Files.readString(source).replace("\r\n", "\n");
            }
            current = current.getParent();
        }
        throw new IllegalStateException("CoronaPoker project root not found");
    }
}

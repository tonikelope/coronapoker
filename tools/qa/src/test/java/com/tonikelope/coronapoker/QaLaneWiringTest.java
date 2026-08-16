package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Keeps the aggregate QA lanes from silently re-enabling statistical bots. */
class QaLaneWiringTest {

    private static final String[] BOT_SELECTORS = {
        "**/bot/harness/*Test.java",
        "**/bot/eval/MemoizedHandPotentialTest.java",
        "**/smoke/GameFlowSmoke.java"
    };

    @Test
    void aggregateLanesExcludeStatisticalBotSelectors() throws IOException {
        String pom = Files.readString(locateRoot().resolve("tools/qa/pom.xml"))
                .replace("\r\n", "\n");

        for (String profile : new String[]{"qa-heavy", "qa-release"}) {
            String profileBlock = profileBlock(pom, profile);
            for (String selector : BOT_SELECTORS) {
                assertTrue(profileBlock.contains("<exclude>" + selector + "</exclude>"),
                        profile + " must exclude " + selector);
            }
        }

        String botBlock = profileBlock(pom, "qa-bots");
        for (String selector : BOT_SELECTORS) {
            assertTrue(botBlock.contains("<include>" + selector + "</include>"),
                    "qa-bots must select " + selector);
        }
    }

    private static String profileBlock(String pom, String profile) {
        String startMarker = "<id>" + profile + "</id>";
        int start = pom.indexOf(startMarker);
        int end = pom.indexOf("\n        </profile>", start);
        assertTrue(start >= 0 && end > start, "profile not found: " + profile);
        return pom.substring(start, end);
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

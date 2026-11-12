package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * Keeps the aggregate QA lanes from silently re-enabling statistical bots.
 */
class QaLaneWiringTest {

    @Test
    void aggregateLanesExcludeBotCampaignsByTag() throws IOException {
        String pom = Files.readString(locateRoot().resolve("tools/qa/pom.xml"))
                .replace("\r\n", "\n");

        for (String profile : new String[]{"qa-heavy", "qa-all", "qa-headless-all"}) {
            String block = profileBlock(pom, profile);
            assertTrue(block.contains("<qa.excludedGroups>bot-campaign</qa.excludedGroups>"),
                    profile + " must exclude bot campaigns");
            assertFalse(block.contains("<exclude>**/bot/harness/*Test.java</exclude>"),
                    profile + " must preserve deterministic simulator tests");
        }

        String botBlock = profileBlock(pom, "qa-bots");
        assertTrue(botBlock.contains("<qa.groups>bot-regression | bot-campaign</qa.groups>"));
        assertTrue(botBlock.contains("<qa.excludedGroups>bot-quality</qa.excludedGroups>"));

        String qualityBlock = profileBlock(pom, "qa-bot-quality");
        assertTrue(qualityBlock.contains("<qa.groups>bot-quality</qa.groups>"));
        assertTrue(qualityBlock.contains("<qa.excludedGroups></qa.excludedGroups>"));
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

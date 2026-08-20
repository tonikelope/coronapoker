package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Prevents CI and the standalone QA module from silently testing a stale game artifact.
 */
class QaBaselineWiringTest {

    private static final Pattern PROJECT_VERSION = Pattern.compile(
            "<artifactId>(?:CoronaPoker|coronapoker-bot-tests|coronapoker-qa-reactor)</artifactId>\\s*<version>([^<]+)</version>");

    @Test
    void projectQaAndReactorVersionsStayAligned() throws IOException {
        Path root = locateRoot();
        String gameVersion = projectVersion(root.resolve("pom.xml"));
        String qaVersion = projectVersion(root.resolve("tools/qa/pom.xml"));
        String reactorVersion = projectVersion(root.resolve("tools/reactor/pom.xml"));
        String qaPom = Files.readString(root.resolve("tools/qa/pom.xml"));

        assertEquals(gameVersion, qaVersion);
        assertEquals(gameVersion, reactorVersion);
        assertTrue(qaPom.contains("<coronapoker.version>" + gameVersion + "</coronapoker.version>"));
    }

    @Test
    void ciBuildsCurrentCommitThroughReactorAndFailsOnZeroTests() throws IOException {
        Path root = locateRoot();
        String workflow = Files.readString(root.resolve(".github/workflows/main.yml"))
                .replace("\\r\\n", "\\n");
        String gamePom = Files.readString(root.resolve("pom.xml"));
        String qaPom = Files.readString(root.resolve("tools/qa/pom.xml"));
        String updaterPom = Files.readString(root.resolve("coronaupdater/pom.xml"));
        String readme = Files.readString(root.resolve("README.md"));
        String identitySpec = Files.readString(root.resolve("docs/ec-identity-spec.md"));

        assertTrue(workflow.contains("actions/checkout@"), "CI must checkout the repository");
        assertTrue(workflow.contains("ref: ${{ github.sha }}"), "CI must test the triggering commit");
        assertTrue(workflow.contains("java-version: '17'"), "CI must pin JDK 17");
        assertTrue(gamePom.contains("<maven.compiler.release>17</maven.compiler.release>"),
                "The game must compile against the Java 17 API baseline");
        assertTrue(qaPom.contains("<maven.compiler.release>17</maven.compiler.release>"),
                "QA must compile against the Java 17 API baseline");
        assertTrue(updaterPom.contains("<maven.compiler.release>17</maven.compiler.release>"),
                "The updater must compile against the Java 17 API baseline");
        assertTrue(readme.contains("JDK 17 or newer for both building and running"),
                "README must publish the Java 17 build and runtime requirement");
        assertTrue(identitySpec.contains("supported JDK 17+ runtime"),
                "The public identity specification must match the supported runtime");
        assertTrue(workflow.contains("tools/reactor/pom.xml"), "CI must build game and QA in one reactor");
        assertTrue(workflow.contains("qa-fast"), "CI must execute the fast QA lane");
        assertTrue(qaPom.contains("<failIfNoTests>true</failIfNoTests>"),
                "Surefire must fail when no tests are discovered");
    }

    private static String projectVersion(Path pom) throws IOException {
        Matcher matcher = PROJECT_VERSION.matcher(Files.readString(pom));
        assertTrue(matcher.find(), "project version not found in " + pom);
        return matcher.group(1).trim();
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

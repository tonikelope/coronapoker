package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Prevents the standalone QA module from silently testing a stale game artifact.
 */
class QaBaselineWiringTest {

    @Test
    void projectQaAndReactorVersionsStayAligned() throws IOException {
        Path root = locateRoot();
        String gameVersion = projectVersion(root.resolve("pom.xml"), "coronapoker");
        String modulesVersion = projectVersion(root.resolve("modules/pom.xml"), "coronapoker-modules");
        String qaVersion = projectVersion(root.resolve("tools/qa/pom.xml"), "coronapoker-modules");
        String reactorVersion = projectVersion(root.resolve("tools/reactor/pom.xml"), "coronapoker-qa-reactor");
        String qaPom = Files.readString(root.resolve("tools/qa/pom.xml"));
        String modulesPom = Files.readString(root.resolve("modules/pom.xml"));

        assertEquals(gameVersion, modulesVersion);
        assertEquals(gameVersion, qaVersion);
        assertEquals(gameVersion, reactorVersion);
        assertTrue(modulesPom.contains("<coronapoker.version>" + gameVersion + "</coronapoker.version>"));
        assertTrue(qaPom.contains("<artifactId>coronapoker-core</artifactId>"));
        assertTrue(qaPom.contains("<artifactId>coronapoker-gdx</artifactId>"));
    }

    @Test
    void localBuildUsesCurrentReactorAndFailsOnZeroTests() throws IOException {
        Path root = locateRoot();
        String gamePom = Files.readString(root.resolve("pom.xml"));
        String modulesPom = Files.readString(root.resolve("modules/pom.xml"));
        String qaPom = Files.readString(root.resolve("tools/qa/pom.xml"));
        String updaterPom = Files.readString(root.resolve("coronaupdater/pom.xml"));
        String readme = Files.readString(root.resolve("README.md"));
        String identitySpec = Files.readString(root.resolve("docs/ec-identity-spec.md"));
        String mavenConfig = Files.readString(root.resolve(".mvn/maven.config"));

        assertTrue(gamePom.contains("<module>modules</module>"),
                "The root build must delegate to the product module reactor");
        assertTrue(modulesPom.contains("<maven.compiler.release>17</maven.compiler.release>"),
                "Product modules must compile against the Java 17 API baseline");
        assertTrue(qaPom.contains("<maven.compiler.release>17</maven.compiler.release>"),
                "QA must compile against the Java 17 API baseline");
        assertTrue(updaterPom.contains("<maven.compiler.release>17</maven.compiler.release>"),
                "The updater must compile against the Java 17 API baseline");
        assertTrue(readme.contains("JDK 17 or newer for both building and running"),
                "README must publish the Java 17 build and runtime requirement");
        assertTrue(identitySpec.contains("supported JDK 17+ runtime"),
                "The public identity specification must match the supported runtime");
        assertTrue(qaPom.contains("<failIfNoTests>true</failIfNoTests>"),
                "Surefire must fail when no tests are discovered");
        assertTrue(qaPom.contains("<runOrder>alphabetical</runOrder>"),
                "Surefire must use a deterministic order without persistent timing files");
        assertTrue(mavenConfig.contains("--no-transfer-progress"),
                "The checkout must be a Maven project root with quiet, reproducible output");
        assertTrue(mavenConfig.contains("-Dmaven.repo.local=.m2/repository"),
                "Maven must use the ignored checkout-local cache on every machine");
    }

    @Test
    void publicReactorCommandsReachThePackagedGameLifecycle() throws IOException {
        Path root = locateRoot();
        List<Path> publicInstructions = List.of(
                root.resolve("docs/TESTING.md"),
                root.resolve("docs/BOTS.md"),
                root.resolve("tools/reactor/pom.xml"),
                root.resolve("tools/qa/src/test/java/com/tonikelope/coronapoker/bot/harness/README.md"),
                root.resolve("tools/qa/src/test/java/com/tonikelope/coronapoker/protocolsim/README.md"),
                root.resolve("tools/qa/src/test/java/com/tonikelope/coronapoker/smoke/README.md"));

        for (Path instruction : publicInstructions) {
            for (String line : Files.readAllLines(instruction)) {
                String command = line.trim();
                if (command.startsWith("mvn ") && command.contains("tools/reactor/pom.xml")) {
                    assertTrue(command.matches(".*\\b(?:verify|install)\\b.*"),
                            "reactor command stops before the game JAR exists in "
                            + instruction + ": " + command);
                    assertTrue(!command.matches(".*\\b(?:test|test-compile|dependency:analyze)\\b.*"),
                            "invalid early reactor lifecycle in " + instruction + ": " + command);
                }
            }
        }
    }

    @Test
    void publicWindowsLaunchersBypassOnlyProcessPolicyAndPreserveExitCodes() throws IOException {
        Path root = locateRoot();
        assertLauncher(root, "certify.cmd", "run-certification.ps1");
        assertLauncher(root, "headless-sim.cmd", "run-headless-sim.ps1");
        String qaLauncher = Files.readString(root.resolve("qa.cmd"))
                .replace("\r\n", "\n");
        assertTrue(qaLauncher.contains("powershell.exe -NoLogo -NoProfile "
                + "-ExecutionPolicy Bypass -File \"%~dp0tools\\qa\\run-all.ps1\" %*"),
                "root QA launcher must forward every argument to run-all.ps1");
        assertTrue(qaLauncher.contains("exit /b %ERRORLEVEL%"),
                "root QA launcher must preserve the failing stage exit code");

        String testing = Files.readString(root.resolve("docs/TESTING.md"));
        assertTrue(testing.contains(".\\qa.cmd test"));
        assertTrue(testing.contains(".\\qa.cmd scenarios fast"));
        assertTrue(testing.contains(".\\tools\\qa\\certify.cmd"));
        assertTrue(testing.contains(".\\tools\\qa\\headless-sim.cmd"));
        assertFalse(testing.contains("gdx-scenarios.cmd"),
                "the retired duplicate scenario launcher must stay undocumented");
    }

    @Test
    void centralQaRunnerKeepsBuildTestsAndScenariosExplicit() throws IOException {
        Path root = locateRoot();
        String runner = Files.readString(root.resolve("tools/qa/run-all.ps1"));

        assertTrue(runner.contains("'build', 'test', 'scenarios', 'all', 'list', 'help'"));
        assertTrue(runner.contains("$Action = 'test'"),
                "plain qa.cmd must default to tests, never scenarios");
        assertTrue(runner.contains("'-DskipTests', 'clean', 'package'"),
                "build must be an explicit no-test package operation");
        assertTrue(runner.contains("'clean', 'install'"),
                "test must install the exact checkout before standalone QA");
        assertTrue(runner.contains("'test', '-Pqa-all'"),
                "test must include every replayable non-bot QA lane");
        assertTrue(runner.contains("& $certifier @certificationArgs"),
                "scenario execution must delegate to the one public catalogue");
        assertTrue(runner.contains(".m2\\repository"),
                "the public runner must use the checkout-local dependency cache");
        assertTrue(runner.contains("summary.json"));
        assertTrue(runner.contains("summary.txt"));
    }

    @Test
    void qaRunnersGenerateFreshDefaultsAndPersistReplaySeed() throws IOException {
        Path root = locateRoot();
        String seedHelper = Files.readString(root.resolve("tools/qa/qa-seed.ps1"));
        assertTrue(seedHelper.contains("RandomNumberGenerator]::Create()"),
                "QA default seeds must come from fresh OS entropy");
        assertTrue(seedHelper.contains("ToUInt32"),
                "QA seeds must stay in the safe range used for derived scenario seeds");

        for (String runner : List.of("run-all.ps1", "run-certification.ps1",
                "run-headless-sim.ps1")) {
            String script = Files.readString(root.resolve("tools/qa").resolve(runner));
            assertTrue(script.contains(". (Join-Path $PSScriptRoot 'qa-seed.ps1')"),
                    runner + " must use the shared seed generator");
            assertTrue(script.contains("$PSBoundParameters.ContainsKey('Seed')"),
                    runner + " must preserve an explicit replay seed");
            assertTrue(script.contains("New-CoronaPokerQaSeed"),
                    runner + " must generate a seed when none is supplied");
        }

        String certification = Files.readString(root.resolve("tools/qa/run-certification.ps1"));
        assertTrue(certification.contains("BaseSeed = $Seed"),
                "machine-readable certification summaries must persist the replay seed");
        assertTrue(certification.contains("-StartAtScenario requires the BaseSeed"),
                "continuation must not silently switch to a fresh schedule seed");
        assertTrue(certification.contains("'quick', 'fast', 'balanced', 'stress'"),
                "certifier must expose the full-matrix fast preflight explicitly");
        assertTrue(certification.contains("-StartAtRepeat requires -StartAtScenario"),
                "an exact-repeat checkpoint must not be accepted without a scenario");
        assertTrue(certification.contains("ScenarioRepeats = $ScenarioRepeats"),
                "summaries must persist the schedule needed to resume exactly");
        assertTrue(certification.contains("\"-Dqa.sim.seed=$scenarioSeed\""),
                "each scenario process must receive its derived replay seed");
        assertFalse(certification.contains("'-o'"),
                "the public certifier must work with an empty checkout-local Maven cache");

    }

    @Test
    void publicTestingManualAndEditableDiagramStayDiscoverableAndPaired() throws IOException {
        Path root = locateRoot();
        String readme = Files.readString(root.resolve("README.md"));
        String testing = Files.readString(root.resolve("docs/TESTING.md"));
        assertTrue(readme.contains("docs/TESTING.md"),
                "README must link the canonical testing manual");
        assertTrue(testing.contains("diagrams/testing-certification-flow.png"),
                "testing manual must render its certification diagram");
        assertTrue(Files.isRegularFile(root.resolve(
                "docs/diagrams/testing-certification-flow.drawio")),
                "editable Draw.io source is required");
        assertTrue(Files.size(root.resolve(
                "docs/diagrams/testing-certification-flow.png")) > 100_000,
                "exported 2x testing diagram is missing or unexpectedly small");
    }

    private static void assertLauncher(Path root, String launcher, String script) throws IOException {
        Path launcherPath = root.resolve("tools/qa").resolve(launcher);
        String command = Files.readString(launcherPath).replace("\r\n", "\n");
        assertTrue(command.contains("powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File \"%~dp0"
                + script + "\" %*"), "launcher must invoke its colocated script with every argument: " + launcher);
        assertTrue(command.contains("exit /b %ERRORLEVEL%"),
                "launcher must propagate the certification result: " + launcher);
    }

    private static String projectVersion(Path pom, String artifactId) throws IOException {
        Pattern versionPattern = Pattern.compile(
                "<artifactId>" + Pattern.quote(artifactId)
                + "</artifactId>\\s*<version>([^<]+)</version>");
        Matcher matcher = versionPattern.matcher(Files.readString(pom));
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

package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards the new GDX suite without modifying the established Swing suite. */
class GdxScenarioContractTest {

    private static final Pattern QUOTED = Pattern.compile("\\\"([^\\\"]+)\\\"");
    private static final Set<String> BLOCKING_GDX_UI_SCENARIOS = Set.of(
            "controlled-exit", "spectator-rebuy-cycle",
            "normal", "raise-mix", "allin-single-board", "allin-rit",
            "allin-rebuy", "straddle-post",
            "rit-network-cut", "straddle-network-cut", "pause-resume",
            "force-recover");

    @Test
    void gdxCatalogueIsAnExactCopyOfTheSwingReference() throws IOException {
        Path root = repositoryRoot();
        Path source = root.resolve("tools/qa/src/test/java/com/tonikelope/"
                + "coronapoker/e2e/RealGameScenarioContract.java");
        String text = Files.readString(source, StandardCharsets.UTF_8);
        Set<String> reference = new HashSet<>();
        Matcher matcher = QUOTED.matcher(text.substring(0,
                text.indexOf("private RealGameScenarioContract")));
        while (matcher.find()) {
            reference.add(matcher.group(1));
        }
        assertEquals(reference, GdxScenarioContract.SWING_REFERENCE,
                "the GDX catalogue must track Swing without editing Swing tests");
    }

    private static Path repositoryRoot() {
        String configured = System.getProperty("coronapoker.repo.root", "");
        if (!configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        Path candidate = Path.of(System.getProperty("user.dir", "."))
                .toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("tools/qa/pom.xml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("CoronaPoker repository root not found");
    }

    @Test
    void everyDeclaredMappingNamesAnExecutableGdxNetworkTest() {
        Set<String> methods = Arrays.stream(
                new Class<?>[]{GdxNetworkHumanProjectionIntegrationTest.class,
                    GdxReconnectScenarioTest.class})
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Test.class))
                .map(java.lang.reflect.Method::getName)
                .collect(java.util.stream.Collectors.toSet());
        GdxScenarioContract.SUPPORTING_NETWORK_TESTS.forEach((scenario, tests) -> {
            assertTrue(GdxScenarioContract.SWING_REFERENCE.contains(scenario)
                            || scenario.equals("turn-timeout")
                            || scenario.equals("live-rules-last-hand")
                            || scenario.equals("paused-exit"),
                    "unknown supporting scenario: " + scenario);
            assertTrue(methods.containsAll(tests),
                    "missing supporting GDX tests for " + scenario + ": "
                            + tests.stream().filter(test -> !methods.contains(test)).toList());
        });
        GdxScenarioContract.STRICT_HOMOLOGUE_TESTS.forEach((scenario, tests) -> {
            assertTrue(GdxScenarioContract.SWING_REFERENCE.contains(scenario),
                    "strict GDX homologue is absent from Swing: " + scenario);
            assertTrue(methods.containsAll(tests),
                    "missing strict GDX homologue for " + scenario + ": "
                            + tests.stream().filter(test -> !methods.contains(test)).toList());
        });
    }

    @Test
    void everySwingScenarioHasItsOwnStrictGdxCoverage() {
        assertEquals(GdxScenarioContract.SWING_REFERENCE,
                GdxScenarioContract.STRICT_HOMOLOGUE_TESTS.keySet(),
                "catalogue parity alone is insufficient: every Swing scenario "
                        + "must have strict executable GDX coverage");

        Set<String> uniqueTests = new HashSet<>();
        GdxScenarioContract.STRICT_HOMOLOGUE_TESTS.forEach((scenario, tests) -> {
            assertTrue(!tests.isEmpty(),
                    "strict GDX coverage is empty for " + scenario);
            tests.forEach(test -> assertTrue(uniqueTests.add(test),
                    "one GDX test cannot certify two different Swing scenarios: "
                            + test));
        });
    }

    @Test
    void blockingGdxUiScenariosAlsoExerciseNativeTableWiring() {
        BLOCKING_GDX_UI_SCENARIOS.forEach(scenario -> {
            Set<String> tests = GdxScenarioContract.STRICT_HOMOLOGUE_TESTS
                    .getOrDefault(scenario, Set.of());
            assertTrue(tests.stream().anyMatch(
                            test -> test.startsWith("nativeGdx")),
                    "blocking GDX interaction lacks a native table test: "
                            + scenario);
        });
    }

    @Test
    void nativeNetworkDialogsCannotUseDetachedSyntheticTables()
            throws IOException {
        Path integrationSource = repositoryRoot().resolve("modules/coronapoker-gdx/src/"
                + "test/java/com/tonikelope/coronapoker/gdx/"
                + "GdxNetworkHumanProjectionIntegrationTest.java");
        Path scenarioSource = repositoryRoot().resolve("modules/coronapoker-gdx/src/"
                + "test/java/com/tonikelope/coronapoker/gdx/scenarios/"
                + "GdxReconnectScenarioTest.java");
        String integration = Files.readString(integrationSource,
                StandardCharsets.UTF_8);
        String scenarios = Files.readString(scenarioSource,
                StandardCharsets.UTF_8);
        assertFalse(integration.contains("dialogTable()"),
                "network decisions must use the CoronaPokerGdxTable bound to "
                        + "the real peer session, never a detached synthetic table");
        assertFalse(scenarios.contains("nativeDialogTable"),
                "strict scenarios must resolve dialogs on each peer's real "
                        + "CoronaPokerGdxTable");
    }

    @Test
    void sharedScenarioDriverCannotBypassNativeGdxActionControls()
            throws IOException {
        Path driverSource = repositoryRoot().resolve("modules/coronapoker-gdx/src/"
                + "test/java/com/tonikelope/coronapoker/gdx/scenarios/"
                + "GdxScenarioRenderer.java");
        Path scenariosSource = repositoryRoot().resolve("modules/coronapoker-gdx/src/"
                + "test/java/com/tonikelope/coronapoker/gdx/scenarios/"
                + "GdxReconnectScenarioTest.java");
        String driver = Files.readString(driverSource, StandardCharsets.UTF_8);
        String scenarios = Files.readString(scenariosSource,
                StandardCharsets.UTF_8);
        for (String source : Set.of(driver, scenarios)) {
            assertFalse(source.contains("new TableCommand.Fold()"),
                    "strict scenarios must activate the native GDX fold control");
            assertFalse(source.contains("new TableCommand.CheckOrCall()"),
                    "strict scenarios must activate the native GDX call control");
            assertFalse(source.contains("new TableCommand.AllIn()"),
                    "strict scenarios must activate the native GDX ALL-IN control");
        }
        assertTrue(driver.contains("new CoronaPokerGdxTable("),
                "the shared scenario driver must bind the product GDX table");
    }

    @Test
    void officialRunnerIsolatesEveryMappedTestInItsOwnMavenProcess()
            throws IOException {
        Path root = repositoryRoot();
        String runner = Files.readString(root.resolve(
                "tools/qa/run-gdx-scenarios.ps1"), StandardCharsets.UTF_8);
        String launcher = Files.readString(root.resolve(
                "tools/qa/gdx-scenarios.cmd"), StandardCharsets.UTF_8);

        assertTrue(runner.contains("STRICT_HOMOLOGUE_TESTS"));
        assertTrue(runner.contains("foreach ($method in $entry.Methods)"));
        assertTrue(runner.contains("$selector = $test.Class + '#' + $test.Method"));
        assertTrue(runner.contains("& $maven @mavenArgs"));
        assertTrue(runner.contains("target\\gdx-scenarios"));
        assertTrue(launcher.contains("run-gdx-scenarios.ps1"));
    }
}

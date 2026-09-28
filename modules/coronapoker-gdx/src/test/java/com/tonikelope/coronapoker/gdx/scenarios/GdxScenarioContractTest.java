package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Guards the GDX suite against the immutable historical Swing GOLD baseline. */
class GdxScenarioContractTest {

    private static final Set<String> BLOCKING_GDX_UI_SCENARIOS = Set.of(
            "controlled-exit", "spectator-rebuy-cycle",
            "normal", "raise-mix", "allin-single-board", "allin-rit",
            "allin-rebuy", "straddle-post",
            "rit-network-cut", "straddle-network-cut", "pause-resume",
            "force-recover");

    @Test
    void gdxCatalogueIsAnExactCopyOfTheSwingGoldManifest() throws IOException {
        Path root = repositoryRoot();
        Path manifest = root.resolve(
                "tools/qa/reference/swing-gold-scenarios.tsv");
        Set<String> reference = Files.readAllLines(manifest,
                        StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> line.split("\\t", -1))
                .peek(parts -> assertEquals(2, parts.length,
                        "invalid Swing GOLD manifest row"))
                .map(parts -> parts[1])
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(reference, GdxScenarioContract.SWING_REFERENCE,
                "the GDX catalogue must preserve the final Swing GOLD baseline");
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
                    GdxReconnectScenarioTest.class,
                    GdxMultiprocessScenarioTest.class})
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
        GdxScenarioContract.AUXILIARY_HOMOLOGUE_TESTS.forEach((scenario, tests) -> {
            assertTrue(GdxScenarioContract.SWING_REFERENCE.contains(scenario),
                    "auxiliary GDX homologue is absent from Swing: " + scenario);
            assertTrue(methods.containsAll(tests),
                    "missing auxiliary GDX homologue for " + scenario + ": "
                            + tests.stream().filter(test -> !methods.contains(test)).toList());
        });
        GdxScenarioContract.SWING_GOLD_MULTIPROCESS_TESTS.forEach(
                (scenario, tests) -> assertTrue(methods.containsAll(tests),
                        "missing multi-process GOLD test for " + scenario + ": "
                                + tests.stream().filter(
                                        test -> !methods.contains(test)).toList()));
        GdxScenarioContract.NATIVE_GDX_UI_TESTS.forEach(
                (scenario, tests) -> assertTrue(methods.containsAll(tests),
                        "missing native GDX UI test for " + scenario + ": "
                                + tests.stream().filter(
                                        test -> !methods.contains(test)).toList()));
        GdxScenarioContract.GDX_ONLY_SCENARIOS.forEach((scenario, tests) -> {
            assertFalse(GdxScenarioContract.SWING_REFERENCE.contains(scenario),
                    "GDX-only scenario duplicates the Swing baseline: " + scenario);
            assertTrue(methods.containsAll(tests),
                    "missing GDX-only scenario tests for " + scenario + ": "
                            + tests.stream().filter(test -> !methods.contains(test)).toList());
        });
    }

    @Test
    void everySwingScenarioRetainsAuxiliaryGdxCoverage() {
        assertEquals(GdxScenarioContract.SWING_REFERENCE,
                GdxScenarioContract.AUXILIARY_HOMOLOGUE_TESTS.keySet(),
                "catalogue parity alone is insufficient: every Swing scenario "
                        + "must retain auxiliary executable GDX coverage");

        Set<String> uniqueTests = new HashSet<>();
        GdxScenarioContract.AUXILIARY_HOMOLOGUE_TESTS.forEach((scenario, tests) -> {
            assertTrue(!tests.isEmpty(),
                    "auxiliary GDX coverage is empty for " + scenario);
            tests.forEach(test -> assertTrue(uniqueTests.add(test),
                    "one GDX test cannot certify two different Swing scenarios: "
                            + test));
        });
    }

    @Test
    void everySwingScenarioRetainsASeparateProcessSocketHomologue() {
        Set<String> multiprocessMethods = Arrays.stream(
                        GdxMultiprocessScenarioTest.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Test.class))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(GdxScenarioContract.SWING_REFERENCE,
                GdxScenarioContract.SWING_GOLD_MULTIPROCESS_TESTS.keySet(),
                "every Swing GOLD scenario must have an official multi-process port");
        GdxScenarioContract.SWING_GOLD_MULTIPROCESS_TESTS.forEach(
                (scenario, tests) -> {
                    assertTrue(!tests.isEmpty(),
                            "empty multi-process GOLD mapping: " + scenario);
                    assertTrue(tests.stream().allMatch(
                                    multiprocessMethods::contains),
                            "GOLD mapping contains an in-process substitute: "
                                    + scenario);
                });
    }

    @Test
    void certificationMethodsBelongToOneScenarioOnly() {
        Set<String> uniqueTests = new HashSet<>();
        GdxScenarioContract.SWING_GOLD_MULTIPROCESS_TESTS.values().stream()
                .flatMap(Set::stream)
                .forEach(test -> assertTrue(uniqueTests.add(test),
                        "duplicated GOLD certification test: " + test));
        GdxScenarioContract.NATIVE_GDX_UI_TESTS.values().stream()
                .flatMap(Set::stream)
                .forEach(test -> assertTrue(uniqueTests.add(test),
                        "native UI test duplicates another certification test: "
                                + test));
        GdxScenarioContract.GDX_ONLY_SCENARIOS.values().stream()
                .flatMap(Set::stream)
                .forEach(test -> assertTrue(uniqueTests.add(test),
                        "GDX-only certification test duplicates another scenario: "
                                + test));
    }

    @Test
    void certificationMethodsStayOutOfTheNormalProductBuild() {
        Map<String, Method> methods = Arrays.stream(
                new Class<?>[]{GdxNetworkHumanProjectionIntegrationTest.class,
                    GdxReconnectScenarioTest.class,
                    GdxMultiprocessScenarioTest.class})
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Test.class))
                .collect(Collectors.toMap(Method::getName,
                        Function.identity()));
        Set<String> certificationTests = new HashSet<>();
        GdxScenarioContract.SWING_GOLD_MULTIPROCESS_TESTS.values().forEach(
                certificationTests::addAll);
        GdxScenarioContract.NATIVE_GDX_UI_TESTS.values().forEach(
                certificationTests::addAll);
        GdxScenarioContract.GDX_ONLY_SCENARIOS.values().forEach(
                certificationTests::addAll);

        certificationTests.forEach(name -> {
            Method method = methods.get(name);
            assertTrue(method != null, "missing certification method: " + name);
            Tag methodTag = method.getAnnotation(Tag.class);
            Tag classTag = method.getDeclaringClass().getAnnotation(Tag.class);
            assertTrue((methodTag != null
                            && methodTag.value().equals("certification"))
                            || (classTag != null
                            && classTag.value().equals("certification")),
                    "certification method is not isolated from the product build: "
                            + name);
        });
    }

    @Test
    void blockingGdxUiScenariosAlsoExerciseNativeTableWiring() {
        BLOCKING_GDX_UI_SCENARIOS.forEach(scenario -> {
            Set<String> tests = GdxScenarioContract.NATIVE_GDX_UI_TESTS
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
    void multiprocessOracleObservesRealLobbyConnectivityAndDepartureState()
            throws IOException {
        Path root = repositoryRoot();
        String driver = Files.readString(root.resolve("modules/coronapoker-gdx/src/"
                + "test/java/com/tonikelope/coronapoker/gdx/scenarios/"
                + "GdxScenarioRenderer.java"), StandardCharsets.UTF_8);
        String node = Files.readString(root.resolve("modules/coronapoker-gdx/src/"
                + "test/java/com/tonikelope/coronapoker/gdx/"
                + "GdxMultiprocessNodeMain.java"), StandardCharsets.UTF_8);

        assertTrue(driver.contains("LobbySession lobby"),
                "the product table oracle must receive the real lobby session");
        assertTrue(driver.contains("lobby));"),
                "the product table oracle cannot detach network presence");
        assertTrue(node.contains("productTable, lobby"),
                "multiprocess nodes must wire their real lobby into GDX");
        assertTrue(node.contains(
                "renderer.assertNeverShowedReconnectFor(\"client1\")"),
                "voluntary exit must reject a visible reconnect projection");
        assertTrue(node.contains("visible.add(\"server\")"),
                "a disconnected client must observe the remote host seat, "
                + "not permit its own local seat as reconnecting");
        assertTrue(node.contains("Set<String> disconnectedPeers = switch"),
                "visible reconnect allowances must be scenario-specific");
    }

    @Test
    void multiprocessOracleRejectsCorruptEventAndHandLifecycles()
            throws IOException {
        String driver = Files.readString(repositoryRoot().resolve(
                "modules/coronapoker-gdx/src/test/java/com/tonikelope/"
                + "coronapoker/gdx/scenarios/GdxScenarioRenderer.java"),
                StandardCharsets.UTF_8);

        assertTrue(driver.contains("lastEventSequence"),
                "the shared oracle must reject duplicated or reordered events");
        assertTrue(driver.contains("preparedHandIds"),
                "the shared oracle must remember distinct PREPARE boundaries");
        assertTrue(driver.contains("endedHandIds"),
                "the shared oracle must remember distinct END boundaries");
        assertTrue(driver.contains("assertEquals(preparedHandIds, endedHandIds"),
                "completion must prove every observed hand closed exactly once");
        assertTrue(driver.contains("event arrived after CloseTable"),
                "CloseTable must remain the terminal visual event");

        String processOracle = Files.readString(repositoryRoot().resolve(
                "modules/coronapoker-gdx/src/test/java/com/tonikelope/"
                + "coronapoker/gdx/GdxMultiprocessScenarioTest.java"),
                StandardCharsets.UTF_8);
        assertTrue(processOracle.contains(
                "node.count(\"CP_GDX_E2E_HANDS_COMPLETE \""),
                "a duplicated terminal outcome cannot satisfy a scenario");
    }

    @Test
    void officialRunnerIsolatesEveryMappedTestInItsOwnMavenProcess()
            throws IOException {
        Path root = repositoryRoot();
        String runner = Files.readString(root.resolve(
                "tools/qa/run-certification.ps1"), StandardCharsets.UTF_8);
        String launcher = Files.readString(root.resolve(
                "tools/qa/certify.cmd"), StandardCharsets.UTF_8);

        assertTrue(runner.contains("SWING_GOLD_MULTIPROCESS_TESTS"));
        assertTrue(runner.contains("NATIVE_GDX_UI_TESTS"));
        assertTrue(runner.contains("foreach ($method in $entry.Methods)"));
        assertTrue(runner.contains("$selector = $test.Class + '#' + $test.Method"));
        assertTrue(runner.contains("& $maven @arguments"));
        assertTrue(runner.contains("-Dcoronapoker.gdx.excludedGroups="));
        assertTrue(runner.contains("target\\certification"));
        assertTrue(launcher.contains("run-certification.ps1"));
    }
}

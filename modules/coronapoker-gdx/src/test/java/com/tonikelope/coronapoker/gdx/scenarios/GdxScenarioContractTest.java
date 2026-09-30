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
        GdxScenarioContract.CERTIFICATION_SCENARIOS.forEach(
                (scenario, tests) -> {
                    assertFalse(tests.isEmpty(),
                            "empty GDX certification scenario: " + scenario);
                    assertTrue(methods.containsAll(tests),
                            "missing executable GDX tests for " + scenario + ": "
                                    + tests.stream().filter(
                                            test -> !methods.contains(test)).toList());
                });
    }

    @Test
    void unifiedCatalogueContainsEverySwingScenario() {
        assertTrue(GdxScenarioContract.CERTIFICATION_SCENARIOS.keySet()
                        .containsAll(GdxScenarioContract.SWING_REFERENCE),
                "the unified GDX catalogue must contain every Swing scenario");
    }

    @Test
    void everySwingScenarioRetainsASeparateProcessSocketHomologue() {
        Set<String> multiprocessMethods = Arrays.stream(
                        GdxMultiprocessScenarioTest.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Test.class))
                .map(Method::getName)
                .collect(Collectors.toSet());

        GdxScenarioContract.SWING_REFERENCE.forEach(scenario -> {
            Set<String> tests = GdxScenarioContract.CERTIFICATION_SCENARIOS
                    .getOrDefault(scenario, Set.of());
            assertTrue(tests.stream().anyMatch(multiprocessMethods::contains),
                    "historical scenario lacks an independent-process port: "
                            + scenario);
        });
    }

    @Test
    void certificationMethodsBelongToOneScenarioOnly() {
        Set<String> uniqueTests = new HashSet<>();
        GdxScenarioContract.CERTIFICATION_SCENARIOS.values().stream()
                .flatMap(Set::stream)
                .forEach(test -> assertTrue(uniqueTests.add(test),
                        "GDX test belongs to more than one scenario: " + test));
    }

    @Test
    void criticalProductRegressionsRunInTheNormalBuild() {
        Map<String, Method> methods = Arrays.stream(
                        GdxNetworkHumanProjectionIntegrationTest.class
                                .getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Test.class))
                .collect(Collectors.toMap(Method::getName,
                        Function.identity()));

        Set.of("gdxIwtsthCandidateRequestsAndRevealsTheMuckedNetworkHand",
                        "nativeGdxIwtsthOffersAndRevealsAMuckedBotLikeSwing",
                        "realGdxHandsAdvanceBlindsUpdateBothHudProjectionsAndPlayTheGong")
                .stream()
                .forEach(name -> {
                    Method method = methods.get(name);
                    assertTrue(method != null,
                            "missing critical product regression: " + name);
                    Tag methodTag = method.getAnnotation(Tag.class);
                    Tag classTag = method.getDeclaringClass().getAnnotation(Tag.class);
                    assertFalse(methodTag != null
                                    && methodTag.value().equals("certification")
                                    || classTag != null
                                    && classTag.value().equals("certification"),
                            "critical product regression was removed from the normal build: "
                                    + name);
                });
    }

    @Test
    void everyTaggedCertificationMethodBelongsToTheUnifiedCatalogue() {
        Map<String, Method> methods = Arrays.stream(
                new Class<?>[]{GdxNetworkHumanProjectionIntegrationTest.class,
                    GdxReconnectScenarioTest.class,
                    GdxMultiprocessScenarioTest.class})
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Test.class))
                .collect(Collectors.toMap(Method::getName,
                        Function.identity()));
        Set<String> unified = GdxScenarioContract.CERTIFICATION_SCENARIOS
                .values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        Set<String> missing = methods.values().stream().filter(method -> {
            Tag methodTag = method.getAnnotation(Tag.class);
            Tag classTag = method.getDeclaringClass().getAnnotation(Tag.class);
            return methodTag != null && methodTag.value().equals("certification")
                    || classTag != null && classTag.value().equals("certification");
        }).map(Method::getName).filter(name -> !unified.contains(name))
                .collect(Collectors.toSet());
        assertTrue(missing.isEmpty(),
                "tagged GDX scenarios outside the unified catalogue: " + missing);
    }

    @Test
    void blockingGdxUiScenariosAlsoExerciseNativeTableWiring() {
        BLOCKING_GDX_UI_SCENARIOS.forEach(scenario -> {
            Set<String> tests = GdxScenarioContract.CERTIFICATION_SCENARIOS
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
    void everyScenarioRendererKeepsTheSemanticHudOracle()
            throws IOException {
        Path root = repositoryRoot();
        String driver = Files.readString(root.resolve("modules/coronapoker-gdx/src/"
                + "test/java/com/tonikelope/coronapoker/gdx/scenarios/"
                + "GdxScenarioRenderer.java"), StandardCharsets.UTF_8);
        String node = Files.readString(root.resolve("modules/coronapoker-gdx/src/"
                + "test/java/com/tonikelope/coronapoker/gdx/"
                + "GdxMultiprocessNodeMain.java"), StandardCharsets.UTF_8);
        String oracle = Files.readString(root.resolve(
                "modules/coronapoker-gdx/src/test/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFunctionalLabelOracle.java"),
                StandardCharsets.UTF_8);
        String integration = Files.readString(root.resolve(
                "modules/coronapoker-gdx/src/test/java/com/tonikelope/"
                + "coronapoker/gdx/GdxNetworkHumanProjectionIntegrationTest.java"),
                StandardCharsets.UTF_8);
        String reconnect = Files.readString(root.resolve(
                "modules/coronapoker-gdx/src/test/java/com/tonikelope/"
                + "coronapoker/gdx/scenarios/GdxReconnectScenarioTest.java"),
                StandardCharsets.UTF_8);

        assertTrue(node.contains("new GdxScenarioRenderer("),
                "official process scenarios must use the shared GDX renderer");
        assertTrue(driver.contains(
                "assertProjectedLabelContract(event, projection)"),
                "multiprocess scenarios lost the shared label oracle");
        for (String requiredOracle : Set.of(
                "localizedActionLabel(", "communityPotText(",
                "communityBlindsText(", "communityHandText(",
                "projection.callCostText()", "PlayerDeparture departure",
                "HandResult result")) {
            assertTrue(oracle.contains(requiredOracle),
                    "shared scenarios lost semantic HUD assertion: "
                    + requiredOracle);
        }
        assertTrue(integration.contains(
                "GdxFunctionalLabelOracle.assertProjectedLabelContract("),
                "in-process network scenarios lost the shared label oracle");
        assertTrue(reconnect.contains(
                "GdxFunctionalLabelOracle.assertProjectedLabelContract("),
                "recovery scenarios lost the shared label oracle");
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
        String integration = Files.readString(root.resolve(
                "modules/coronapoker-gdx/src/test/java/com/tonikelope/"
                + "coronapoker/gdx/GdxNetworkHumanProjectionIntegrationTest.java"),
                StandardCharsets.UTF_8);

        assertTrue(driver.contains("LobbySession lobby"),
                "the product table oracle must receive the real lobby session");
        assertTrue(driver.contains("lobby));"),
                "the product table oracle cannot detach network presence");
        assertTrue(driver.contains("lobby.subscribe("),
                "short reconnect projections must be captured from real lobby snapshots");
        assertTrue(driver.contains("current != null && !closed.get()"),
                "terminal lobby teardown must not count as visible reconnect state");
        assertTrue(node.contains("productTable, lobby"),
                "multiprocess nodes must wire their real lobby into GDX");
        assertTrue(node.contains(
                "renderer.assertNeverShowedReconnectFor(\"client1\")"),
                "voluntary exit must reject a visible reconnect projection");
        assertTrue(integration.contains("new GdxGameText(\"es\")"),
                "multiprocess scenarios must exercise production GDX localization");
        assertTrue(node.contains(
                "renderer.assertDepartureLabel(\"client1\", \"SE VA\")"),
                "voluntary exit must assert its exact visible semantic label");
        assertTrue(node.contains("visible.add(\"server\")"),
                "a disconnected client must observe the remote host seat, "
                + "not permit its own local seat as reconnecting");
        assertTrue(node.contains("expectedDisconnectedPeers(Config config)"),
                "visible reconnect allowances must be scenario-specific");
        assertTrue(node.contains("requiredVisibleReconnects(config)"),
                "real network cuts must require their GDX reconnect projection");
        assertTrue(node.contains(
                "!= TableSessionSummary.CloseReason.EXITED"),
                "connectivity must be judged from surviving renderers");
        assertTrue(node.contains("assertPhaseReconnects(Config config"),
                "recovery scenarios must validate every session phase");
        assertTrue(node.contains(
                "phase == 1 ? Set.of(\"client3\") : Set.of()"),
                "the injected recovery crash must be observable only in its phase");
        assertTrue(node.contains(
                "recovering ? Set.of(\"client3\")"),
                "transport chaos must distinguish pre/post-recovery cuts");
        assertTrue(node.contains(
                "phase == 0 ? Set.of(\"client1\")"),
                "lifecycle chaos must keep phase-specific reconnect identities");
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
        assertTrue(driver.contains("skippedRecoveredHandIds"),
                "the shared oracle must identify passively skipped recovered hands");
        assertTrue(driver.contains("assertEquals(preparedHandIds, closedHandIds"),
                "completion must prove every observed hand ended or was explicitly skipped");
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

        assertTrue(runner.contains("CERTIFICATION_SCENARIOS"));
        assertFalse(runner.contains("$certificationMaps"),
                "the public runner must not rebuild special scenario lanes");
        assertTrue(runner.contains("foreach ($method in $entry.Methods)"));
        assertTrue(runner.contains("$selector = $test.Class + '#' + $test.Method"));
        assertTrue(runner.contains("& $maven @arguments"));
        assertTrue(runner.contains("-Dcoronapoker.gdx.excludedGroups="));
        assertTrue(runner.contains("target\\certification"));
        assertTrue(launcher.contains("run-certification.ps1"));
    }
}

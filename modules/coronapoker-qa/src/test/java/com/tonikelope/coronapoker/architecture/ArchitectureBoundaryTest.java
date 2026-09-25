package com.tonikelope.coronapoker.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArchitectureBoundaryTest {

    private static final Pattern GRAPHICS_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+(?:java\\.awt|javax\\.swing|com\\.badlogic\\.gdx)(?:\\.|;)");
    private final Path reactor = Path.of(System.getProperty("architecture.reactor.dir"))
            .toAbsolutePath().normalize();

    @Test
    void coreHasNoSwingAwtOrLibgdxImports() throws IOException {
        List<Path> violations = coreSources().stream()
                .filter(this::containsGraphicsImport)
                .toList();
        assertTrue(violations.isEmpty(), "Graphics imports in core: " + violations);
    }

    @Test
    void frontendsDependOnlyTowardCore() throws IOException {
        String corePom = read("coronapoker-core/pom.xml");
        String swingPom = read("coronapoker-swing/pom.xml");
        String gdxPom = read("coronapoker-gdx/pom.xml");

        assertFalse(corePom.contains("coronapoker-swing"));
        assertFalse(corePom.contains("coronapoker-gdx"));
        assertFalse(swingPom.contains("gdx-backend"));
        assertFalse(gdxPom.contains("coronapoker-swing"));
        assertTrue(swingPom.contains("<artifactId>coronapoker-core</artifactId>"));
        assertTrue(gdxPom.contains("<artifactId>coronapoker-core</artifactId>"));
        assertFalse(corePom.contains("build-helper-maven-plugin"));
        assertFalse(swingPom.contains("build-helper-maven-plugin"));
        assertFalse(corePom.contains("<includes>"));
        assertFalse(swingPom.contains("com/tonikelope/coronapoker/core/**"));
        assertFalse(swingPom.contains("com/tonikelope/coronapoker/table/**"));
        assertTrue(Files.isDirectory(reactor.resolve("coronapoker-core/src/main/java")));
        assertTrue(Files.isDirectory(reactor.resolve("coronapoker-swing/src/main/java")));
    }

    @Test
    void productSourcesHaveOnePhysicalOwner() throws IOException {
        Path repository = reactor.getParent();
        Path legacyRoot = repository.resolve("src/main/java");
        if (Files.isDirectory(legacyRoot)) {
            try (Stream<Path> files = Files.walk(legacyRoot)) {
                List<Path> leftovers = files
                        .filter(Files::isRegularFile)
                        .toList();
                assertTrue(leftovers.isEmpty(),
                        "Legacy source tree must be empty: " + leftovers);
            }
        }

        List<Path> roots = List.of(
                reactor.resolve("coronapoker-core/src/main/java"),
                reactor.resolve("coronapoker-swing/src/main/java"),
                reactor.resolve("coronapoker-gdx/src/main/java"));
        Set<String> seen = new java.util.HashSet<>();
        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root)) {
                Set<String> relativeSources = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .map(root::relativize)
                        .map(Path::toString)
                        .collect(Collectors.toSet());
                Set<String> duplicates = new java.util.HashSet<>(relativeSources);
                duplicates.retainAll(seen);
                assertTrue(duplicates.isEmpty(),
                        "Sources owned by more than one module: " + duplicates);
                seen.addAll(relativeSources);
            }
        }
    }

    @Test
    void gdxApplicationIsSelfContainedAndDoesNotCompileTheDemo() throws IOException {
        String gdxPom = read("coronapoker-gdx/pom.xml");
        assertFalse(gdxPom.contains("prototype-gdx"));
        assertFalse(gdxPom.contains("gdxdemo"));
        assertTrue(gdxPom.contains("627c71e4f"));
        assertTrue(gdxPom.contains("com.tonikelope.coronapoker.gdx.GdxLauncher"));
        assertTrue(Files.exists(reactor.resolve(
                "coronapoker-gdx/src/main/java/com/tonikelope/coronapoker/gdx/CoronaPokerGdxTable.java")));
    }

    @Test
    void gdxProductionContainsNoScriptedDemoRuntime() throws IOException {
        Path sourceRoot = reactor.resolve("coronapoker-gdx/src/main/java");
        List<String> forbiddenRuntimeSymbols = List.of(
                "package com.tonikelope.coronapoker.gdxdemo",
                "GdxNewGamePreviewLauncher",
                "DEMO_PLAYERS",
                "DEMO_HAND_RANKS",
                "DEMO_HAND_RESULTS",
                "thinkDurations",
                "thinkRandom",
                "communityHands",
                "holeCardHands",
                "handTime(",
                "demoHandIndex(",
                "thinkingAction(",
                "potAt(",
                "COMMUNITY_REVEAL",
                "SHOWDOWN_START",
                "SHOWCASE_",
                "LOCAL_CARD_RANKS",
                "SHOWDOWN_RESULTS",
                "HUD_ACTIONS",
                "if (\"ALL_IN\".equals(player.lastAction()))",
                "currentTurnNickname().isBlank() ? 0f : 1f",
                "liveBetAmount = 1d",
                "textToSpeechEnabled = true",
                "voiceMessagesEnabled = true",
                "orElse(TableSnapshot.Position.NONE)",
                "potBefore() == 0d",
                "communityStreet(",
                "autoButtonsImmediateActivation",
                "autoPreActionsEligible(",
                "autoPreActionTarget(",
                "malformed payloads produced by early GDX previews",
                "PostChips",
                "Synchronize",
                "preservePresentedCards",
                "preserveVisibleCards",
                "new TableVisualEvent.",
                "new TableCommand.OpenSettings(",
                "new TableCommand.OpenLog(");

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<Path> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path,
                                    StandardCharsets.UTF_8);
                            return forbiddenRuntimeSymbols.stream()
                                    .anyMatch(source::contains);
                        } catch (IOException ex) {
                            throw new IllegalStateException(
                                    "Cannot inspect " + path, ex);
                        }
                    })
                    .toList();
            assertTrue(violations.isEmpty(),
                    "Scripted demo runtime leaked into GDX production: "
                            + violations);
        }
        assertFalse(Files.exists(sourceRoot.resolve(
                "com/tonikelope/coronapoker/gdx/GdxNewGamePreviewLauncher.java")));

        String projection = read("coronapoker-gdx/src/main/java/"
                + "com/tonikelope/coronapoker/gdx/GdxTableViewState.java");
        assertFalse(projection.contains("player.stack() - action."),
                "GDX must consume the dealer's post-action stack");
        assertFalse(projection.contains("player.streetBet() + action."),
                "GDX must consume the dealer's post-action street bet");
        assertFalse(projection.contains("snapshot.pot() - payout."),
                "GDX must consume the dealer's post-payout pot");
        assertFalse(projection.contains("player.stack() + transfer.amount()"),
                "GDX must consume the dealer's post-rebuy stack");
        assertFalse(projection.contains("Math.max(player.potContribution(),"
                + " transfer.amount())"),
                "GDX must not reconcile forced-bet accounting heuristically");
        assertFalse(projection.contains(
                "copyPlayer(player, player.stack(), 0d, 0d"),
                "GDX must not invent new-hand player accounting");
        assertTrue(projection.contains("snapshot = boundary.snapshot();"),
                "GDX must consume the dealer's exact hand-boundary snapshot");
        assertFalse(projection.contains("roster.snapshot()"),
                "Roster updates must not replace unrelated hand state");
        assertTrue(projection.contains("roster.players()"),
                "GDX must consume only the exact player roster payload");
        assertFalse(projection.contains(
                "copySnapshot(snapshot, TableSnapshot.Street.SHOWDOWN"),
                "GDX must consume the dealer's exact showdown street");
        assertTrue(projection.contains(
                "copySnapshot(snapshot, result.street()"),
                "GDX must project the showdown street carried by the verdict");
        assertTrue(projection.contains("close.terminalStreet()"),
                "GDX must consume the core's explicit terminal street");
        assertFalse(projection.contains("snapshot = close.snapshot();"),
                "CloseTable must not replace the last valid table projection");
        assertFalse(projection.contains(
                "action.kind() != TableVisualEvent.PlayerAction.ActionKind.FOLD"),
                "PlayerAction must not infer folded state in the renderer");
        assertFalse(projection.contains(
                "action.kind() == TableVisualEvent.PlayerAction.ActionKind.FOLD"),
                "PlayerAction must not infer card dimming in the renderer");
        assertFalse(projection.contains("movingPositions"),
                "A visual position-chip flight must not reconcile player state");
        String eventContract = Files.readString(
                reactor.resolve("coronapoker-core/src/main/java/com/tonikelope/coronapoker/"
                        + "table/TableVisualEvent.java").normalize(),
                StandardCharsets.UTF_8);
        assertFalse(eventContract.contains("record MovePosition"),
                "Dead demo-era position events must not remain in production");
    }

    @Test
    void bothLaunchersUseTheSharedBootstrap() throws IOException {
        String swingLauncher = Files.readString(
                reactor.resolve("coronapoker-swing/src/main/java/com/tonikelope/coronapoker/swing/SwingLauncher.java").normalize(),
                StandardCharsets.UTF_8);
        String gdxLauncher = read("coronapoker-gdx/src/main/java/com/tonikelope/coronapoker/gdx/GdxLauncher.java");

        assertTrue(swingLauncher.contains("CoronaPokerBootstrap.createApplication()"));
        assertTrue(gdxLauncher.contains("CoronaPokerBootstrap.createApplication()"));
        assertTrue(gdxLauncher.contains("new GdxApplicationShell"));
        assertTrue(gdxLauncher.contains("new Lwjgl3Application"));
        assertFalse(gdxLauncher.contains("CoronaPokerGdxLauncher.main(args)"));
        assertFalse(gdxLauncher.contains("new CoronaPokerGdxDemo"));
    }

    private boolean containsGraphicsImport(Path path) {
        try {
            return GRAPHICS_IMPORT.matcher(Files.readString(path, StandardCharsets.UTF_8)).find();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot inspect " + path, ex);
        }
    }

    private List<Path> coreSources() throws IOException {
        Path sourceRoot = reactor.resolve("coronapoker-core/src/main/java");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<Path> sources = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
            assertFalse(sources.isEmpty(), "No physical core sources found");
            return sources;
        }
    }

    private String read(String relative) throws IOException {
        return Files.readString(reactor.resolve(relative), StandardCharsets.UTF_8);
    }
}

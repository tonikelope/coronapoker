package com.tonikelope.coronapoker.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArchitectureBoundaryTest {

    private static final Pattern GRAPHICS_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+(?:java\\.awt|javax\\.swing|com\\.badlogic\\.gdx)(?:\\.|;)");

    private final Path reactor = Path.of(System.getProperty("migration.reactor.dir"))
            .toAbsolutePath().normalize();

    @Test
    void coreHasNoSwingAwtOrLibgdxImports() throws IOException {
        List<Path> sourceRoots = List.of(
                reactor.resolve("../src/main/java/com/tonikelope/coronapoker/core").normalize(),
                reactor.resolve("../src/main/java/com/tonikelope/coronapoker/table").normalize(),
                reactor.resolve("../src/main/java/com/tonikelope/coronapoker/bot/context").normalize());
        try (Stream<Path> files = sourceRoots.stream().flatMap(this::walk)) {
            List<Path> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::containsGraphicsImport)
                    .toList();
            assertTrue(violations.isEmpty(), "Graphics imports in core: " + violations);
        }
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
        assertTrue(corePom.contains("../../src/main/java/com/tonikelope/coronapoker/core"));
        assertTrue(corePom.contains("../../src/main/java/com/tonikelope/coronapoker/table"));
        assertTrue(swingPom.contains("com/tonikelope/coronapoker/core/**"));
        assertTrue(swingPom.contains("com/tonikelope/coronapoker/table/**"));
    }

    @Test
    void gdxModuleUsesApprovedDemoSourceWithoutCopyingIt() throws IOException {
        String gdxPom = read("coronapoker-gdx/pom.xml");
        assertTrue(gdxPom.contains("../../prototype-gdx/src/main/java"));
        assertTrue(gdxPom.contains("627c71e4f"));
        assertTrue(gdxPom.contains("com.tonikelope.coronapoker.gdx.GdxLauncher"));
        assertFalse(Files.exists(reactor.resolve(
                "coronapoker-gdx/src/main/java/com/tonikelope/coronapoker/gdxdemo/CoronaPokerGdxDemo.java")));
    }

    @Test
    void bothLaunchersUseTheSharedBootstrap() throws IOException {
        String swingLauncher = Files.readString(
                reactor.resolve("../src/main/java/com/tonikelope/coronapoker/swing/SwingLauncher.java").normalize(),
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

    private Stream<Path> walk(Path root) {
        try {
            return Files.walk(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot inspect " + root, ex);
        }
    }

    private String read(String relative) throws IOException {
        return Files.readString(reactor.resolve(relative), StandardCharsets.UTF_8);
    }
}

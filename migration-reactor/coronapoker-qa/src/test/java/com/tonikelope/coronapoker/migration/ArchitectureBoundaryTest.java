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
        Path sources = reactor.resolve("coronapoker-core/src/main/java");
        try (Stream<Path> files = Files.walk(sources)) {
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
    }

    @Test
    void gdxModuleUsesApprovedDemoSourceWithoutCopyingIt() throws IOException {
        String gdxPom = read("coronapoker-gdx/pom.xml");
        assertTrue(gdxPom.contains("../../prototype-gdx/src/main/java"));
        assertTrue(gdxPom.contains("627c71e4f"));
        assertFalse(Files.exists(reactor.resolve(
                "coronapoker-gdx/src/main/java/com/tonikelope/coronapoker/gdxdemo/CoronaPokerGdxDemo.java")));
    }

    private boolean containsGraphicsImport(Path path) {
        try {
            return GRAPHICS_IMPORT.matcher(Files.readString(path, StandardCharsets.UTF_8)).find();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot inspect " + path, ex);
        }
    }

    private String read(String relative) throws IOException {
        return Files.readString(reactor.resolve(relative), StandardCharsets.UTF_8);
    }
}

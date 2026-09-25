package com.tonikelope.coronapoker.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProductDistributionContractTest {

    private static final Pattern PROJECT_VERSION = Pattern.compile(
            "<artifactId>coronapoker-modules</artifactId>\\s*"
                    + "<version>([^<]+)</version>");

    private final Path reactor = Path.of(System.getProperty("migration.reactor.dir"))
            .toAbsolutePath().normalize();

    @Test
    void productFrontendsPublishOnlyTheCurrentVersionToTheRootTarget()
            throws IOException {
        String parent = read("pom.xml");
        String swing = read("coronapoker-swing/pom.xml");
        String gdx = read("coronapoker-gdx/pom.xml");
        String legacy = Files.readString(reactor.resolve("../pom.xml").normalize(),
                StandardCharsets.UTF_8);

        String reactorVersion = projectVersion(parent);
        String releaseVersion = reactorVersion.replaceFirst("-SNAPSHOT$", "");
        String swingJar = "CoronaPoker-" + releaseVersion + "-swing.jar";
        String gdxJar = "CoronaPoker-" + releaseVersion + "-gdx.jar";

        assertTrue(parent.contains("<distribution.directory>"
                        + "${maven.multiModuleProjectDirectory}/target"
                        + "</distribution.directory>"),
                "Product artifacts must have one canonical root target");
        assertTrue(parent.contains("<exclude>" + swingJar + "</exclude>"));
        assertTrue(parent.contains("<exclude>" + gdxJar + "</exclude>"));

        assertPublishes(swing, reactorVersion, swingJar, "swing");
        assertPublishes(gdx, reactorVersion, gdxJar, "gdx");

        assertTrue(legacy.contains("<directory>${project.basedir}/build/legacy-root"
                        + "</directory>"),
                "Legacy QA must never publish into the product target");
        assertFalse(swing.contains("CoronaPoker-24.10"));
        assertFalse(gdx.contains("CoronaPoker-24.10"));
    }

    @Test
    void swingDoesNotRecompileClassesOwnedByTheSharedCore() throws IOException {
        Path coreClasses = reactor.resolve("coronapoker-core/target/classes");
        Path swingClasses = reactor.resolve("coronapoker-swing/target/classes");

        Set<String> core = classFiles(coreClasses);
        Set<String> swing = classFiles(swingClasses);
        core.retainAll(swing);

        assertTrue(core.isEmpty(),
                "Swing must consume core classes from its dependency, not "
                        + "compile duplicate definitions: " + core);
    }

    private static void assertPublishes(String pom, String reactorVersion,
            String jarName, String frontend) {
        assertTrue(pom.contains("<version>" + reactorVersion + "</version>"),
                frontend + " must inherit the current product version");
        assertTrue(pom.contains("<outputFile>${project.build.directory}/"
                        + jarName.replace(".jar", ".staged.jar") + "</outputFile>"),
                frontend + " must shade into a module-local staging archive");
        assertTrue(pom.contains("<argument>${distribution.directory}/"
                        + jarName + "</argument>"),
                frontend + " must atomically publish the current product JAR");
    }

    private static String projectVersion(String pom) {
        Matcher matcher = PROJECT_VERSION.matcher(pom);
        assertTrue(matcher.find(), "Cannot locate reactor product version");
        return matcher.group(1).trim();
    }

    private String read(String relative) throws IOException {
        return Files.readString(reactor.resolve(relative), StandardCharsets.UTF_8);
    }

    private static Set<String> classFiles(Path root) throws IOException {
        Set<String> classes = new TreeSet<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .map(root::relativize)
                    .map(Path::toString)
                    .forEach(classes::add);
        }
        return classes;
    }
}

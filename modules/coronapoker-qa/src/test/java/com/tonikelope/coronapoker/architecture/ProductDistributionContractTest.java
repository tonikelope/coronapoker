package com.tonikelope.coronapoker.architecture;

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

    private final Path reactor = Path.of(System.getProperty("architecture.reactor.dir"))
            .toAbsolutePath().normalize();

    @Test
    void productPublishesOnlyTheCurrentVersionToTheRootTarget()
            throws IOException {
        String parent = read("pom.xml");
        String gdx = read("coronapoker-gdx/pom.xml");
        String root = Files.readString(reactor.resolve("../pom.xml").normalize(),
                StandardCharsets.UTF_8);

        String reactorVersion = projectVersion(parent);
        String productJar = "CoronaPoker_${coronapoker.version}.jar";

        assertTrue(parent.contains("<distribution.directory>"
                        + "${maven.multiModuleProjectDirectory}/target"
                        + "</distribution.directory>"),
                "Product artifacts must have one canonical root target");
        assertTrue(parent.contains("<include>CoronaPoker-*.jar</include>"),
                "Clean must remove legacy hyphenated product JARs");
        assertTrue(parent.contains("<include>CoronaPoker_*.jar</include>"),
                "Clean must remove versioned product JARs");
        assertTrue(parent.contains("<exclude>" + productJar + "</exclude>"));

        assertPublishes(gdx, reactorVersion, productJar);

        assertTrue(root.contains("<packaging>pom</packaging>"),
                "The repository root must remain an aggregator");
        assertTrue(root.contains("<module>modules</module>"),
                "The root reactor must delegate product ownership to modules");
        assertTrue(root.contains("<directory>${project.basedir}/.maven-root-build</directory>"),
                "The root clean lifecycle must not erase the product target");
        assertFalse(root.contains("maven-shade-plugin"),
                "The root aggregator must not build a third application JAR");
        assertFalse(gdx.contains("CoronaPoker-24.10"));
    }

    @Test
    void gdxDoesNotRecompileClassesOwnedByTheSharedCore() throws IOException {
        Path coreClasses = reactor.resolve("coronapoker-core/target/classes");
        Path gdxClasses = reactor.resolve("coronapoker-gdx/target/classes");

        Set<String> core = classFiles(coreClasses);
        Set<String> gdx = classFiles(gdxClasses);
        core.retainAll(gdx);

        assertTrue(core.isEmpty(),
                "GDX must consume core classes from its dependency, not "
                        + "compile duplicate definitions: " + core);
    }

    private static void assertPublishes(String pom, String reactorVersion,
            String jarName) {
        assertTrue(pom.contains("<version>" + reactorVersion + "</version>"),
                "GDX must inherit the current product version");
        assertTrue(pom.contains("<outputFile>${project.build.directory}/"
                        + jarName.replace(".jar", ".staged.jar") + "</outputFile>"),
                "GDX must shade into a module-local staging archive");
        assertTrue(pom.contains("<argument>${distribution.directory}/"
                        + jarName + "</argument>"),
                "GDX must atomically publish the current product JAR");
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

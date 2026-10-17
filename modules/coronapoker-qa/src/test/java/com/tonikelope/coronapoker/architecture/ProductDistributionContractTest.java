package com.tonikelope.coronapoker.architecture;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void runtimeIdentityMatchesTheProductVersion() throws IOException {
        String reactorVersion = projectVersion(read("pom.xml"));
        Properties metadata = new Properties();
        metadata.load(new StringReader(read(
                "coronapoker-core/src/main/resources/META-INF/"
                        + "coronapoker-version.properties")));

        assertEquals(reactorVersion, metadata.getProperty("version"),
                "The displayed runtime version must match the Maven product version");
        assertTrue(read("coronapoker-core/src/main/java/com/tonikelope/"
                + "coronapoker/core/ApplicationMetadata.java").contains(
                        "FALLBACK_VERSION = \"" + reactorVersion + "\""),
                "The fallback runtime version must match the Maven product version");
    }

    @Test
    void buildRunnerCanIncrementEveryCanonicalVersionBeforePackaging()
            throws IOException {
        String runner = Files.readString(reactor.resolve(
                "../tools/qa/run-all.ps1").normalize(),
                StandardCharsets.UTF_8);

        assertTrue(runner.contains("[switch] $IncrementVersion"));
        assertTrue(runner.contains(
                "if ($IncrementVersion -and $Action -ne 'build')"));
        assertTrue(runner.contains("Version increment: {0} -> {1}"));
        assertTrue(runner.contains("if ($minor -ge 99)"),
                "Version 25.99 must roll over to 26.0, never 25.100");
        for (String marker : Set.of(
                "pom.xml",
                "modules\\pom.xml",
                "modules\\coronapoker-assets\\pom.xml",
                "modules\\coronapoker-core\\pom.xml",
                "modules\\coronapoker-gdx\\pom.xml",
                "modules\\coronapoker-qa\\pom.xml",
                "tools\\reactor\\pom.xml",
                "tools\\qa\\pom.xml",
                "coronapoker-version.properties",
                "ApplicationMetadata.java",
                "ApplicationMetadataTest.java")) {
            assertTrue(runner.contains(marker),
                    "Build version increment must include " + marker);
        }
    }

    @Test
    void windowsInstallerPreservesThePortableUpdaterContract()
            throws IOException {
        Path repository = reactor.resolve("..").normalize();
        Path scriptPath = repository.resolve(
                "tools/windows/build-installer.ps1");
        Path releaseScriptPath = repository.resolve(
                "tools/windows/build-release-installer.ps1");
        Path iconPath = repository.resolve(
                "packaging/windows/CoronaPoker.ico");
        Path installerSpecPath = repository.resolve(
                "packaging/windows/CoronaPoker.iss");
        Path wizardImagePath = repository.resolve(
                "packaging/windows/installer-wizard.png");
        Path smallImagePath = repository.resolve(
                "packaging/windows/installer-small.png");
        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        String releaseScript = Files.readString(releaseScriptPath,
                StandardCharsets.UTF_8);
        String installerSpec = Files.readString(installerSpecPath,
                StandardCharsets.UTF_8);

        assertTrue(Files.size(iconPath) > 10_000L,
                "The Windows launcher must carry a real multi-size icon");
        assertTrue(Files.size(wizardImagePath) > 10_000L,
                "The installer needs real CoronaPoker wizard artwork");
        assertTrue(Files.size(smallImagePath) > 5_000L,
                "The installer needs a high-DPI CoronaPoker header logo");
        assertTrue(script.contains("\"CoronaPoker.jar\""),
                "The installed JAR name must remain stable for updates");
        assertTrue(script.contains("\"--runtime-image\""));
        assertTrue(script.contains(
                "The Windows installer must be built with JDK 25."));
        assertTrue(script.contains("bin\\java.exe"),
                "The private runtime must retain Java for coronaupdater.jar");
        assertFalse(script.contains("--strip-native-commands"),
                "jpackage's stripped runtime would break coronaupdater.jar");
        assertTrue(script.contains(
                "$jlinkArguments += @(\"--module-path\", $jmods)"),
                "JDKs with jmods must remain supported");
        assertTrue(script.contains(
                "$setupArtifactBase = \"${appImageArtifactBase}_Setup\""));
        assertTrue(installerSpec.contains(
                "AppId={{8E6B1337-F015-4F21-A920-73ACF3EF0A55}"),
                "Installer upgrades need one stable Windows identity");
        assertTrue(installerSpec.contains(
                "DefaultDirName={localappdata}\\Programs\\CoronaPoker"),
                "The updater needs a user-writable installation");
        assertTrue(installerSpec.contains("PrivilegesRequired=lowest"),
                "The installer must stay per-user unless firewall access is selected");
        assertTrue(installerSpec.contains("Name: \"firewall\""),
                "Firewall access must be an explicit installer task");
        assertFalse(installerSpec.contains(
                "Name: \"firewall\"; Description: \"{cm:FirewallAccess}\"; "
                        + "GroupDescription: \"{cm:NetworkAccess}\"; Flags: unchecked"),
                "The firewall task must be selected by default");
        assertTrue(installerSpec.contains(
                "Permitir CoronaPoker en el Firewall de Windows"));
        assertTrue(installerSpec.contains(
                "-Direction Inbound -Action Allow -Profile Private,Public -Program"),
                "The rule must cover the selected Windows network profile and be tied to the launcher");
        assertTrue(installerSpec.contains(
                "Get-NetFirewallApplicationFilter -Program"),
                "Installer upgrades must replace an existing rule for the launcher path");
        assertTrue(installerSpec.contains("-Protocol TCP"));
        assertFalse(installerSpec.toLowerCase(java.util.Locale.ROOT)
                .contains("localport"),
                "The installer must not freeze the configurable poker port");
        assertTrue(installerSpec.contains("CurUninstallStepChanged"));
        assertTrue(installerSpec.contains("RemoveFirewallRule()"));
        assertTrue(installerSpec.contains(
                "compiler:Languages\\Spanish.isl"));
        assertTrue(installerSpec.contains("ShowLanguageDialog=auto"));
        assertTrue(installerSpec.contains(
                "WizardImageFile=installer-wizard.png"));
        assertTrue(installerSpec.contains(
                "WizardSmallImageFile=installer-small.png"));
        assertFalse(script.contains("-Duser.home"),
                "Installer must preserve %USERPROFILE%/.coronapoker");
        assertTrue(script.contains(
                "installed CoronaPoker.jar differs from the canonical"),
                "The packaged JAR must be hash-checked before release");
        assertTrue(script.contains(
                "Updater-compatible runtime is missing module"));
        assertTrue(script.contains("Invoke-Sign $nativeLauncher"));
        assertTrue(script.contains("Invoke-Sign $finalPath"));
        assertTrue(releaseScript.contains(
                "microsoft-jdk-25-windows-x64.zip"));
        assertTrue(releaseScript.contains("Get-FileHash"),
                "The downloaded JDK must be verified before use");
        assertTrue(releaseScript.contains("finally"));
        assertTrue(releaseScript.contains(
                "Remove-Item -LiteralPath $temporaryPath -Recurse -Force"),
                "Temporary JDK and staging data must always be removed");
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

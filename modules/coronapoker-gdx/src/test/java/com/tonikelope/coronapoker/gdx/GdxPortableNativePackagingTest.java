/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

final class GdxPortableNativePackagingTest {

    private static final Set<String> MODULES = Set.of("lwjgl", "lwjgl-glfw",
            "lwjgl-jemalloc", "lwjgl-openal", "lwjgl-opengl", "lwjgl-stb");
    private static final Set<String> PLATFORMS = Set.of("natives-windows",
            "natives-windows-arm64", "natives-linux", "natives-linux-arm64",
            "natives-macos", "natives-macos-arm64");

    @Test
    void fatJarDeclaresLwjglNativesForEverySupportedDesktopTarget()
            throws Exception {
        Path pom = moduleDirectory().resolve("pom.xml");
        var document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(pom.toFile());
        NodeList dependencies = document.getElementsByTagName("dependency");
        Set<String> declared = new HashSet<>();
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            String group = text(dependency, "groupId");
            String artifact = text(dependency, "artifactId");
            String classifier = text(dependency, "classifier");
            if ("org.lwjgl".equals(group) && !classifier.isBlank()) {
                declared.add(artifact + ':' + classifier);
            }
        }

        Set<String> required = new HashSet<>();
        for (String module : MODULES) {
            for (String platform : PLATFORMS) {
                required.add(module + ':' + platform);
            }
        }
        Set<String> missing = new HashSet<>(required);
        missing.removeAll(declared);
        assertTrue(missing.isEmpty(), "Missing portable natives: " + missing);
    }

    private static String text(Element parent, String name) {
        NodeList values = parent.getElementsByTagName(name);
        return values.getLength() == 0 ? ""
                : values.item(0).getTextContent().trim();
    }

    private static Path moduleDirectory() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("src/main/java"))
                    && "coronapoker-gdx".equals(current.getFileName()
                            .toString())) {
                return current;
            }
            Path nested = current.resolve("modules/coronapoker-gdx");
            if (Files.isRegularFile(nested.resolve("pom.xml"))) return nested;
            current = current.getParent();
        }
        throw new IllegalStateException("GDX module directory not found");
    }
}

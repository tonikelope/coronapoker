/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class GdxPortableNativePackagingTest {

    private static final Set<String> MODULES = Set.of("lwjgl", "lwjgl-glfw",
            "lwjgl-jemalloc", "lwjgl-openal", "lwjgl-opengl", "lwjgl-stb");
    private static final Map<String, String> PLATFORM_PROFILES = Map.of(
            "jcef-windows-amd64", "natives-windows",
            "jcef-linux-amd64", "natives-linux",
            "jcef-linux-arm64", "natives-linux-arm64",
            "jcef-macos-amd64", "natives-macos",
            "jcef-macos-arm64", "natives-macos-arm64");

    @Test
    void eachPlatformProfileDeclaresExactlyOneLwjglNativeFamily()
            throws Exception {
        Element project = document().getDocumentElement();
        Element profiles = directChild(project, "profiles");
        assertNotNull(profiles);
        Map<String, Element> byId = new HashMap<>();
        for (Element profile : directChildren(profiles, "profile")) {
            byId.put(text(profile, "id"), profile);
        }

        for (var expected : PLATFORM_PROFILES.entrySet()) {
            Element profile = byId.get(expected.getKey());
            assertNotNull(profile, "Missing profile " + expected.getKey());
            Element properties = directChild(profile, "properties");
            assertEquals(expected.getValue(),
                    text(properties, "lwjgl.natives"));

            Set<String> nativeModules = new HashSet<>();
            Element dependencies = directChild(profile, "dependencies");
            for (Element dependency : directChildren(dependencies,
                    "dependency")) {
                if (!"org.lwjgl".equals(text(dependency, "groupId"))) {
                    continue;
                }
                assertEquals("${lwjgl.natives}",
                        text(dependency, "classifier"));
                nativeModules.add(text(dependency, "artifactId"));
            }
            assertEquals(MODULES, nativeModules,
                    "Incomplete LWJGL family in " + expected.getKey());
        }
    }

    @Test
    void baseDependenciesContainNoCrossPlatformLwjglNatives()
            throws Exception {
        Element dependencies = directChild(document().getDocumentElement(),
                "dependencies");
        for (Element dependency : directChildren(dependencies, "dependency")) {
            if ("org.lwjgl".equals(text(dependency, "groupId"))) {
                assertFalse(text(dependency, "classifier")
                        .startsWith("natives-"));
            }
        }
    }

    @Test
    void shadeFiltersRemoveForeignLibgdxDesktopBinaries()
            throws Exception {
        String pom = Files.readString(moduleDirectory().resolve("pom.xml"));
        assertTrue(pom.contains(
                "<artifact>com.badlogicgames.gdx:gdx-platform</artifact>"));
        assertTrue(pom.contains(
                "<artifact>com.badlogicgames.gdx:gdx-freetype-platform</artifact>"));
        for (int index = 1; index <= 8; index++) {
            assertTrue(pom.contains("<exclude>${gdx.native.exclude."
                    + index + "}</exclude>"));
        }
    }

    private static org.w3c.dom.Document document() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(moduleDirectory().resolve("pom.xml").toFile());
    }

    private static Set<Element> directChildren(Element parent, String name) {
        Set<Element> result = new HashSet<>();
        if (parent == null) return result;
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element
                    && name.equals(element.getTagName())) {
                result.add(element);
            }
        }
        return result;
    }

    private static Element directChild(Element parent, String name) {
        return directChildren(parent, name).stream().findFirst().orElse(null);
    }

    private static String text(Element parent, String name) {
        if (parent == null) return "";
        Element value = directChild(parent, name);
        return value == null ? "" : value.getTextContent().trim();
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

package com.tonikelope.coronapoker.core.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModMediaCatalogTest {

    @Test
    void discoversExternalAssetsWithoutCopyingThemIntoTheApplication(
            @TempDir Path installation) throws Exception {
        Path mod = Files.createDirectories(installation.resolve("mod"));
        Files.writeString(mod.resolve("mod.xml"), """
                <mod fusion_sounds="true" fusion_cinematics="false">
                  <name>Chilean MOD</name><version>1.0</version>
                  <font>corona.ttf</font>
                </mod>
                """);
        Path deck = Files.createDirectories(mod.resolve("decks/pepsiman/hq"));
        Path card = Files.writeString(deck.resolve("A_C.jpg"), "asset");
        Path font = Files.createDirectories(mod.resolve("fonts"))
                .resolve("corona.ttf");
        Files.writeString(font, "font");
        Path funny = Files.createDirectories(
                mod.resolve("sounds/joke/es/fold"));
        Files.writeString(funny.resolve("mod.wav"), "sound");

        ModMediaCatalog catalog = ModMediaCatalog.discover(installation);

        assertTrue(catalog.installed());
        assertEquals("Chilean MOD", catalog.name());
        assertEquals("1.0", catalog.version());
        assertTrue(catalog.fuseSounds());
        assertFalse(catalog.fuseCinematics());
        assertEquals(java.util.List.of("pepsiman"), catalog.decks());
        assertEquals(card, catalog.resolve("decks/pepsiman/hq/A_C.jpg")
                .orElseThrow());
        assertEquals(font, catalog.font().orElseThrow());
        Map.Entry<String, String[]> sounds = catalog.soundCategory("es",
                "fold", Map.entry("joke/es/fold/",
                        new String[]{"base.wav"}));
        assertEquals("joke/es/fold/", sounds.getKey());
        assertEquals(java.util.List.of("base.wav", "mod.wav"),
                java.util.List.of(sounds.getValue()));
        assertTrue(catalog.resolve("../outside.txt").isEmpty());
    }

    @Test
    void missingOrInvalidDescriptorKeepsTheModInactive(
            @TempDir Path installation) throws Exception {
        Files.createDirectories(installation.resolve("mod/decks/pepsiman"));
        assertFalse(ModMediaCatalog.discover(installation).installed());

        Files.writeString(installation.resolve("mod/mod.xml"),
                "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><mod>&e;</mod>");
        ModMediaCatalog rejected = ModMediaCatalog.discover(installation);
        assertFalse(rejected.installed());
        assertTrue(rejected.decks().isEmpty());
    }
}

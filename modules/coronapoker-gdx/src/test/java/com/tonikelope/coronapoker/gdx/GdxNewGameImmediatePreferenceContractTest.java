/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxNewGameImmediatePreferenceContractTest {

    @Test
    void identityAndCreateEndpointArePersistedFromEveryEditingPath()
            throws Exception {
        String source = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "GdxFrontendScreen.java"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertTrue(source.contains(
                "connection.setNickname(value);\n"
                + "                connection.persistLocalIdentity(preferences);"));
        assertTrue(source.contains(
                "connection.persistLocalIdentity(preferences);\n"
                + "        refreshSelectedAvatarTexture();"));
        assertTrue(source.contains(
                "connection.setServer(options.get(selectedOption));\n"
                + "                        connection.persistLocalHostEndpoint(preferences);"));
        assertTrue(source.contains(
                "connection.setPort(value);\n"
                + "                connection.persistLocalHostEndpoint(preferences);"));
        assertTrue(source.contains("String preferred = target.server();"));
        assertTrue(source.contains(
                "!hostAddressOptions.contains(preferred)"));
        assertTrue(source.contains(
                "hit(445f, 385f, 150f, 155f, this::selectAvatar);"));
        assertTrue(source.contains(
                "hit(315f, 422f, 130f, 135f, this::selectAvatar);"));
        assertFalse(source.contains("gameText.translate(\"gdx.change\")"));
        assertFalse(source.contains("gameText.translate(\"gdx.remove\")"));
        assertFalse(source.contains("this::resetAvatar"));
    }
}

/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxNewGameIdentityLayoutTest {

    @Test
    void createKeepsProfileCenteredAndPasswordBelowUpnp() throws Exception {
        String source = frontendSource();
        String create = section(source, "private void drawIdentityPage()",
                "private void drawJoinIdentityPage()");

        assertTrue(create.contains("panel(430f, 185f, 670f, 625f"));
        assertTrue(create.contains("panel(1130f, 185f, 725f, 625f"));
        assertTrue(create.contains("hovered(445f, 385f, 150f, 155f)"));
        assertTrue(create.contains("field(610f, 435f, 450f"));
        assertTrue(create.contains("toggle(1170f, 432f, 635f, \"UPnP\""));
        assertTrue(create.contains("field(1170f, 275f, 635f"));
        assertFalse(create.contains("field(610f, 475f, 450f"));
    }

    @Test
    void joinKeepsProfileCenteredAndPasswordBelowServerHistory()
            throws Exception {
        String source = frontendSource();
        String join = section(source, "private void drawJoinIdentityPage()",
                "/** Global presets cover");

        assertTrue(join.contains("panel(295f, 285f, 620f, 480f"));
        assertTrue(join.contains("panel(945f, 285f, 680f, 480f"));
        assertTrue(join.contains("hovered(315f, 422f, 130f, 135f)"));
        assertTrue(join.contains("field(470f, 452f, 400f"));
        int history = join.indexOf("bidirectionalChoice(985f, 445f, 595f");
        int password = join.indexOf("field(985f, 315f, 595f");
        assertTrue(history >= 0 && password > history);
        assertFalse(join.contains("field(470f, 435f, 400f"));
    }

    private static String frontendSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue(from >= 0 && to > from);
        return source.substring(from, to);
    }
}

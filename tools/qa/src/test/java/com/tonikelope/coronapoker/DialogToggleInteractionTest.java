package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class DialogToggleInteractionTest {

    private static final Pattern LABEL_ACTIVATES_TOGGLE = Pattern.compile(
            "(?s)[A-Za-z0-9_]*(?:label|icon)[A-Za-z0-9_]*\\.addMouseListener\\s*\\(.{0,800}?"
            + "[A-Za-z0-9_]*(?:checkbox|check|toggle)[A-Za-z0-9_]*\\.doClick\\s*\\(");
    private static final Pattern LABEL_HANDLER_ACTIVATES_TOGGLE = Pattern.compile(
            "(?s)(?:label|icon)[A-Za-z0-9_]*Mouse(?:Clicked|Released)\\s*\\([^)]*\\)\\s*\\{.{0,500}?"
            + "[A-Za-z0-9_]*(?:checkbox|check|toggle)[A-Za-z0-9_]*\\.doClick\\s*\\(");
    private static final Pattern TOGGLE_PRECEDES_SEPARATE_LABEL = Pattern.compile(
            "(?s)createSequentialGroup\\(\\)\\s*\\.addComponent\\("
            + "[A-Za-z0-9_]*(?:checkbox|check|toggle)[A-Za-z0-9_]*\\).{0,350}?"
            + "\\.addComponent\\([A-Za-z0-9_]*(?:label|icon)[A-Za-z0-9_]*\\)");

    @Test
    void labelsCannotActivateSettingsToggles() throws IOException {
        try (Stream<Path> paths = Files.walk(locateSourceDir())) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(LABEL_ACTIVATES_TOGGLE.matcher(source).find(),
                        file.getFileName() + " must not activate a toggle from a label listener");
                assertFalse(LABEL_HANDLER_ACTIVATES_TOGGLE.matcher(source).find(),
                        file.getFileName() + " must not retain a label handler that activates a toggle");
            }
        }
    }

    @Test
    void everySwitchImplementationUsesTheHandCursor() throws IOException {
        String source = Files.readString(locateSourceDir().resolve("SettingsUI.java"));

        assertTrue(source.contains("setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR))"),
                "ToggleSwitch must use the hand cursor");
        assertTrue(source.contains("cb.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR))"),
                "toggleized dialog checkboxes must use the hand cursor");
    }

    @Test
    void toggleStylingNeverReplacesComponentsInsideExistingLayouts() throws IOException {
        Path sourceDir = locateSourceDir();
        String settingsUi = Files.readString(sourceDir.resolve("SettingsUI.java"));
        String waitingRoom = Files.readString(sourceDir.resolve("WaitingRoomFrame.java"));

        assertFalse(settingsUi.contains("labeledToggleize"),
                "toggle styling must not replace controls inside generated layouts");
        assertTrue(waitingRoom.contains("SettingsUI.toggleize(chat_notifications);"),
                "the waiting-room checkbox must retain its original layout component");
    }

    @Test
    void switchesAreAlwaysPlacedToTheRightOfTheirLabels() throws IOException {
        Path sourceDir = locateSourceDir();

        try (Stream<Path> paths = Files.walk(sourceDir)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(TOGGLE_PRECEDES_SEPARATE_LABEL.matcher(source).find(),
                        file.getFileName() + " must place a separate label before its toggle");
            }
        }

        String settingsUi = Files.readString(sourceDir.resolve("SettingsUI.java"));
        assertOrdered(settingsUi, "row.add(left);", "row.add(right);",
                "shared aligned rows must place the control on the right");
        assertTrue(settingsUi.contains("cb.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);"),
                "legacy styled checkboxes must render their text to the left of the switch");

        assertSettingsPanelOrder(sourceDir.resolve("NewGameDialog.java"),
                "partida_panelLayout.setHorizontalGroup", "partida_panelLayout.setVerticalGroup",
                "limite_manos_label", "manos_checkbox", "think_time_label", "think_time_checkbox",
                "iwtsth_icon", "iwtsth_checkbox", "rit_icon", "rit_checkbox");
        assertSettingsPanelOrder(sourceDir.resolve("NewGameDialog.java"),
                "recompra_panelLayout.setHorizontalGroup", "recompra_panelLayout.setVerticalGroup",
                "recomprar_label", "rebuy_checkbox");
        assertSettingsPanelOrder(sourceDir.resolve("NewGameDialog.java"),
                "recover_panelLayout.setHorizontalGroup", "recover_panelLayout.setVerticalGroup",
                "recover_checkbox_label", "recover_checkbox");
        assertSettingsPanelOrder(sourceDir.resolve("GameSettingsPanel.java"),
                "rules_panelLayout.setHorizontalGroup", "rules_panelLayout.setVerticalGroup",
                "manos_label", "manos_checkbox", "think_time_label", "think_time_checkbox",
                "iwtsth_label", "iwtsth_checkbox", "rit_label", "rit_checkbox");
        assertSettingsPanelOrder(sourceDir.resolve("GameSettingsPanel.java"),
                "compra_panelLayout.setHorizontalGroup", "compra_panelLayout.setVerticalGroup",
                "recomprar_label", "rebuy_checkbox");
        assertSettingsPanelOrder(sourceDir.resolve("WaitingGameSettingsPanel.java"),
                "rules_panelLayout.setHorizontalGroup", "rules_panelLayout.setVerticalGroup",
                "manos_label", "manos_checkbox", "think_time_label", "think_time_checkbox",
                "iwtsth_label", "iwtsth_checkbox", "rit_label", "rit_checkbox");
        assertSettingsPanelOrder(sourceDir.resolve("WaitingGameSettingsPanel.java"),
                "recompra_panelLayout.setHorizontalGroup", "recompra_panelLayout.setVerticalGroup",
                "recomprar_label", "rebuy_checkbox");
    }

    private static void assertSettingsPanelOrder(Path file, String blockStart, String blockEnd,
            String... labelTogglePairs) throws IOException {
        String source = Files.readString(file);
        int start = source.indexOf(blockStart);
        int end = source.indexOf(blockEnd, start);
        assertTrue(start >= 0 && end > start, file.getFileName() + " layout block must exist");
        String horizontalLayout = source.substring(start, end);

        for (int i = 0; i < labelTogglePairs.length; i += 2) {
            assertOrdered(horizontalLayout,
                    ".addComponent(" + labelTogglePairs[i] + ")",
                    ".addComponent(" + labelTogglePairs[i + 1] + ")",
                    file.getFileName() + " must place " + labelTogglePairs[i + 1]
                    + " to the right of " + labelTogglePairs[i]);
        }
    }

    private static void assertOrdered(String source, String left, String right, String message) {
        int leftIndex = source.indexOf(left);
        int rightIndex = source.indexOf(right);
        assertTrue(leftIndex >= 0 && rightIndex > leftIndex, message);
    }

    private static Path locateSourceDir() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path p = start; p != null; p = p.getParent()) {
            Path candidate = p.resolve("src/main/java/com/tonikelope/coronapoker");
            if (Files.isDirectory(candidate) && Files.isRegularFile(candidate.resolve("SettingsUI.java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("No encuentro el fuente de CoronaPoker desde " + start);
    }
}

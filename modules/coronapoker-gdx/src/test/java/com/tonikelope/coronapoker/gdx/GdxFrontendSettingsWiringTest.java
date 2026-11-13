package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxFrontendSettingsWiringTest {

    @Test
    void newGameGameRowsKeepBalancedVerticalSpacing() {
        assertEquals(625f, GdxFrontendScreen.NEW_GAME_GAME_TOP_ROW_Y);
        assertEquals(505f, GdxFrontendScreen.NEW_GAME_GAME_SECOND_ROW_Y);
        assertEquals(345f, GdxFrontendScreen.NEW_GAME_GAME_LABELLED_ROW_Y);
        assertEquals(225f, GdxFrontendScreen.NEW_GAME_GAME_BOTTOM_ROW_Y);

        assertEquals(120f, GdxFrontendScreen.NEW_GAME_GAME_TOP_ROW_Y
                - GdxFrontendScreen.NEW_GAME_GAME_SECOND_ROW_Y);
        assertEquals(120f, GdxFrontendScreen.NEW_GAME_GAME_LABELLED_ROW_Y
                - GdxFrontendScreen.NEW_GAME_GAME_BOTTOM_ROW_Y);
        assertEquals(40f, GdxFrontendScreen.NEW_GAME_GAME_BOTTOM_ROW_Y - 185f);
    }

    @Test
    void onlyChangedHostTableSettingsRequireALobbyCommand() {
        NewGameTableDraft table = new NewGameTableDraft();
        NewGameTableDraft.Settings original = table.snapshot();
        table.setThinkSeconds(original.thinkSeconds() + 1);
        NewGameTableDraft.Settings changed = table.snapshot();

        assertTrue(GdxFrontendScreen.shouldSubmitLobbyTableSettings(
                snapshot(true, original), changed));
        assertFalse(GdxFrontendScreen.shouldSubmitLobbyTableSettings(
                snapshot(false, original), changed));
        assertFalse(GdxFrontendScreen.shouldSubmitLobbyTableSettings(
                snapshot(true, original), original));
    }

    @Test
    void globalFpsOverlayDrawsItsGlyphsInTheImmediateTopLayerPass()
            throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int start = source.indexOf("private void drawFrontendFpsCounter()");
        int end = source.indexOf("private void drawFittedCenteredInBox(",
                start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);
        assertTrue(method.contains("GdxFrameRateOverlayStyle.draw("),
                "every screen must use the same complete FPS renderer");
        assertFalse(method.contains("y + 29f"),
                "vertical centering must not rely on a fixed baseline offset");
        assertFalse(method.contains("shapes.rect("),
                "the FPS overlay must not draw the old cyan underline");
    }

    @Test
    void voiceRetentionUsesTheSharedDropdownOnBothSettingsSurfaces()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        assertTrue(frontend.contains(
                "settingsDropdownChoice(retention.x, retention.y"));
        assertTrue(frontend.contains("Dropdown.VOICE_RETENTION"));
        assertTrue(table.contains(
                "SettingsSelectionList.VOICE_RETENTION"));
        assertTrue(table.contains(
                "drawSettingsDropdownShape(retention.x, retention.y"));
        assertTrue(table.contains(
                "drawSettingsChoiceText(retention.x, retention.y"));
    }

    @Test
    void graphicsVsyncUsesTheSharedValueAndPerformanceContract()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        assertTrue(frontend.contains("GdxSettingsContract.vsyncStatusLabel("));
        assertTrue(frontend.contains("\"gdx_vsync\", \"gdx_fps_limit\""));
        assertTrue(frontend.contains("settingsTooltip(bounds, keys[row])"));
        assertTrue(table.contains(".vsyncStatusLabel("));
        assertTrue(table.contains("\"gdx_vsync\""));
        assertTrue(table.contains("\"gdx_fps_limit\""));
        assertTrue(table.contains("GdxSettingsTooltips.content("));
        assertTrue(table.contains(
                "GdxFrameRateOverlayStyle.draw(shapes, batch, fpsFont"));
    }

    @Test
    void rootSettingsDrawTheirTooltipLayerAboveTheModal() throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);

        String render = methodSection(frontend, "public void render()");
        int popup = render.indexOf("drawDropdownTopLayer();");
        int topLayers = render.indexOf("drawFrontendTopLayers();");
        int tooltip = render.indexOf("drawTooltipTopLayer(");
        assertTrue(popup >= 0 && topLayers > popup && tooltip > topLayers);
        assertEquals(1, occurrences(render, "drawTooltipTopLayer("));
        assertTrue(frontend.contains("case SETTINGS -> drawSettingsScreen();"),
                "menu and lobby settings must share the same sharp modal");
        assertTrue(frontend.contains(
                "settingsReturnSurface == Surface.LOBBY && lobby != null"),
                "only the blurred backdrop may differ between menu and lobby");
    }

    @Test
    void rootSettingsOwnTheirWheelAndScrollbarDespiteProtectingTheBackdrop()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int wheel = frontend.indexOf(
                "public boolean scrolled(float amountX, float amountY)");
        int settingsWheel = frontend.indexOf(
                "if (surface == Surface.SETTINGS && amountY != 0f", wheel);
        int modalShield = frontend.indexOf(
                "if (childModalOwnsInput() && dropdown == Dropdown.NONE)",
                wheel);

        assertTrue(wheel >= 0 && settingsWheel > wheel);
        assertTrue(settingsWheel < modalShield,
                "settings must route their wheel before the root-modal shield");
        assertTrue(frontend.contains(
                "boolean rootSurfaceOwnsPointer = isFrontendRootModal();"));
        assertTrue(frontend.contains(
                "(!blockingModal || rootSurfaceOwnsPointer"));
        assertTrue(frontend.contains("currentSettingsPixelRows()"),
                "wheel limits must come from the current subsection rows");
    }

    @Test
    void menuLobbyAndLiveSettingsConsumeTheSameVisualContracts()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        assertTrue(frontend.contains(
                "GdxSettingsLayout.contentClip(content, settingsRowsClip)"));
        assertTrue(table.contains(
                "GdxSettingsLayout.contentClip(content)"));
        assertTrue(frontend.contains(
                "GdxSettingsContract.masterVolumeRow(page)"));
        assertTrue(table.contains(
                "GdxSettingsContract.masterVolumeRow(page)"));
        assertTrue(frontend.contains("GdxSettingsLayout.splitActionRow("));
        assertTrue(table.contains("GdxSettingsLayout.splitActionRow("));
        assertTrue(frontend.contains("GdxSettingsStyle.drawShortcutRow("));
        assertTrue(table.contains("GdxSettingsStyle.drawShortcutRow("));
        assertTrue(frontend.contains("GdxSettingsStyle.drawRowScrollbar("));
        assertTrue(table.contains("GdxSettingsStyle.drawRowScrollbar("));
        assertTrue(frontend.contains("textFit(smallFont, key,"));
        assertTrue(table.contains(
                "drawFittedCentered(settingsSmallFont, key,"),
                "live shortcut keys must not silently grow beyond the menu");
        assertTrue(frontend.contains(
                "GdxSettingsStyle.shortcutKeyText(capturing)"));
        assertTrue(table.contains(
                "GdxSettingsStyle.shortcutKeyText(capturing)"));
        assertTrue(table.contains(
                "drawFittedLeft(settingsTitleFont,"),
                "the live settings title must retain the master font path");
        String normalizedFrontend = frontend.replaceAll("\\s+", " ");
        String normalizedTable = table.replaceAll("\\s+", " ");
        assertTrue(normalizedFrontend.contains(
                "GdxSettingsStyle.ACTION_FONT_SIZE, "
                + "GdxSettingsStyle.ACTION_FONT_BORDER, border, characters, "
                + "font -> actionFont = replaceFont(actionFont, font)"));
        assertTrue(normalizedTable.contains(
                "settingsActionFont = font(creationFontGenerator, "
                + "GdxSettingsStyle.ACTION_FONT_SIZE,"));
        assertFalse(table.contains(
                "capturing ? settingsSmallFont\n                : settingsActionFont"));
    }

    @Test
    void liveAudioUsesCanonicalGroupsInEveryRenderingPass()
            throws Exception {
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);
        String shapes = methodSection(table,
                "private void drawSettingsContentShapes(");
        String text = methodSection(table,
                "private void drawSettingsContentText(");

        assertTrue(shapes.contains("GdxSettingsContract.audioGroups(page)"));
        assertTrue(text.contains("GdxSettingsContract.audioGroups(page)"));
        assertFalse(shapes.contains("GdxSettingsContract.toggleGroups(page"));
        assertFalse(text.contains("GdxSettingsContract.toggleGroups(page"));
    }

    @Test
    void liveManagerActionsKeepSharedTypographyAndAuthorityColors()
            throws Exception {
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);
        String shapes = methodSection(table,
                "private void drawLiveSettingsActionShape(");
        String text = methodSection(table,
                "private void drawLiveSettingsActionText(");

        assertTrue(table.contains("drawLiveSettingsActionShape(manage.x"));
        assertTrue(table.contains("drawLiveSettingsActionText(manage.x"));
        assertTrue(shapes.contains("GdxUiButtonStyle.Tone.NEUTRAL, enabled"));
        assertTrue(shapes.contains("GdxSettingsStyle.drawServerOnlyRow"));
        assertTrue(shapes.contains("GdxSettingsStyle.drawLockedRow"));
        assertTrue(text.contains(
                "drawDialogButtonLabel(settingsActionFont"));
        assertTrue(text.contains("GdxSettingsStyle.serverOnlyText()"));
        assertTrue(text.contains("GdxSettingsStyle.lockedText()"));
        assertFalse(text.contains(
                "drawDialogButtonLabel(settingsBodyFont"));
    }

    @Test
    void liveBlindStructureDefaultUsesTheSharedTranslatedValue() throws Exception {
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        assertTrue(table.contains(
                "gameText.translate(\"gdx.settings.value.default\")"));
        assertFalse(table.contains("settingsGameText(\"value.default\")"));
    }

    @Test
    void graphicsParentGroupDrivesPaintInputTooltipsAndScrollingEverywhere()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        assertTrue(frontend.contains(
                "GdxSettingsContract.appearanceGraphicsGroups()"));
        assertTrue(frontend.contains(
                "GdxSettingsLayout.drawGroups(shapes, baseX, baseWidth"));
        String scrollMethod = methodSection(frontend,
                "private GdxSettingsLayout.PixelRows currentSettingsPixelRows()");
        assertTrue(scrollMethod.contains("APPEARANCE_GRAPHICS_ROW_COUNT"));
        assertTrue(scrollMethod.contains("appearanceGraphicsGroups()"));
        assertTrue(table.contains(
                "GdxSettingsContract.appearanceGraphicsGroups()"));
        assertTrue(table.contains(
                "settingsAppearanceGraphicsRows(content, firstRowY"));
        assertTrue(occurrences(table,
                "settingsAppearanceGraphicsRowBounds(") >= 5,
                "live settings must share the grouped geometry in input, "
                + "tooltips, shape and text passes");
        String tableWheel = methodSection(table,
                "public boolean scrolled(float amountX, float amountY)");
        assertTrue(tableWheel.contains("scrollActiveSettingsRows(amountY)"),
                "every live settings page must use the active row geometry");
        assertFalse(tableWheel.contains(
                "!GdxSettingsContract.isAppearanceGraphicsPage"),
                "Graphics must not be excluded from wheel scrolling");
        assertFalse(tableWheel.contains("settingsContentPage() == 7"),
                "Game wheel scrolling must not be limited to one subpage");
        String activeRows = methodSection(table,
                "private boolean scrollActiveSettingsRows(float amountY)");
        assertTrue(activeRows.contains("settingsRowScroll()"));
        assertTrue(activeRows.contains("settingsRowScrollValue()"));
        assertTrue(activeRows.contains("setSettingsRowScroll("));
        assertFalse(frontend.contains("Rectangle autoFullscreenRow"));
        assertFalse(table.contains("Rectangle autoFullscreenRow"));
    }

    @Test
    void everyProductScrollbarUsesSharedClickAndContinuousDragGeometry()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        String[] frontendBars = {"DROPDOWN", "LOBBY_CHAT", "SETTINGS_ROWS",
            "SETTINGS_DEBUG", "STATS_PICKER", "STATS_EVIDENCE",
            "STATS_RESULTS", "STATS_SUMMARY_PLAYERS",
            "STATS_CHART_HORIZONTAL", "STATS_CHART_VERTICAL",
            "NETWORK_BLOCKS", "CUSTOM_BOTS"};
        for (String bar : frontendBars) {
            assertTrue(occurrences(frontend, "ScrollDrag." + bar) >= 3,
                    bar + " must support capture, continuous drag and apply");
        }

        String tableGeometry = methodSection(table,
                "private TableScrollGeometry tableScrollGeometry(");
        String tableValue = methodSection(table,
                "private float tableScrollValue(TableScrollDrag type)");
        String tableApply = methodSection(table,
                "private void applyTableScrollRatio(float ratio, float maximum)");
        String[] tableBars = {"GAME_LOG", "SETTINGS_ROWS",
            "SETTINGS_SELECTION", "SETTINGS_DEBUG", "QUICK_CHAT",
            "NETWORK_BLOCKS"};
        for (String bar : tableBars) {
            String route = "case " + bar + " ->";
            assertTrue(tableGeometry.contains(route),
                    bar + " must expose exact visual track geometry");
            assertTrue(tableValue.contains(route),
                    bar + " must expose its current value");
            assertTrue(tableApply.contains(route),
                    bar + " must apply continuous pointer movement");
        }

        assertTrue(frontend.contains(
                "GdxSettingsLayout.verticalScrollbarContains("));
        assertTrue(frontend.contains(
                "GdxSettingsLayout.horizontalScrollbarContains("));
        assertTrue(table.contains(
                "GdxSettingsLayout.verticalScrollbarContains("));
        assertTrue(frontend.contains("ScrollDrag.DROPDOWN"),
                "frontend popup lists must drag, not only jump on click");
        assertTrue(table.contains("TableScrollDrag.SETTINGS_SELECTION"),
                "live popup lists must drag, not only jump on click");
    }

    @Test
    void everyFrontendSettingsRowUsesTheCompleteVolumetricMaterial()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);

        assertFalse(frontend.contains("GdxSettingsStyle.rowBorder("),
                "settings rows must not bypass the shared bevel renderer");
        assertFalse(frontend.contains("GdxSettingsStyle.rowFill("),
                "settings rows must not rebuild only the flat fill");
        assertTrue(frontend.split("GdxSettingsStyle\\.drawRow\\(shapes", -1)
                .length - 1 >= 8,
                "selectors, steppers and information rows must use the "
                + "same complete material as toggles");
    }

    @Test
    void menuAndLobbySettingsFooterHitsAreOutsideTheScrollableClip()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int start = frontend.indexOf("private void drawSettingsScreen()");
        int end = frontend.indexOf("private void drawAudioSettings(", start);
        assertTrue(start >= 0 && end > start);
        String method = frontend.substring(start, end);

        int rowsEnd = method.indexOf("settingsRowsActive = false;");
        int cancel = method.indexOf("Rectangle cancel = frame.cancelButton()");
        int save = method.indexOf("Rectangle save = frame.saveButton()");
        assertTrue(rowsEnd >= 0 && cancel > rowsEnd && save > cancel,
                "waiting-room footer hits must be registered after clipped "
                + "row input has ended");
        assertTrue(method.indexOf("this::requestCancelSettings", cancel)
                > cancel);
        assertTrue(method.indexOf("if (saveEnabled)", save) > save);
        assertTrue(method.indexOf("() -> closeSettings(true)", save) > save);
    }

    @Test
    void botSettingsRowsUseOneBalancedVerticalRhythm() throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int start = frontend.indexOf("private void drawBotsPage()");
        int end = frontend.indexOf("private void nextBotDifficulty()", start);
        assertTrue(start >= 0 && end > start);
        String method = frontend.substring(start, end);

        assertTrue(method.contains("float botOptionTopY = 505f;"));
        assertTrue(method.contains("float botOptionStride = 97f;"));
        assertTrue(method.contains(
                "float customBotsOnlyY = botOptionTopY - botOptionStride;"));
        assertTrue(method.contains(
                "float botRebuyY = botOptionTopY - botOptionStride * 2f;"));
        assertTrue(method.contains(
                "float botBalanceY = botOptionTopY - botOptionStride * 3f;"));
    }

    @Test
    void gameProfileUsesTheSameCompactPanelWidthAsBots() throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int profileStart = frontend.indexOf("private void drawProfilePage()");
        int profileEnd = frontend.indexOf(
                "private String selectedPresetLabel()", profileStart);
        int botsStart = frontend.indexOf("private void drawBotsPage()");
        int botsEnd = frontend.indexOf("private void nextBotDifficulty()",
                botsStart);
        assertTrue(profileStart >= 0 && profileEnd > profileStart);
        assertTrue(botsStart >= 0 && botsEnd > botsStart);
        String profile = frontend.substring(profileStart, profileEnd);
        String bots = frontend.substring(botsStart, botsEnd);

        String compactPanel = "panel(520f, 185f, 1230f, 625f,";
        assertTrue(profile.contains(compactPanel));
        assertTrue(bots.contains(compactPanel));
        assertTrue(profile.contains("dropdownChoice(580f, 500f, 1110f"));
        assertTrue(profile.contains("button(580f, 350f, 530f, 70f"));
        assertTrue(profile.contains("button(1160f, 350f, 530f, 70f"));
    }

    @Test
    void blindsPageUsesOneWideStructureActionAndUnifiedLabels()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int start = frontend.indexOf("private void drawBlindsPage()");
        int end = frontend.indexOf("private String blindLevelLabel(", start);
        assertTrue(start >= 0 && end > start);
        String method = frontend.substring(start, end);

        assertTrue(method.contains("panel(leftPanelX, panelY, panelWidth, panelHeight,"));
        assertTrue(method.contains("panel(rightPanelX, panelY, panelWidth, panelHeight,"));
        assertTrue(method.contains("button(leftX, 435f, rowWidth, 70f,"));
        int antePanel = method.indexOf("plainPanel(leftX, modeY - 8f, rowWidth,");
        int anteToggle = method.indexOf("toggle(anteX, anteY, anteWidth,");
        int anteList = method.indexOf("settingsDropdownChoice(anteX, modeY, anteWidth, rowHeight,");
        assertTrue(method.contains("toggle(leftX, straddleY, rowWidth,"));
        assertTrue(antePanel >= 0 && antePanel < anteToggle && anteToggle < anteList);
        assertTrue(method.contains("openDropdown(Dropdown.ANTE_MODE"));
        assertTrue(method.contains("openDropdown(Dropdown.BLIND_LEVEL"));
        assertTrue(method.contains(
                "settingsGameText(\"row.manage_structures\")"));
        assertFalse(method.contains("row.default_structure"));
        assertTrue(method.contains(
                "settingsGameText(\"row.increase_blinds\")"));
    }

    @Test
    void discreteChoicesUseScrollableSelectionListsAcrossAllSurfaces()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        assertTrue(frontend.contains("Dropdown.BLIND_STRUCTURE, enabled"));
        assertTrue(frontend.contains("Dropdown.BLIND_LEVEL, enabled"));
        assertTrue(frontend.contains("Dropdown.BLIND_STRUCTURE_EDITOR"));
        assertTrue(frontend.contains("Dropdown.DECK, true"));
        assertTrue(frontend.contains("Dropdown.FELT, true"));
        assertTrue(frontend.contains("Dropdown.SERVER_HISTORY"));
        assertTrue(frontend.contains("Dropdown.LANGUAGE"));
        assertTrue(frontend.contains("Dropdown.AUDIO_OUTPUT"));
        assertTrue(frontend.contains("Dropdown.AUDIO_CAPTURE"));
        assertTrue(frontend.contains("Dropdown.WINDOW_MODE"));
        assertTrue(frontend.contains("Dropdown.FPS_LIMIT"));
        assertTrue(frontend.contains("Dropdown.MSAA"));
        assertTrue(frontend.contains("Dropdown.CARD_RENDERING"));
        assertTrue(frontend.contains("Dropdown.APPEARANCE_CHOICE"));
        assertTrue(frontend.contains("Dropdown.BLIND_UNIT"));
        assertTrue(frontend.contains("Dropdown.REBUY_CAP"));
        assertTrue(frontend.contains("Dropdown.BOT_DIFFICULTY"));
        assertTrue(frontend.contains("Dropdown.RABBIT"));
        assertFalse(frontend.contains("adjustSettingsBlindStructure("));
        assertFalse(frontend.contains("selectNextDeck"));
        assertFalse(frontend.contains("selectPreviousFelt"));
        assertFalse(frontend.contains("selectNextFrameRateLimit"));
        assertFalse(frontend.contains("selectPreviousMsaa"));
        assertTrue(frontend.contains("float thumbH = Math.max(34f,"));
        assertTrue(frontend.contains(
                "GdxSettingsLayout.POPUP_MAX_VISIBLE_ROWS"));
        assertTrue(table.contains(
                "SettingsSelectionList.BLIND_STRUCTURE"));
        assertTrue(table.contains("SettingsSelectionList.DECK"));
        assertTrue(table.contains("SettingsSelectionList.CARD_BACK"));
        assertTrue(table.contains("SettingsSelectionList.FELT"));
        assertTrue(table.contains("SettingsSelectionList.AUDIO_OUTPUT"));
        assertTrue(table.contains("SettingsSelectionList.AUDIO_CAPTURE"));
        assertTrue(table.contains("SettingsSelectionList.WINDOW_MODE"));
        assertTrue(table.contains("SettingsSelectionList.FPS_LIMIT"));
        assertTrue(table.contains("SettingsSelectionList.MSAA"));
        assertTrue(table.contains("SettingsSelectionList.CARD_RENDERING"));
        assertTrue(table.contains("SettingsSelectionList.APPEARANCE_CHOICE"));
        assertTrue(table.contains("SettingsSelectionList.BLIND_UNIT"));
        assertTrue(table.contains("SettingsSelectionList.BOT_DIFFICULTY"));
        assertTrue(table.contains("SettingsSelectionList.RABBIT"));
        assertTrue(table.contains("drawSettingsSelectionList(alpha)"));
        assertTrue(table.contains(
                "GdxSettingsLayout.POPUP_MAX_VISIBLE_ROWS"));
        assertTrue(frontend.contains("drawDropdownOverlay(false)"));
        assertTrue(frontend.contains("drawDropdownOverlay(true)"));
        int pageText = frontend.indexOf("for (TextItem item : texts)");
        int popupTopLayer = frontend.indexOf("drawDropdownTopLayer();",
                pageText);
        assertTrue(pageText >= 0 && popupTopLayer > pageText,
                "popup lists must be composited after covered page text");
        assertTrue(frontend.contains("GdxSettingsLayout.popupListTop("));
        assertTrue(table.contains("GdxSettingsLayout.popupListTop("));
        assertTrue(frontend.contains(
                "boolean shieldDropdownPointer = dropdown != Dropdown.NONE"));
        assertTrue(table.contains(
                "settingsSelectionList != SettingsSelectionList.NONE"));
        assertTrue(frontend.contains(
                "pointer.set(dropdownPointerX, dropdownPointerY)"));
        assertTrue(table.contains(
                "pointer.set(selectionPointerX, selectionPointerY)"));
        assertFalse(table.contains("adjustTableDeck(int direction)"));
        assertFalse(table.contains("adjustTableFelt(int direction)"));
    }

    @Test
    void dropdownsRemainSharpPopupsInsteadOfBlurredChildModals()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        String visibleModal = methodSection(frontend,
                "private boolean hasVisibleFrontendModal()");
        String rootModal = methodSection(frontend,
                "private boolean isFrontendRootModal()");

        assertFalse(visibleModal.contains("dropdown != Dropdown.NONE"),
                "a dropdown must not request another blurred modal level");
        assertFalse(rootModal.contains("dropdown == Dropdown.NONE"),
                "opening a dropdown must preserve its sharp root surface");
        String normalized = frontend.replaceAll("\\s+", " ");
        assertEquals(1, occurrences(frontend, "drawDropdownTopLayer();"));
        int popup = frontend.indexOf("drawDropdownTopLayer();");
        int editMenu = frontend.indexOf("if (shouldDrawEditMenu())", popup);
        assertTrue(popup >= 0 && editMenu > popup);
        assertEquals(1, occurrences(frontend.substring(popup, editMenu), "}"),
                "main-menu dropdowns must share the global sharp popup pass");
        assertTrue(normalized.contains(
                "&& dropdown == Dropdown.NONE && (!blockingModal"),
                "covered scrollbars must not start a drag through the popup");
    }

    @Test
    void selectionListScrollbarsMapTopAndBottomConsistently() {
        assertEquals(0, GdxFrontendScreen.dropdownScrollFromTrack(
                280f, 0f, 300f, 40f, 12));
        assertEquals(12, GdxFrontendScreen.dropdownScrollFromTrack(
                20f, 0f, 300f, 40f, 12));
        assertEquals(0, CoronaPokerGdxTable.selectionScrollFromTrack(
                280f, 0f, 300f, 40f, 12));
        assertEquals(12, CoronaPokerGdxTable.selectionScrollFromTrack(
                20f, 0f, 300f, 40f, 12));
    }

    @Test
    void menuLobbyAndLiveControlsShareTheSameSixActionRows()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        assertEquals(6, GdxSettingsContract.CONTROL_ROWS);
        String normalizedFrontend = frontend.replaceAll("\\s+", " ");
        String normalizedTable = table.replaceAll("\\s+", " ");
        assertTrue(normalizedFrontend.contains(
                "GdxSettingsContract.CONTROL_ROWS + entries.size()"));
        assertTrue(normalizedTable.contains(
                "GdxSettingsContract.CONTROL_ROWS + entries.size()"));
        for (String key : List.of("auto_action_buttons",
                "auto_call_enabled", "auto_call_max",
                "auto_action_persist", "modo_auto_confirm",
                "confirmar_todo")) {
            assertTrue(frontend.contains("\"" + key + "\""), key);
            assertTrue(table.contains("\"" + key + "\""), key);
            assertTrue(GdxSettingsContract.PREFERENCE_KEYS.contains(key),
                    key + " must participate in Cancel/Save transactions");
        }
        assertTrue(frontend.contains(
                "presentationSettings.autoRebuyOnBroke()"));
        assertTrue(table.contains("presentationSettings.autoRebuyOnBroke()"));
    }

    @Test
    void settingsFeedbackUsesTransientNotificationsInEverySettingsSurface()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        for (String source : List.of(frontend, table)) {
            assertTrue(source.contains(
                    "showSettingsDefaultsRestoredToast();"));
            assertTrue(source.contains(
                    "\"gdx.settings.defaults_restored\""));
            assertTrue(source.contains("showShortcutToast(\"updated\")"));
            assertTrue(source.contains("showShortcutToast(\"conflict\")"));
            assertTrue(source.contains("showShortcutToast(\"unsupported\")"));
            assertFalse(source.contains("showShortcutToast(\"restored\")"),
                    "restoring any settings section must use the shared notice");
            assertFalse(source.contains("shortcutStatus ="),
                    "shortcut results must never remain embedded in settings");
        }
        assertTrue(frontend.contains("showToast(gameText.translate("));
        assertTrue(table.contains("postTableNotice(GdxTableChatFeed.Kind.SUCCESS"));
        assertTrue(table.contains("chatFeed.add(GdxTableChatFeed.Line.notice("));
    }

    @Test
    void restoringAnySupportedSettingsSectionQueuesOneGlobalNotice()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        String frontendRestore = methodSection(frontend,
                "private void restoreCurrentFrontendSettingsSectionDefaults()");
        assertTrue(frontendRestore.contains(
                "case APPEARANCE -> restoreFrontendAppearanceDefaults();"));
        assertTrue(frontendRestore.contains(
                "case AUDIO -> restoreFrontendAudioDefaults();"));
        assertTrue(frontendRestore.contains(
                "case SHORTCUTS ->"));
        assertEquals(1, occurrences(frontendRestore,
                "showSettingsDefaultsRestoredToast();"));

        String tableRestore = methodSection(table,
                "private void restoreCurrentSettingsSectionDefaults()");
        assertTrue(tableRestore.contains("restoreTableAppearanceDefaults();"));
        assertTrue(tableRestore.contains("restoreTableAudioDefaults();"));
        assertTrue(tableRestore.contains("shortcutBindings.resetAllEdits();"));
        assertEquals(1, occurrences(tableRestore,
                "showSettingsDefaultsRestoredToast();"));

        int modalComposition = frontend.indexOf(
                "if (hasVisibleFrontendModal()) {");
        int finalTopLayers = frontend.indexOf("drawFrontendTopLayers();",
                modalComposition);
        assertTrue(modalComposition >= 0 && finalTopLayers > modalComposition,
                "global notices must be composited after the settings modal");
    }

    @Test
    void frontendNotificationsKeepOneGlobalPlacement() throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int start = frontend.indexOf("private void drawToastTopLayer()");
        int end = frontend.indexOf("private void drawScreenshotToastTopLayer()",
                start);
        assertTrue(start >= 0 && end > start);
        String method = frontend.substring(start, end);

        assertTrue(method.contains("GdxTransientNoticeLayout.drawCentered("));
        assertFalse(method.contains("customBotManagerOpen"),
                "dialogs must not move the global notification layer");
        assertTrue(frontend.contains(
                "showToast(gameText.translate(\"gdx.custom_bots.saved\"))"),
                "bot-manager feedback must use the global notification");
    }

    @Test
    void iconButtonsHaveNoDecorativeSeparatorAndUseVectorKeyIcons()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int start = frontend.indexOf("private void drawButtonIconColumn(");
        int end = frontend.indexOf("private void drawMainMenuIcon(", start);
        assertTrue(start >= 0 && end > start);
        String iconColumn = frontend.substring(start, end);
        assertFalse(iconColumn.contains("shapes.rect("),
                "the old vertical separator must not return");
        assertTrue(frontend.contains("GdxUiIconRenderer.Icon.SETTINGS"));
        assertTrue(frontend.contains("GdxUiIconRenderer.Icon.EXIT"));
        assertTrue(frontend.contains("GdxUiIconRenderer.Icon.CAMERA"));
        for (String icon : List.of("ADD", "PLAYERS", "STATISTICS",
                "SETTINGS", "INFO", "EXIT", "CAMERA")) {
            assertTrue(frontend.contains("GdxUiIconRenderer.Icon." + icon),
                    icon + " must use the shared resolution-independent set");
        }
    }

    @Test
    void mainMenuLanguageUsesTheSameGlassMaterialAsItsPeers()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int menuStart = frontend.indexOf("private void drawMainMenu()");
        int menuEnd = frontend.indexOf("private void drawMainMenuQuote(",
                menuStart);
        int choiceStart = frontend.indexOf("private void mainMenuChoice(");
        int choiceEnd = frontend.indexOf("private void iconButton(",
                choiceStart);
        assertTrue(menuStart >= 0 && menuEnd > menuStart);
        assertTrue(choiceStart >= 0 && choiceEnd > choiceStart);
        String menu = frontend.substring(menuStart, menuEnd);
        String languageChoice = frontend.substring(choiceStart, choiceEnd);

        assertTrue(menu.contains("mainMenuChoice(975f, 310f, 350f, 82f"));
        assertFalse(menu.contains("choice(975f, 310f, 350f, 82f"));
        assertTrue(languageChoice.contains("themedButton("));
        assertTrue(languageChoice.contains("ButtonTone.NEUTRAL"));
    }

    @Test
    void fittedTextSelectsNativeTiersWithoutScalingRasterGlyphs()
            throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"));
        assertTrue(source.contains(
                "GdxCrispButtonText.resolveWithinContentWidth("));
        assertFalse(source.contains("GdxPixelText.applyScale"));
        assertFalse(source.contains("setTransformMatrix(italicTransform)"));
    }

    @Test
    void finalSummaryUsesStackInBothLanguages() {
        assertEquals("STACK", new GdxGameText("es")
                .translate("balance.fichas"));
        assertEquals("STACK", new GdxGameText("en")
                .translate("balance.fichas"));
    }

    @Test
    void compactSummaryAndQuickAccessIconsRemainResolutionIndependent()
            throws Exception {
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);
        int summaryStart = table.indexOf("private void drawFinalSummary()");
        int summaryEnd = table.indexOf("private void drawFinalExitPending(",
                summaryStart);
        assertTrue(summaryStart >= 0 && summaryEnd > summaryStart);
        String summary = table.substring(summaryStart, summaryEnd);

        assertTrue(summary.contains("GdxUiIconRenderer.draw(shapes,"));
        assertTrue(summary.contains("FINAL_NAV_ICONS[index]"));
        assertTrue(summary.contains("GdxUiIconRenderer.Icon.CAMERA"));
        assertTrue(summary.contains("drawFinalNavigationLabel("),
                "final navigation must not apply button padding twice");
        assertTrue(table.contains("action == FastAccessAction.SETTINGS"));
        assertFalse(table.contains("private Texture finalLogIcon;"),
                "a 24 px log PNG must never be enlarged in the final header");
        assertFalse(table.contains("private Texture finalScreenshotIcon;"));
        assertTrue(table.contains("GdxUiIconRenderer.Icon.SETTINGS"),
                "the 24 px quick-settings PNG also needs vector geometry");

        String iconRenderer = Files.readString(Path.of("src/main/java/com/"
                + "tonikelope/coronapoker/gdx/GdxUiIconRenderer.java"),
                StandardCharsets.UTF_8);
        assertTrue(iconRenderer.contains("drawGearTooth("),
                "settings must use a real vector cog silhouette");
        assertTrue(iconRenderer.contains(
                "shapes.triangle(previousOuterX, previousOuterY"),
                "rings must be continuous filled meshes without cap seams");
        int ringStart = iconRenderer.indexOf("private static void ring(");
        int ringEnd = iconRenderer.indexOf("private static void roundedRect(",
                ringStart);
        assertTrue(ringStart >= 0 && ringEnd > ringStart);
        assertFalse(iconRenderer.substring(ringStart, ringEnd)
                .contains("rectLine("),
                "annuli must not expose overlapping line caps");
        assertFalse(iconRenderer.contains("MathUtils.random"),
                "toolbar artwork must stay deterministic between frames");
    }

    @Test
    void tableTimeoutWarningUsesHighResolutionArtwork() throws Exception {
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        assertTrue(table.contains(
                "timeoutIcon = texture(\"images/action/timeout.png\")"));
        assertFalse(table.contains(
                "timeoutIcon = texture(\"images/menu/timeout.png\")"));
    }

    @Test
    void communityHudFitsBothDynamicStatusLabelsInsideTheirBoxes()
            throws Exception {
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);
        int start = table.indexOf("private void drawCommunityHud(");
        int end = table.indexOf("static String communityBlindsText(", start);
        assertTrue(start >= 0 && end > start);
        String method = table.substring(start, end);
        assertTrue(method.contains(
                "drawFittedMoneyInBox(actionFont, blinds,"));
        assertTrue(method.contains(
                "drawFittedCenteredInBox(actionFont, hand,"));
        assertFalse(method.contains("float iconX = communityPauseX"),
                "The compact pause control must remain text-only");
        assertTrue(method.contains(
                "communityPauseX + 8f, communityPauseY + 5f"),
                "The pause label must use the space freed by its old icon");
    }

    private static String methodSection(String source, String signature) {
        int start = source.indexOf(signature);
        int end = source.indexOf("\n    private ", start + signature.length());
        assertTrue(start >= 0 && end > start,
                "method boundary not found for " + signature);
        return source.substring(start, end);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static LobbySnapshot snapshot(boolean host,
            NewGameTableDraft.Settings settings) {
        LobbyParticipant local = new LobbyParticipant(
                host ? "server" : "client", null, true, host, false, true,
                false, true, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY);
        LobbyParticipant remote = new LobbyParticipant(
                host ? "client" : "server", null, false, !host, false, true,
                false, true, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY);
        return new LobbySnapshot(local.nickname(), "server",
                "localhost:2345", host,
                LobbySnapshot.Phase.WAITING_FOR_PLAYERS, "",
                List.of(local, remote), List.of(), settings, false);
    }
}

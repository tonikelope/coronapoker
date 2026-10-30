package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.GameDialogSink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxSettingsNavigationContractTest {

    @Test
    void liveGraphicsRowsPaintSmaaAsAStepperWithoutToggleOverlap()
            throws IOException {
        String source = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "CoronaPokerGdxTable.java"));
        int start = source.indexOf(
                "private void drawSettingsContentShapes(");
        int end = source.indexOf(
                "private float settingsToggleAnimation(", start);
        assertTrue(start >= 0 && end > start);
        String shapes = source.substring(start, end);

        assertTrue(shapes.contains(
                "drawSettingsStepperShape(x, firstY - 5f * rowStride"));
        assertTrue(shapes.contains(
                "drawSettingsToggleShape(x, firstY - 6f * rowStride"));
        assertTrue(shapes.contains(
                "drawSettingsInfoShape(x, firstY - 7f * rowStride"));
        assertFalse(shapes.contains(
                "drawSettingsToggleShape(x, firstY - 5f * rowStride"),
                "the FPS toggle must never be painted over the SMAA plus "
                + "button");
    }

    @Test
    void gamePagesHaveOneSharedContextAwareContract() {
        assertEquals(List.of("CIEGAS", "COMPRA", "BOTS", "REGLAS"),
                GdxSettingsContract.WAITING_ROOM_GAME_PAGES);
        assertEquals(GdxSettingsContract.WAITING_ROOM_GAME_PAGES,
                GdxSettingsContract.LIVE_TABLE_GAME_PAGES);
    }

    @Test
    void liveTableSettingsExcludeFastAccessSessionOperations() {
        assertEquals(List.of("CIEGAS", "COMPRA", "BOTS", "REGLAS"),
                CoronaPokerGdxTable.settingsGamePageLabels());
    }

    @Test
    void liveHandLimitUsesTheSameInlineSwitchAndCounterAsTheLobby()
            throws IOException {
        String source = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "CoronaPokerGdxTable.java"));

        assertTrue(source.contains("toggleDraftHandLimit();"));
        assertTrue(source.contains(
                "adjustDraftHandLimit(settingsStepperDirection"));
        assertFalse(source.contains("openDraftHandLimitDialog("),
                "the live hand limit must not fall back to a legacy modal");
    }

    @Test
    void liveAuthorityStatesHaveDistinctPaletteAndTooltipContracts()
            throws IOException {
        String source = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "CoronaPokerGdxTable.java"));

        assertTrue(source.contains("SERVER_ONLY"));
        assertTrue(source.contains("LIVE_LOCKED"));
        assertTrue(source.contains("gdx.settings.game.server_only"));
        assertTrue(source.contains("gdx.settings.game.locked_during_game"));
        assertTrue(source.contains("GdxSettingsStyle.serverOnlyText()"));
        assertTrue(source.contains("GdxSettingsStyle.lockedText()"));
    }

    @Test
    void mergedGameTabsRouteToTheirCombinedContentPages() {
        assertEquals(3, CoronaPokerGdxTable.settingsGameContentPage(0));
        assertEquals(4, CoronaPokerGdxTable.settingsGameContentPage(1));
        assertEquals(6, CoronaPokerGdxTable.settingsGameContentPage(2));
        assertEquals(7, CoronaPokerGdxTable.settingsGameContentPage(3));
        assertEquals(3, CoronaPokerGdxTable.settingsGameContentPage(-1));
        assertEquals(7, CoronaPokerGdxTable.settingsGameContentPage(99));
    }

    @Test
    void liveSettingsFooterOwnsInputBeforeEverySubpageContentHandler()
            throws IOException {
        String source = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "CoronaPokerGdxTable.java"));
        int start = source.indexOf("private void handleSettingsClick(");
        int end = source.indexOf("private static int settingsStepperDirection(",
                start);
        assertTrue(start >= 0 && end > start);
        String handler = source.substring(start, end);

        int footer = handler.indexOf("handleSettingsFooterClick(frame, x, y)");
        int content = handler.indexOf("int contentPage = settingsContentPage()");
        assertTrue(footer >= 0 && content > footer,
                "Save, Cancel and Restore must be routed before any clipped "
                + "or scrollable subsection can consume the click");
        assertTrue(source.contains(
                "private boolean handleSettingsFooterClick("));
        assertTrue(source.contains("requestCancelTableSettings(null)"));
        assertTrue(source.contains("restoreCurrentSettingsSectionDefaults()"));
        assertTrue(source.contains("closeTableSettings(true)"));
    }

    @Test
    void liveAndLobbyDiscardPromptsShareGeometryAndDangerSemantics()
            throws IOException {
        String table = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "CoronaPokerGdxTable.java"));
        String frontend = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "GdxFrontendScreen.java"));

        assertTrue(table.contains(
                "GdxUiDialogStyle.confirmationLayout(worldWidth, worldHeight)"));
        assertTrue(table.contains(
                "positive.width, positive.height, FOLD_RED"));
        assertTrue(table.contains(
                "handleSettingsDiscardDialogInput(dialog, pointer.x, pointer.y)"));
        assertTrue(frontend.contains(
                "GdxUiDialogStyle.ConfirmationLayout layout = drawConfirmationChrome("));
        assertTrue(frontend.contains(
                "ButtonTone.DANGER, () -> finishClosingSettings(false)"));
    }

    @Test
    void fastAccessBarRoutesEveryVisibleButtonToOneTypedAction() {
        assertEquals(List.of(
                CoronaPokerGdxTable.FastAccessAction.SETTINGS,
                CoronaPokerGdxTable.FastAccessAction.CHAT,
                CoronaPokerGdxTable.FastAccessAction.VOICE,
                CoronaPokerGdxTable.FastAccessAction.IMAGE,
                CoronaPokerGdxTable.FastAccessAction.REBUY,
                CoronaPokerGdxTable.FastAccessAction.GAME_LOG,
                CoronaPokerGdxTable.FastAccessAction.SCREENSHOTS,
                CoronaPokerGdxTable.FastAccessAction.FULLSCREEN,
                CoronaPokerGdxTable.FastAccessAction.EXIT),
                java.util.stream.IntStream.range(0, 9)
                        .mapToObj(CoronaPokerGdxTable::fastAccessActionAt)
                        .toList());
        assertEquals(CoronaPokerGdxTable.FastAccessAction.NONE,
                CoronaPokerGdxTable.fastAccessActionAt(-1));
        assertEquals(CoronaPokerGdxTable.FastAccessAction.NONE,
                CoronaPokerGdxTable.fastAccessActionAt(9));
    }

    @Test
    void fastAccessArtworkIsBoundToTheActionInsteadOfItsVisibleIndex() {
        assertEquals(0, CoronaPokerGdxTable.fastAccessResourceIndex(
                CoronaPokerGdxTable.FastAccessAction.SETTINGS));
        assertEquals(1, CoronaPokerGdxTable.fastAccessResourceIndex(
                CoronaPokerGdxTable.FastAccessAction.CHAT));
        assertEquals(2, CoronaPokerGdxTable.fastAccessResourceIndex(
                CoronaPokerGdxTable.FastAccessAction.VOICE));
        assertEquals(3, CoronaPokerGdxTable.fastAccessResourceIndex(
                CoronaPokerGdxTable.FastAccessAction.IMAGE));
        assertEquals(4, CoronaPokerGdxTable.fastAccessResourceIndex(
                CoronaPokerGdxTable.FastAccessAction.REBUY));
        assertEquals(5, CoronaPokerGdxTable.fastAccessResourceIndex(
                CoronaPokerGdxTable.FastAccessAction.GAME_LOG));
        assertEquals(6, CoronaPokerGdxTable.fastAccessResourceIndex(
                CoronaPokerGdxTable.FastAccessAction.SCREENSHOTS));
        assertEquals(7, CoronaPokerGdxTable.fastAccessResourceIndex(
                CoronaPokerGdxTable.FastAccessAction.FULLSCREEN));
        assertEquals(8, CoronaPokerGdxTable.fastAccessResourceIndex(
                CoronaPokerGdxTable.FastAccessAction.STOP));
        assertEquals(9, CoronaPokerGdxTable.fastAccessResourceIndex(
                CoronaPokerGdxTable.FastAccessAction.EXIT));
        assertEquals(-1, CoronaPokerGdxTable.fastAccessResourceIndex(
                CoronaPokerGdxTable.FastAccessAction.BLOCKS));
        assertEquals(-1, CoronaPokerGdxTable.fastAccessResourceIndex(
                CoronaPokerGdxTable.FastAccessAction.NONE));
    }

    @Test
    void hostFastAccessAddsStopImmediatelyBeforeExit() {
        assertEquals(List.of(
                CoronaPokerGdxTable.FastAccessAction.SETTINGS,
                CoronaPokerGdxTable.FastAccessAction.CHAT,
                CoronaPokerGdxTable.FastAccessAction.VOICE,
                CoronaPokerGdxTable.FastAccessAction.IMAGE,
                CoronaPokerGdxTable.FastAccessAction.REBUY,
                CoronaPokerGdxTable.FastAccessAction.GAME_LOG,
                CoronaPokerGdxTable.FastAccessAction.SCREENSHOTS,
                CoronaPokerGdxTable.FastAccessAction.FULLSCREEN,
                CoronaPokerGdxTable.FastAccessAction.STOP,
                CoronaPokerGdxTable.FastAccessAction.EXIT),
                java.util.stream.IntStream.range(0, 10)
                        .mapToObj(index -> CoronaPokerGdxTable
                                .fastAccessActionAt(index, true))
                        .toList());
        assertEquals(CoronaPokerGdxTable.FastAccessAction.NONE,
                CoronaPokerGdxTable.fastAccessActionAt(10, true));
    }

    @Test
    void hostFastAccessShowsBlocksOnlyWhileAQuarantineExists() {
        assertEquals(List.of(
                CoronaPokerGdxTable.FastAccessAction.SETTINGS,
                CoronaPokerGdxTable.FastAccessAction.CHAT,
                CoronaPokerGdxTable.FastAccessAction.VOICE,
                CoronaPokerGdxTable.FastAccessAction.IMAGE,
                CoronaPokerGdxTable.FastAccessAction.REBUY,
                CoronaPokerGdxTable.FastAccessAction.GAME_LOG,
                CoronaPokerGdxTable.FastAccessAction.SCREENSHOTS,
                CoronaPokerGdxTable.FastAccessAction.FULLSCREEN,
                CoronaPokerGdxTable.FastAccessAction.BLOCKS,
                CoronaPokerGdxTable.FastAccessAction.STOP,
                CoronaPokerGdxTable.FastAccessAction.EXIT),
                java.util.stream.IntStream.range(0, 11)
                        .mapToObj(index -> CoronaPokerGdxTable
                                .fastAccessActionAt(index, true, true))
                        .toList());
        assertEquals(CoronaPokerGdxTable.FastAccessAction.NONE,
                CoronaPokerGdxTable.fastAccessActionAt(11, true, true));
        assertTrue(CoronaPokerGdxTable.fastAccessSurfaceContains(
                673f, CoronaPokerGdxTable.FAST_BAR_Y + 30f,
                true, true, true));
        assertFalse(CoronaPokerGdxTable.fastAccessSurfaceContains(
                675f, CoronaPokerGdxTable.FAST_BAR_Y + 30f,
                true, true, true));
    }

    @Test
    void fastAccessSurfaceOwnsButtonsPaddingAndGaps() {
        // Closed bar owns its complete painted panel, not only the icon.
        assertTrue(CoronaPokerGdxTable.fastAccessSurfaceContains(
                19f, CoronaPokerGdxTable.FAST_BAR_Y + 1f, false, true));
        assertTrue(CoronaPokerGdxTable.fastAccessSurfaceContains(
                83f, CoronaPokerGdxTable.FAST_BAR_Y + 60f, false, true));
        assertFalse(CoronaPokerGdxTable.fastAccessSurfaceContains(
                85f, CoronaPokerGdxTable.FAST_BAR_Y + 30f, false, true));

        // Expanded host bar includes the gap before STOP and the complete EXIT
        // button, precisely where it overlaps the local HUD at 1920x1080.
        assertTrue(CoronaPokerGdxTable.fastAccessSurfaceContains(
                434f, CoronaPokerGdxTable.FAST_BAR_Y + 30f, true, true));
        assertTrue(CoronaPokerGdxTable.fastAccessSurfaceContains(
                614f, CoronaPokerGdxTable.FAST_BAR_Y + 30f, true, true));
        assertFalse(CoronaPokerGdxTable.fastAccessSurfaceContains(
                616f, CoronaPokerGdxTable.FAST_BAR_Y + 30f, true, true));
    }

    @Test
    void autoModeDoesNotBlockHoverDrivenTableUtilities() {
        assertFalse(CoronaPokerGdxTable.blocksTableUtilities(null));
        assertFalse(CoronaPokerGdxTable.blocksTableUtilities(
                GdxTableDialog.autoAction("VAS A PASAR")));
        assertTrue(CoronaPokerGdxTable.blocksTableUtilities(
                new GdxTableDialog(GdxTableDialog.Kind.CONFIRM,
                        "CONFIRMAR", GameDialogSink.Icon.NONE, 720, 0)));
    }

    @Test
    void expandedQuickBarOwnsEveryPixelPaintedAboveAutoMode() {
        float y = CoronaPokerGdxTable.FAST_BAR_Y + 30f;

        // At 1920 logical pixels the host STOP and EXIT buttons overlap the
        // AUTO action strip. The quick bar is painted last and must own them.
        assertTrue(CoronaPokerGdxTable.autoActionHudLayout(1920f).bounds()
                .contains(582f, y));
        assertTrue(CoronaPokerGdxTable.fastAccessSurfaceContains(
                582f, y, true, true, false));
        assertFalse(CoronaPokerGdxTable.autoActionHudOwnsPointer(
                582f, y, 1920f, true, true, false));

        // A quarantine inserts BLOCKS before STOP/EXIT and extends the same
        // overlap; all of those topmost pixels remain quick-bar owned.
        assertTrue(CoronaPokerGdxTable.autoActionHudLayout(1920f).bounds()
                .contains(641f, y));
        assertTrue(CoronaPokerGdxTable.fastAccessSurfaceContains(
                641f, y, true, true, true));
        assertFalse(CoronaPokerGdxTable.autoActionHudOwnsPointer(
                641f, y, 1920f, true, true, true));

        // The unobscured middle of the strip still belongs to AUTO and cannot
        // click through to cards or controls beneath it.
        assertTrue(CoronaPokerGdxTable.autoActionHudOwnsPointer(
                800f, y, 1920f, true, true, true));
    }

    @Test
    void quickChatRemainsAPlainTablePanelWhileImageGalleryIsModal() {
        assertFalse(CoronaPokerGdxTable.uiLayerUsesBackdropBlur(
                CoronaPokerGdxTable.UI_NONE, false));
        assertFalse(CoronaPokerGdxTable.uiLayerUsesBackdropBlur(
                CoronaPokerGdxTable.UI_CHAT, false));
        assertFalse(CoronaPokerGdxTable.uiLayerUsesBackdropBlur(
                CoronaPokerGdxTable.UI_GAME_LOG, false));
        assertTrue(CoronaPokerGdxTable.uiLayerUsesBackdropBlur(
                CoronaPokerGdxTable.UI_CHAT, true));
        assertTrue(CoronaPokerGdxTable.uiLayerUsesBackdropBlur(
                CoronaPokerGdxTable.UI_SETTINGS, false));
    }

    @Test
    void graphicsSettingsKeepsTheLiveTableSharpForSmaaPreview() {
        assertTrue(CoronaPokerGdxTable.settingsUsesSharpTablePreview(
                CoronaPokerGdxTable.UI_SETTINGS,
                GdxSettingsContract.Section.APPEARANCE, 1));
        assertFalse(CoronaPokerGdxTable.settingsUsesSharpTablePreview(
                CoronaPokerGdxTable.UI_SETTINGS,
                GdxSettingsContract.Section.APPEARANCE, 0));
        assertFalse(CoronaPokerGdxTable.settingsUsesSharpTablePreview(
                CoronaPokerGdxTable.UI_NONE,
                GdxSettingsContract.Section.APPEARANCE, 1));
    }

    @Test
    void pauseOwnsSharpBannerUnlessAnotherModalIsAboveIt() {
        assertTrue(CoronaPokerGdxTable.pauseOverlayUsesSharpForeground(
                true, false));
        assertFalse(CoronaPokerGdxTable.pauseOverlayUsesSharpForeground(
                false, false));
        assertFalse(CoronaPokerGdxTable.pauseOverlayUsesSharpForeground(
                true, true));
    }
}

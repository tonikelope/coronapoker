package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Input;
import com.tonikelope.coronapoker.table.TableSnapshot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxTableShortcutTest {

    @Test
    void quickActionsDoNotAdvertiseUnavailableTableFeatures() {
        assertFalse(CoronaPokerGdxTable.immediateRebuyControlEnabled(false));
        assertTrue(CoronaPokerGdxTable.immediateRebuyControlEnabled(true));
        assertFalse(CoronaPokerGdxTable.tableChatControlEnabled(false, true));
        assertFalse(CoronaPokerGdxTable.tableChatControlEnabled(true, false));
        assertTrue(CoronaPokerGdxTable.tableChatControlEnabled(true, true));
        assertFalse(CoronaPokerGdxTable.tableImageControlEnabled(false, true));
        assertFalse(CoronaPokerGdxTable.tableImageControlEnabled(true, false));
        assertTrue(CoronaPokerGdxTable.tableImageControlEnabled(true, true));
    }

    @Test
    void warmingAndSpectatorPlayersCannotRequestImmediateRebuy() {
        TableSnapshot.PlayerSnapshot warming = player(true, false, 10d);
        TableSnapshot.PlayerSnapshot spectator = player(true, false, 0d);
        TableSnapshot.PlayerSnapshot active = player(false, false, 10d);

        assertFalse(CoronaPokerGdxTable.immediateRebuyControlEnabled(
                true, warming));
        assertFalse(CoronaPokerGdxTable.immediateRebuyControlEnabled(
                true, spectator));
        assertFalse(CoronaPokerGdxTable.immediateRebuyControlEnabled(
                true, player(false, true, 10d)));
        assertTrue(CoronaPokerGdxTable.immediateRebuyControlEnabled(
                true, active));
    }

    @Test
    void usesTheCanonicalSwingPokerActionDefaults() {
        GdxShortcutBindings bindings = bindings();
        assertEquals(GdxShortcutBindings.FOLD,
                bindings.actionFor(Input.Keys.ESCAPE, false, false, false));
        assertEquals(GdxShortcutBindings.CHECK,
                bindings.actionFor(Input.Keys.SPACE, false, false, false));
        assertEquals(GdxShortcutBindings.BET_DOWN,
                bindings.actionFor(Input.Keys.DOWN, false, false, false));
        assertEquals(GdxShortcutBindings.BET_DOWN,
                bindings.actionFor(Input.Keys.LEFT, false, false, false));
        assertEquals(GdxShortcutBindings.BET_UP,
                bindings.actionFor(Input.Keys.UP, false, false, false));
        assertEquals(GdxShortcutBindings.BET_UP,
                bindings.actionFor(Input.Keys.RIGHT, false, false, false));
        assertEquals(GdxShortcutBindings.BET,
                bindings.actionFor(Input.Keys.ENTER, false, false, false));
        assertEquals(GdxShortcutBindings.ALL_IN,
                bindings.actionFor(Input.Keys.ENTER, false, false, true));
        assertEquals(GdxShortcutBindings.FULLSCREEN,
                bindings.actionFor(Input.Keys.F11, false, false, false));
    }

    @Test
    void escapeBelongsToEveryForegroundLayerBeforeItCanFold() {
        assertFalse(CoronaPokerGdxTable.foregroundUiOwnsEscape(
                CoronaPokerGdxTable.UI_NONE));
        assertTrue(CoronaPokerGdxTable.foregroundUiOwnsEscape(
                CoronaPokerGdxTable.UI_SETTINGS));
        assertTrue(CoronaPokerGdxTable.foregroundUiOwnsEscape(
                CoronaPokerGdxTable.UI_GAME_LOG));
        assertTrue(CoronaPokerGdxTable.foregroundUiOwnsEscape(
                CoronaPokerGdxTable.UI_CHAT));
        assertTrue(CoronaPokerGdxTable.foregroundUiOwnsEscape(
                CoronaPokerGdxTable.UI_CARD_VIEWER));
        assertTrue(CoronaPokerGdxTable.foregroundUiOwnsEscape(
                CoronaPokerGdxTable.UI_SCREENSHOTS));
    }

    @Test
    void foregroundEscapeReturnsBeforeLiveTableShortcutDispatch()
            throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        int gate = source.indexOf("&& consumeForegroundEscape()) {");
        int consumedReturn = source.indexOf("return;", gate);
        int liveDispatch = source.indexOf(
                "handleLiveTableInput(shortcutAction);", gate);

        assertTrue(gate >= 0 && consumedReturn > gate
                && liveDispatch > consumedReturn,
                "foreground ESC must leave the frame before poker shortcuts");
    }

    @Test
    void pauseAndLightsRequireTheCanonicalAltModifier() {
        GdxShortcutBindings bindings = bindings();
        assertEquals(GdxShortcutBindings.PAUSE,
                bindings.actionFor(Input.Keys.P, true, false, false));
        assertEquals(GdxShortcutBindings.LIGHTS,
                bindings.actionFor(Input.Keys.L, true, false, false));
        assertNull(bindings.actionFor(Input.Keys.P, false, false, false));
        assertNull(bindings.actionFor(Input.Keys.L, false, false, false));
    }

    @Test
    void volumeShortcutsKeepTheirSwingCombinations() {
        GdxShortcutBindings bindings = bindings();
        assertEquals(GdxShortcutBindings.VOLUME_UP,
                bindings.actionFor(Input.Keys.UP, false, false, true));
        assertEquals(GdxShortcutBindings.VOLUME_DOWN,
                bindings.actionFor(Input.Keys.DOWN, false, false, true));
    }

    @Test
    void finalSummaryKeepsBothVolumeShortcutsAvailable() {
        assertEquals(0.01f, CoronaPokerGdxTable.finalSummaryVolumeDelta(
                GdxShortcutBindings.VOLUME_UP));
        assertEquals(-0.01f, CoronaPokerGdxTable.finalSummaryVolumeDelta(
                GdxShortcutBindings.VOLUME_DOWN));
        assertEquals(0f, CoronaPokerGdxTable.finalSummaryVolumeDelta(
                GdxShortcutBindings.PAUSE));
    }

    @Test
    void extraModifiersCannotTriggerAHighImpactAction() {
        GdxShortcutBindings bindings = bindings();
        assertNull(bindings.actionFor(Input.Keys.ENTER, false, true, true));
        assertNull(bindings.actionFor(Input.Keys.ESCAPE, true, false, false));
        assertNull(bindings.actionFor(Input.Keys.P, true, false, true));
    }

    @Test
    void consumesTheExactSwingPersistenceFormat() {
        Properties properties = new Properties();
        properties.setProperty("shortcut.PAUSE", KeyEvent.VK_K + ","
                + InputEvent.ALT_DOWN_MASK);
        properties.setProperty("shortcut.ALLIN-BUTTON", KeyEvent.VK_A + ","
                + (InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        GdxShortcutBindings bindings = new GdxShortcutBindings(properties);

        assertNull(bindings.actionFor(Input.Keys.P, true, false, false));
        assertEquals(GdxShortcutBindings.PAUSE,
                bindings.actionFor(Input.Keys.K, true, false, false));
        assertEquals(GdxShortcutBindings.ALL_IN,
                bindings.actionFor(Input.Keys.A, false, true, true));
        assertNull(bindings.actionFor(Input.Keys.A, false, false, true));
    }

    @Test
    void malformedOrUnsupportedOverridesFallBackSafely() {
        Properties properties = new Properties();
        properties.setProperty("shortcut.PAUSE", "not-a-shortcut");
        properties.setProperty("shortcut.FOLD-BUTTON", KeyEvent.VK_ESCAPE
                + "," + InputEvent.META_DOWN_MASK);
        GdxShortcutBindings bindings = new GdxShortcutBindings(properties);

        assertEquals(GdxShortcutBindings.PAUSE,
                bindings.actionFor(Input.Keys.P, true, false, false));
        assertEquals(GdxShortcutBindings.FOLD,
                bindings.actionFor(Input.Keys.ESCAPE, false, false, false));
    }

    @Test
    void fixedHorizontalBetAliasesRemainAvailableAfterRebinding() {
        Properties properties = new Properties();
        properties.setProperty("shortcut.BET-UP", KeyEvent.VK_U + ",0");
        GdxShortcutBindings bindings = new GdxShortcutBindings(properties);

        assertEquals(GdxShortcutBindings.BET_UP,
                bindings.actionFor(Input.Keys.U, false, false, false));
        assertEquals(GdxShortcutBindings.BET_UP,
                bindings.actionFor(Input.Keys.RIGHT, false, false, false));
        assertNull(bindings.actionFor(Input.Keys.UP, false, false, false));
    }

    @Test
    void translatesSwingFunctionKeysInsteadOfReusingAwtNumbers() {
        Properties properties = new Properties();
        properties.setProperty("shortcut.VOICE-RECORD",
                KeyEvent.VK_F9 + ",0");
        GdxShortcutBindings bindings = new GdxShortcutBindings(properties);

        assertEquals(GdxShortcutBindings.VOICE_RECORD,
                bindings.actionFor(Input.Keys.F9, false, false, false));
    }

    @Test
    void shortcutEditingCommitsInTheCanonicalSwingFormat() {
        Properties properties = new Properties();
        GdxShortcutBindings bindings = new GdxShortcutBindings(properties);
        bindings.beginEdit();

        assertEquals(GdxShortcutBindings.Assignment.ASSIGNED,
                bindings.assign(GdxShortcutBindings.PAUSE, Input.Keys.K,
                        true, false, false));
        bindings.commitEdit();

        assertEquals(KeyEvent.VK_K + "," + InputEvent.ALT_DOWN_MASK,
                properties.getProperty("shortcut.PAUSE"));
        assertEquals(GdxShortcutBindings.PAUSE,
                bindings.actionFor(Input.Keys.K, true, false, false));
    }

    @Test
    void cancellingAnEditRestoresTheOpeningBindingsAndProperties() {
        Properties properties = new Properties();
        GdxShortcutBindings bindings = new GdxShortcutBindings(properties);
        bindings.beginEdit();
        assertEquals(GdxShortcutBindings.Assignment.ASSIGNED,
                bindings.assign(GdxShortcutBindings.PAUSE, Input.Keys.K,
                        true, false, false));

        bindings.cancelEdit();

        assertNull(properties.getProperty("shortcut.PAUSE"));
        assertEquals(GdxShortcutBindings.PAUSE,
                bindings.actionFor(Input.Keys.P, true, false, false));
        assertNull(bindings.actionFor(Input.Keys.K, true, false, false));
    }

    @Test
    void reportsPendingShortcutChangesOnlyInsideTheEditTransaction() {
        GdxShortcutBindings bindings = bindings();
        bindings.beginEdit();
        assertFalse(bindings.hasPendingEdits());

        assertEquals(GdxShortcutBindings.Assignment.ASSIGNED,
                bindings.assign(GdxShortcutBindings.PAUSE, Input.Keys.K,
                        true, false, false));
        assertTrue(bindings.hasPendingEdits());

        bindings.cancelEdit();
        assertFalse(bindings.hasPendingEdits());
    }

    @Test
    void editingRejectsCollisionsIncludingFixedAliases() {
        GdxShortcutBindings bindings = bindings();
        bindings.beginEdit();

        assertEquals(GdxShortcutBindings.Assignment.CONFLICT,
                bindings.assign(GdxShortcutBindings.PAUSE, Input.Keys.SPACE,
                        false, false, false));
        assertEquals(GdxShortcutBindings.Assignment.CONFLICT,
                bindings.assign(GdxShortcutBindings.PAUSE, Input.Keys.RIGHT,
                        false, false, false));
        assertEquals(GdxShortcutBindings.PAUSE,
                bindings.actionFor(Input.Keys.P, true, false, false));
    }

    @Test
    void resetAllClearsOverridesOnlyWhenTheTransactionIsCommitted() {
        Properties properties = new Properties();
        properties.setProperty("shortcut.PAUSE", KeyEvent.VK_K + ","
                + InputEvent.ALT_DOWN_MASK);
        GdxShortcutBindings bindings = new GdxShortcutBindings(properties);
        bindings.beginEdit();
        bindings.resetAllEdits();

        assertEquals(GdxShortcutBindings.PAUSE,
                bindings.actionFor(Input.Keys.P, true, false, false));
        assertTrue(properties.containsKey("shortcut.PAUSE"));

        bindings.commitEdit();
        assertFalse(properties.containsKey("shortcut.PAUSE"));
    }

    @Test
    void editorExposesEveryImplementedActionWithReadableKeyCaps() {
        GdxShortcutBindings bindings = bindings();
        assertEquals(20, bindings.editableEntries().size());
        GdxShortcutBindings.ShortcutEntry pause
                = bindings.editableEntries().get(0);
        assertEquals(GdxShortcutBindings.PAUSE, pause.id());
        assertEquals("ALT + P", pause.display());
        assertEquals("PAUSAR LA TIMBA", pause.description());
        assertEquals("PAUSAR LA TIMBA *", pause.markedDescription());
        assertEquals("F11", bindings.displayFor(
                GdxShortcutBindings.FULLSCREEN));
        assertEquals("UP", bindings.displayFor(GdxShortcutBindings.BET_UP));
        assertEquals("DOWN", bindings.displayFor(
                GdxShortcutBindings.BET_DOWN));
        assertEquals("Pause game", bindings.editableEntries(
                new GdxGameText("en")).get(0).description());
        assertEquals(GdxShortcutBindings.SCREENSHOT,
                bindings.actionFor(Input.Keys.P, false, true, false));
    }

    @Test
    void defaultMarkersFollowTheEditedShortcutPage() {
        GdxShortcutBindings bindings = bindings();
        assertTrue(bindings.isDefault(GdxShortcutBindings.PAUSE));

        bindings.beginEdit();
        assertEquals(GdxShortcutBindings.Assignment.ASSIGNED,
                bindings.assign(GdxShortcutBindings.PAUSE, Input.Keys.K,
                        true, false, false));
        assertFalse(bindings.isDefault(GdxShortcutBindings.PAUSE));
        GdxShortcutBindings.ShortcutEntry pause
                = bindings.editableEntries().get(0);
        assertEquals("ALT + K", pause.display());
        assertEquals("PAUSAR LA TIMBA", pause.markedDescription());
    }

    private static GdxShortcutBindings bindings() {
        return new GdxShortcutBindings(new Properties());
    }

    private static TableSnapshot.PlayerSnapshot player(boolean spectator,
            boolean exited, double stack) {
        return new TableSnapshot.PlayerSnapshot("local", stack, 0d, 0d,
                !spectator && !exited, spectator, exited, false,
                -2, -2, 0, 0L, false, false, TableSnapshot.Position.NONE,
                "", "", java.util.List.of(), 0, 0,
                spectator && !exited && stack > 0d);
    }
}

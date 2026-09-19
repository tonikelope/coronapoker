package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Input;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxTableShortcutTest {

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
        assertEquals(GdxShortcutBindings.SCREENSHOT,
                bindings.actionFor(Input.Keys.P, false, true, false));
    }

    private static GdxShortcutBindings bindings() {
        return new GdxShortcutBindings(new Properties());
    }
}

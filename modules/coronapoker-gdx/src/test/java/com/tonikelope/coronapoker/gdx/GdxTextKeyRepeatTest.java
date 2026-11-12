package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.JsonReader;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxTextKeyRepeatTest {

    @Test
    void repeatsOnlyInTheEditorThatReceivedThePress() {
        var repeat = new GdxTextKeyRepeat();
        List<Integer> edits = new ArrayList<>();
        repeat.press(Input.Keys.BACKSPACE, "query");
        repeat.update(0.3f, "query", true, edits::add);
        assertTrue(edits.isEmpty());
        repeat.update(0.13f, "query", true, edits::add);
        assertEquals(List.of(Input.Keys.BACKSPACE), edits);
        repeat.update(1f, "other", true, edits::add);
        repeat.update(1f, "query", true, edits::add);
        assertEquals(1, edits.size());
    }

    @Test
    void releaseAndFocusLossCancelAndActivationKeysDoNotRepeat() {
        var repeat = new GdxTextKeyRepeat();
        List<Integer> edits = new ArrayList<>();
        repeat.press(Input.Keys.LEFT, "query");
        repeat.update(1f, "query", false, edits::add);
        repeat.update(1f, "query", true, edits::add);
        repeat.press(Input.Keys.FORWARD_DEL, "query");
        repeat.release(Input.Keys.FORWARD_DEL);
        repeat.update(1f, "query", true, edits::add);
        assertTrue(edits.isEmpty());
        assertFalse(GdxTextKeyRepeat.isEditingKey(Input.Keys.A));
        assertFalse(GdxTextKeyRepeat.isEditingKey(Input.Keys.ENTER));
        assertFalse(GdxTextKeyRepeat.isEditingKey(Input.Keys.TAB));
    }

    @Test
    void repeatedChromiumKeysCarryTheRepeatFlagAndNativeEditingCommands() {
        var json = new JsonReader();
        var backspace = json.parse(GdxBrowserKeyboard.parameters(KeyEvent.KEY_PRESSED,
                Input.Keys.BACKSPACE, InputEvent.CTRL_DOWN_MASK, true));
        assertEquals("rawKeyDown", backspace.getString("type"));
        assertTrue(backspace.getBoolean("autoRepeat"));
        assertEquals("DeleteWordBackward", backspace.get("commands").getString(0));
        var arrow = json.parse(GdxBrowserKeyboard.parameters(KeyEvent.KEY_PRESSED,
                Input.Keys.LEFT, InputEvent.SHIFT_DOWN_MASK, true));
        assertEquals("ArrowLeft", arrow.getString("key"));
        assertEquals(8, arrow.getInt("modifiers"));
        var release = json.parse(GdxBrowserKeyboard.parameters(KeyEvent.KEY_RELEASED,
                Input.Keys.LEFT, 0, false));
        assertEquals("keyUp", release.getString("type"));
        assertFalse(release.has("autoRepeat"));
    }
}

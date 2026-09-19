package com.tonikelope.coronapoker.gdx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GdxTextEditStateTest {

    @Test
    void replacesSelectionAndSupportsClipboardStyleEdits() {
        GdxTextEditState edit = new GdxTextEditState();
        edit.focus("chat", "CoronaPoker");
        edit.home("CoronaPoker", false);
        for (int i = 0; i < 6; i++) edit.right("CoronaPoker", true);

        assertEquals("Corona", edit.selectedText("CoronaPoker"));
        assertEquals("GDXPoker",
                edit.replaceSelection("CoronaPoker", "GDX", 40));
    }

    @Test
    void movementAndDeletionNeverSplitUnicodeCodePoints() {
        GdxTextEditState edit = new GdxTextEditState();
        String value = "A😀B";
        edit.focus("chat", value);
        edit.left(value, false);
        edit.left(value, false);

        assertEquals(1, edit.caret(value));
        assertEquals("AB", edit.delete(value));
    }

    @Test
    void insertionHonoursCodePointLimitAfterReplacingSelection() {
        GdxTextEditState edit = new GdxTextEditState();
        edit.focus("chat", "1234");
        edit.selectAll("1234");

        assertEquals("ab😀", edit.replaceSelection("1234", "ab😀cd", 3));
    }

    @Test
    void focusChangePlacesCaretAtEndButRefocusPreservesSelection() {
        GdxTextEditState edit = new GdxTextEditState();
        edit.focus("nick", "server");
        edit.left("server", true);
        edit.focus("nick", "server");
        assertEquals("r", edit.selectedText("server"));

        edit.focus("port", "2345");
        assertEquals(4, edit.caret("2345"));
    }

    @Test
    void pointerChoosesNearestVisualInsertionBoundary() {
        String value = "abcd";

        assertEquals(0, GdxTextEditState.nearestBoundary(value, 0,
                value.length(), 4f, index -> index + 1,
                index -> index * 10d));
        assertEquals(1, GdxTextEditState.nearestBoundary(value, 0,
                value.length(), 6f, index -> index + 1,
                index -> index * 10d));
        assertEquals(4, GdxTextEditState.nearestBoundary(value, 0,
                value.length(), 100f, index -> index + 1,
                index -> index * 10d));
    }

    @Test
    void pointerCanTreatACompoundTokenAsOneVisualGlyph() {
        String value = "A#12#B";

        assertEquals(1, GdxTextEditState.nearestBoundary(value, 0,
                value.length(), 13f,
                index -> index == 1 ? 5 : index + 1,
                index -> switch (index) {
                    case 1 -> 10d;
                    case 5 -> 30d;
                    case 6 -> 40d;
                    default -> 0d;
                }));
        assertEquals(5, GdxTextEditState.nearestBoundary(value, 0,
                value.length(), 22f,
                index -> index == 1 ? 5 : index + 1,
                index -> switch (index) {
                    case 1 -> 10d;
                    case 5 -> 30d;
                    case 6 -> 40d;
                    default -> 0d;
                }));
    }

    @Test
    void pointerFallbackNeverSplitsASurrogatePair() {
        String value = "A😀B";

        assertEquals(3, GdxTextEditState.nearestBoundary(value, 0,
                value.length(), 16f, ignored -> -1,
                index -> value.codePointCount(0, index) * 10d));
    }
}

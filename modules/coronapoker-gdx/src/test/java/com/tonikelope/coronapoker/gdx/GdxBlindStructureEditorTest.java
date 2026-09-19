package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.BlindStructureCatalog;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class GdxBlindStructureEditorTest {

    @Test
    void editsAreTransactionalUntilSave() {
        Properties properties = propertiesWith("Turbo", "0.1/0.2,0.2/0.4");
        GdxBlindStructureEditor editor = new GdxBlindStructureEditor();
        editor.begin(properties, "Turbo");

        assertTrue(editor.rename("Turbo amigos"));
        assertTrue(editor.adjustSmallBlind(1));
        assertTrue(editor.dirty());
        assertEquals("Turbo", BlindStructureCatalog.read(properties).get(0).name());

        editor.save(properties);

        assertFalse(editor.dirty());
        assertEquals("Turbo amigos",
                BlindStructureCatalog.read(properties).get(0).name());
        assertEquals(0.15d, BlindStructureCatalog.read(properties).get(0)
                .levels().get(0).smallBlind());
    }

    @Test
    void rejectsDuplicateNamesAndInvalidLevelMoves() {
        Properties properties = propertiesWith("Turbo", "0.1/0.2,0.2/0.4");
        properties.setProperty("blind_structures.count", "2");
        properties.setProperty("blind_structure.1.name", "Deep");
        properties.setProperty("blind_structure.1.levels", "1/2,2/4");
        GdxBlindStructureEditor editor = new GdxBlindStructureEditor();
        editor.begin(properties, "Turbo");

        assertFalse(editor.rename("Deep"));
        assertFalse(editor.adjustSmallBlind(-2));
        editor.selectLevel(1);
        assertFalse(editor.adjustSmallBlind(-2));
        assertEquals(0.2d, editor.selectedLevel().smallBlind());
    }

    @Test
    void supportsCreateDuplicateDeleteAndLevelCrud() {
        GdxBlindStructureEditor editor = new GdxBlindStructureEditor();
        Properties properties = new Properties();
        editor.begin(properties, null);

        assertTrue(editor.create("Nueva"));
        assertEquals(30, editor.selected().levels().size());
        assertTrue(editor.duplicate("Copia"));
        assertFalse(editor.duplicate("Nueva"));
        editor.selectLevel(-1);
        assertTrue(editor.removeSelectedLevel());
        assertEquals(29, editor.selected().levels().size());
        assertTrue(editor.addLevel());
        assertEquals(30, editor.selected().levels().size());
        assertTrue(editor.deleteSelected());
        assertEquals("Nueva", editor.selected().name());
        assertTrue(editor.deleteSelected());
        assertNull(editor.selected());
    }

    private static Properties propertiesWith(String name, String levels) {
        Properties properties = new Properties();
        properties.setProperty("blind_structures.count", "1");
        properties.setProperty("blind_structure.0.name", name);
        properties.setProperty("blind_structure.0.levels", levels);
        return properties;
    }
}

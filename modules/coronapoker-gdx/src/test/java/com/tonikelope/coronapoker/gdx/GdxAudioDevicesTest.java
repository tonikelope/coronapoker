package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxAudioDevicesTest {

    @Test
    void cancelOnlyReopensOpenAlWhenTheOutputSelectionReallyChanged() {
        assertFalse(GdxAudioDevices.outputSelectionChanged(null, ""));
        assertFalse(GdxAudioDevices.outputSelectionChanged("Altavoces",
                "Altavoces"));
        assertTrue(GdxAudioDevices.outputSelectionChanged("Altavoces",
                "Auriculares"));
    }

    @Test
    void deviceCycleIncludesDefaultAndRecoversUnknownSelection() {
        List<String> devices = List.of("Altavoces", "Auriculares");
        assertEquals("Altavoces", GdxAudioDevices.nextDevice(devices, ""));
        assertEquals("Auriculares",
                GdxAudioDevices.nextDevice(devices, "Altavoces"));
        assertEquals("", GdxAudioDevices.nextDevice(devices, "Auriculares"));
        assertEquals("", GdxAudioDevices.nextDevice(devices, "Desconectado"));
        assertEquals("Auriculares",
                GdxAudioDevices.adjacentDevice(devices, "", -1));
        assertEquals("Altavoces",
                GdxAudioDevices.adjacentDevice(devices, "Auriculares", -1));
        assertEquals("",
                GdxAudioDevices.adjacentDevice(devices, "Altavoces", -1));
    }
}

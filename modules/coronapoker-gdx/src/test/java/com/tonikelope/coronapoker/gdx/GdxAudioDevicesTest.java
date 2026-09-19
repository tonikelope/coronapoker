package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class GdxAudioDevicesTest {

    @Test
    void defaultDeviceLabelFollowsTheLiveLanguage() {
        Properties properties = new Properties();
        assertEquals("PREDETERMINADO DEL SISTEMA",
                GdxAudioDevices.outputLabel(properties,
                        new GdxGameText("es")));
        assertEquals("SYSTEM DEFAULT",
                GdxAudioDevices.captureLabel(properties,
                        new GdxGameText("en")));
        properties.setProperty(GdxAudioDevices.OUTPUT_KEY, "Altavoces USB");
        assertEquals("Altavoces USB", GdxAudioDevices.outputLabel(properties,
                new GdxGameText("en")));
    }

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

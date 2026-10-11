package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.LineUnavailableException;
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

    @Test
    void hotplugTopologyIgnoresOrderingButDetectsRealDeviceChanges() {
        assertFalse(GdxAudioDevices.outputTopologyChanged(
                List.of("Altavoces", "Auriculares"),
                List.of("Auriculares", "Altavoces")));
        assertFalse(GdxAudioDevices.outputTopologyChanged(
                List.of("Altavoces", "Altavoces", " "),
                List.of("Altavoces")));
        assertTrue(GdxAudioDevices.outputTopologyChanged(
                List.of("Altavoces"),
                List.of("Altavoces", "Auriculares USB")));
        assertFalse(GdxAudioDevices.topologyHasOutput(List.of()));
        assertFalse(GdxAudioDevices.topologyHasOutput(
                java.util.Arrays.asList(null, " ")));
        assertTrue(GdxAudioDevices.topologyHasOutput(
                List.of("Altavoces")));
    }

    @Test
    void hotplugMonitorTracksDisconnectAndReconnectWithoutChangingPreference() {
        GdxAudioDevices.OutputHotplugMonitor monitor =
                new GdxAudioDevices.OutputHotplugMonitor();
        assertFalse(monitor.acceptTopology(List.of("Altavoces")));
        assertTrue(monitor.outputAvailable());
        assertTrue(monitor.acceptTopology(List.of()));
        assertFalse(monitor.outputAvailable());
        assertFalse(monitor.acceptTopology(List.of()));
        assertTrue(monitor.acceptTopology(List.of("Auriculares USB")));
        assertTrue(monitor.outputAvailable());
    }

    @Test
    void hotplugEnumerationNeverRunsOnTheRenderUpdateCall() {
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<Runnable> worker = new AtomicReference<>();
        GdxAudioDevices.OutputHotplugMonitor monitor =
                new GdxAudioDevices.OutputHotplugMonitor(() -> {
                    reads.incrementAndGet();
                    return List.of("Altavoces");
                }, worker::set);

        assertFalse(monitor.update(new Properties(), 0f));
        assertEquals(0, reads.get(),
                "render update must only schedule the OpenAL enumeration");
        assertNotNull(worker.get());
        worker.getAndSet(null).run();
        assertFalse(monitor.update(new Properties(), 0f),
                "the first topology establishes a baseline");
        assertEquals(1, reads.get());
        assertTrue(monitor.outputAvailable());

        assertFalse(monitor.update(new Properties(), 1f));
        assertEquals(1, reads.get(),
                "the next poll must also remain off the render thread");
        assertNotNull(worker.get());
    }

    @Test
    void missingConfiguredMicrophoneDoesNotFallBackToAnotherInput() {
        Properties properties = new Properties();
        properties.setProperty(GdxAudioDevices.CAPTURE_KEY,
                "__coronapoker_missing_capture_device_for_qa__");

        assertThrows(LineUnavailableException.class,
                () -> GdxAudioDevices.openCapture(properties,
                        GdxVoiceRecorder.PCM_FORMAT));
    }
}

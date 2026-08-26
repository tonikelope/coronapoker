package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import javax.sound.sampled.LineUnavailableException;
import org.junit.jupiter.api.Test;

class VoiceMicrophoneSelectionTest {

    @Test
    void missingConfiguredMicrophoneDoesNotFallBackToAnotherInput() throws Exception {
        Field captureDevice = AudioDeviceManager.class.getDeclaredField("CAPTURE_DEVICE");
        captureDevice.setAccessible(true);
        String previous = (String) captureDevice.get(null);

        try {
            captureDevice.set(null, "__coronapoker_missing_capture_device_for_qa__");

            assertThrows(LineUnavailableException.class,
                    () -> AudioDeviceManager.getTargetDataLine(VoiceRecorder.PCM_FORMAT));
        } finally {
            captureDevice.set(null, previous);
        }
    }
}

package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import org.junit.jupiter.api.Test;

class GdxVoiceRecorderTest {

    @Test
    void completedSimulatedCaptureProducesValidatedWav() throws Exception {
        SimulatedCapture capture = new SimulatedCapture(audiblePcm(4000), true);
        GdxVoiceRecorder recorder = recorder(capture);

        assertEquals(GdxVoiceRecorder.Outcome.RECORDING, recorder.start(null, null));
        awaitCaptured(recorder, GdxVoiceRecorder.MIN_MILLIS);

        byte[] wav = recorder.stopAndEncode();

        assertNotNull(wav);
        assertEquals(GdxVoiceRecorder.Outcome.OK, recorder.outcome());
        assertNull(com.tonikelope.coronapoker.core.audio.VoiceWavContract
                .validationError(wav));
    }

    @Test
    void digitalSilenceNeverProducesAFile() throws Exception {
        SimulatedCapture capture = new SimulatedCapture(new byte[4000], true);
        GdxVoiceRecorder recorder = recorder(capture);

        assertEquals(GdxVoiceRecorder.Outcome.RECORDING, recorder.start(null, null));
        awaitCaptured(recorder, GdxVoiceRecorder.MIN_MILLIS);

        assertNull(recorder.stopAndEncode());
        assertEquals(GdxVoiceRecorder.Outcome.SILENT, recorder.outcome());
    }

    @Test
    void captureStillBlockedAfterCloseIsDiscarded() throws Exception {
        SimulatedCapture capture = new SimulatedCapture(audiblePcm(4000), false);
        GdxVoiceRecorder recorder = recorder(capture);

        try {
            assertEquals(GdxVoiceRecorder.Outcome.RECORDING, recorder.start(null, null));
            awaitCaptured(recorder, GdxVoiceRecorder.MIN_MILLIS);

            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                assertNull(recorder.stopAndEncode());
                assertEquals(GdxVoiceRecorder.Outcome.LOST, recorder.outcome());
            });
        } finally {
            capture.releaseBlockedRead();
        }
    }

    @Test
    void quietCaptureGetsBoundedSoftwareGainWithoutClipping() {
        byte[] quiet = constantPcm(1_000, 200);

        byte[] boosted = GdxVoiceRecorder.applySafeCaptureGain(quiet);

        assertEquals(4_000, firstSample(boosted));
    }

    @Test
    void alreadyLoudCaptureKeepsItsOriginalLevel() {
        byte[] loud = constantPcm(30_000, 200);

        byte[] unchanged = GdxVoiceRecorder.applySafeCaptureGain(loud);

        assertEquals(30_000, firstSample(unchanged));
    }

    @Test
    void automaticGainIsEnabledByDefaultAndCanBeDisabled() {
        Properties properties = new Properties();
        assertEquals(true, GdxVoiceRecorder.automaticGainEnabled(properties));

        properties.setProperty(GdxSettingsContract.AUDIO_MIC_AUTO_GAIN_KEY,
                "false");
        assertEquals(false, GdxVoiceRecorder.automaticGainEnabled(properties));
    }

    private static GdxVoiceRecorder recorder(SimulatedCapture capture) {
        return new GdxVoiceRecorder(format -> capture.line());
    }

    private static void awaitCaptured(GdxVoiceRecorder recorder, long millis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (recorder.capturedMillis() < millis && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertEquals(true, recorder.capturedMillis() >= millis,
                "simulated capture did not deliver its first audio block");
    }

    private static byte[] audiblePcm(int bytes) {
        byte[] pcm = new byte[bytes];
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            pcm[i] = 0x20;
            pcm[i + 1] = 0x03;
        }
        return pcm;
    }

    private static byte[] constantPcm(int sample, int bytes) {
        byte[] pcm = new byte[bytes];
        for (int index = 0; index + 1 < pcm.length; index += 2) {
            pcm[index] = (byte) sample;
            pcm[index + 1] = (byte) (sample >>> 8);
        }
        return pcm;
    }

    private static int firstSample(byte[] pcm) {
        return (short) ((pcm[0] & 0xff) | (pcm[1] << 8));
    }

    private static final class SimulatedCapture implements InvocationHandler {

        private final byte[] first_block;
        private final boolean release_on_close;
        private final AtomicInteger delivered_bytes = new AtomicInteger(0);
        private final CountDownLatch blocked_read = new CountDownLatch(1);
        private final TargetDataLine line;

        SimulatedCapture(byte[] first_block, boolean release_on_close) {
            this.first_block = first_block;
            this.release_on_close = release_on_close;
            this.line = (TargetDataLine) Proxy.newProxyInstance(
                    TargetDataLine.class.getClassLoader(),
                    new Class<?>[]{TargetDataLine.class}, this);
        }

        TargetDataLine line() {
            return line;
        }

        void releaseBlockedRead() {
            blocked_read.countDown();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
            switch (method.getName()) {
                case "read":
                    int source_offset = delivered_bytes.get();
                    if (source_offset < first_block.length) {
                        byte[] target = (byte[]) args[0];
                        int offset = (Integer) args[1];
                        int length = Math.min((Integer) args[2], first_block.length - source_offset);
                        System.arraycopy(first_block, source_offset, target, offset, length);
                        delivered_bytes.addAndGet(length);
                        return length;
                    }
                    blocked_read.await(2, TimeUnit.SECONDS);
                    return 0;
                case "close":
                    if (release_on_close) {
                        releaseBlockedRead();
                    }
                    return null;
                case "open":
                case "start":
                case "stop":
                case "flush":
                case "drain":
                    return null;
                case "available":
                case "getBufferSize":
                case "getFramePosition":
                    return 0;
                case "getLongFramePosition":
                case "getMicrosecondPosition":
                    return 0L;
                case "getLevel":
                    return 0f;
                case "getFormat":
                    return GdxVoiceRecorder.PCM_FORMAT;
                case "getLineInfo":
                    return new DataLine.Info(TargetDataLine.class, GdxVoiceRecorder.PCM_FORMAT);
                case "getControls":
                    return new javax.sound.sampled.Control[0];
                case "isControlSupported":
                    return false;
                case "isOpen":
                case "isRunning":
                case "isActive":
                    return true;
                case "toString":
                    return "SimulatedTargetDataLine";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    throw new UnsupportedOperationException(method.toString());
            }
        }
    }
}

package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import org.junit.jupiter.api.Test;

class VoiceRecorderCaptureTest {

    @Test
    void completedSimulatedCaptureProducesValidatedWav() throws Exception {
        SimulatedCapture capture = new SimulatedCapture(audiblePcm(4000), true);
        VoiceRecorder recorder = recorder(capture);

        assertEquals(VoiceRecorder.Outcome.RECORDING, recorder.start(null, null));
        awaitCaptured(recorder, VoiceRecorder.MIN_MILLIS);

        byte[] wav = recorder.stop();

        assertNotNull(wav);
        assertEquals(VoiceRecorder.Outcome.OK, recorder.getOutcome());
        assertEquals(true, VoiceWavValidator.isValid(wav));
    }

    @Test
    void digitalSilenceNeverProducesAFile() throws Exception {
        SimulatedCapture capture = new SimulatedCapture(new byte[4000], true);
        VoiceRecorder recorder = recorder(capture);

        assertEquals(VoiceRecorder.Outcome.RECORDING, recorder.start(null, null));
        awaitCaptured(recorder, VoiceRecorder.MIN_MILLIS);

        assertNull(recorder.stop());
        assertEquals(VoiceRecorder.Outcome.SILENT, recorder.getOutcome());
    }

    @Test
    void captureStillBlockedAfterCloseIsDiscarded() throws Exception {
        SimulatedCapture capture = new SimulatedCapture(audiblePcm(4000), false);
        VoiceRecorder recorder = recorder(capture);

        try {
            assertEquals(VoiceRecorder.Outcome.RECORDING, recorder.start(null, null));
            awaitCaptured(recorder, VoiceRecorder.MIN_MILLIS);

            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                assertNull(recorder.stop());
                assertEquals(VoiceRecorder.Outcome.LOST, recorder.getOutcome());
            });
        } finally {
            capture.releaseBlockedRead();
        }
    }

    private static VoiceRecorder recorder(SimulatedCapture capture) {
        return new VoiceRecorder(format -> capture.line(), 0, 25, 25);
    }

    private static void awaitCaptured(VoiceRecorder recorder, long millis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (recorder.getCapturedMillis() < millis && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertEquals(true, recorder.getCapturedMillis() >= millis,
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
                    return VoiceRecorder.PCM_FORMAT;
                case "getLineInfo":
                    return new DataLine.Info(TargetDataLine.class, VoiceRecorder.PCM_FORMAT);
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

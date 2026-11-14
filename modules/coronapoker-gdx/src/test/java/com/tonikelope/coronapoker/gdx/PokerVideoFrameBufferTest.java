package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;

final class PokerVideoFrameBufferTest {

    @Test
    void renderSkipsUploadDuringAConcurrentPaint() throws Exception {
        var buffer = new PokerVideoFrameBuffer();
        Field lockField = PokerVideoFrameBuffer.class.getDeclaredField("lock");
        lockField.setAccessible(true);
        var paintLock = (ReentrantLock) lockField.get(buffer);
        var paintStarted = new CountDownLatch(1);
        var finishPaint = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var paint = workers.submit(() -> {
                paintLock.lock();
                try {
                    paintStarted.countDown();
                    finishPaint.await();
                    return null;
                } finally {
                    paintLock.unlock();
                }
            });
            assertTrue(paintStarted.await(2, TimeUnit.SECONDS));
            var render = workers.submit(() -> buffer.uploadPendingFrame());
            assertNull(render.get(2, TimeUnit.SECONDS));
            finishPaint.countDown();
            paint.get(2, TimeUnit.SECONDS);
        } finally {
            finishPaint.countDown();
            workers.shutdownNow();
        }
    }
}

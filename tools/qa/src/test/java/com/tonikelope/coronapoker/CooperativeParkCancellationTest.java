package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class CooperativeParkCancellationTest {

    @Test
    public void executorInterruptEndsAHighPrecisionPauseImmediately() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean interruptPreserved = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            started.countDown();
            Helpers.parkThreadMillis(5_000);
            interruptPreserved.set(Thread.currentThread().isInterrupted());
            completed.countDown();
        }, "cooperative-park-cancellation-test");
        worker.setDaemon(true);
        worker.start();

        assertTrue(started.await(1, TimeUnit.SECONDS));
        worker.interrupt();

        assertTrue(completed.await(500, TimeUnit.MILLISECONDS),
                "an interrupted old-session worker must not finish the original pause");
        assertTrue(interruptPreserved.get(), "the cancellation signal must remain visible");
    }
}

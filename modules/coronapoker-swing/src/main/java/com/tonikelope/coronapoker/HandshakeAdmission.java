package com.tonikelope.coronapoker;

import java.io.IOException;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/** Owns cleanup of a pre-auth socket and its reserved slot until submission succeeds. */
public final class HandshakeAdmission {

    private HandshakeAdmission() {
    }

    public static boolean submit(Function<Runnable, ? extends Future<?>> submitter, Runnable handshake,
            Socket socket, Semaphore slots) {
        Objects.requireNonNull(submitter);
        Objects.requireNonNull(handshake);
        Objects.requireNonNull(socket);
        Objects.requireNonNull(slots);

        try {
            Future<?> submitted = submitter.apply(handshake);
            if (submitted != null) {
                return true;
            }
        } catch (RuntimeException rejected) {
            closeAndRelease(socket, slots);
            return false;
        }

        closeAndRelease(socket, slots);
        return false;
    }

    private static void closeAndRelease(Socket socket, Semaphore slots) {
        try {
            socket.close();
        } catch (IOException ignored) {
        } finally {
            slots.release();
        }
    }
}

package com.tonikelope.coronapoker.core;

import java.util.Objects;

/** Process owner of frontend-specific runtime resources. */
public final class FrontendRuntimeService implements ApplicationService {

    public interface Backend extends AutoCloseable {

        void start() throws Exception;

        @Override
        void close() throws Exception;
    }

    private static final Backend SILENT_BACKEND = new Backend() {
        @Override
        public void start() {
        }

        @Override
        public void close() {
        }
    };

    private Backend backend = SILENT_BACKEND;
    private boolean startAttempted;
    private boolean started;
    private boolean closed;

    public synchronized void configure(Backend backend) {
        if (startAttempted || closed) {
            throw new IllegalStateException("Frontend runtime must be configured before service start");
        }
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Override
    public synchronized void start() throws Exception {
        if (started) {
            return;
        }
        if (startAttempted || closed) {
            throw new IllegalStateException("Frontend runtime cannot be restarted");
        }
        startAttempted = true;
        try {
            backend.start();
            started = true;
        } catch (Exception failure) {
            try {
                backend.close();
            } catch (Exception closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            closed = true;
            throw failure;
        }
    }

    @Override
    public synchronized void close() throws Exception {
        if (closed) {
            return;
        }
        try {
            if (startAttempted) {
                backend.close();
            }
        } finally {
            closed = true;
        }
    }
}

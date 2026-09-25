package com.tonikelope.coronapoker.core;

import java.util.Objects;

/** Process-level owner and exactly-once activation gate for frontend audio. */
public final class AudioService implements ApplicationService {

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
    private boolean started;
    private boolean backendStarted;
    private boolean activated;
    private boolean closed;

    /** Configures the concrete frontend backend before process services start. */
    public synchronized void configure(Backend backend) {
        if (started || closed) {
            throw new IllegalStateException("Audio backend must be configured before service start");
        }
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        if (closed) {
            throw new IllegalStateException("Audio service is closed");
        }
        started = true;
    }

    /** Starts music/device work once, at the frontend's characterized boot point. */
    public synchronized void activate() throws Exception {
        if (!started) {
            throw new IllegalStateException("Audio service has not started");
        }
        if (closed) {
            throw new IllegalStateException("Audio service is closed");
        }
        if (activated) {
            return;
        }
        if (backendStarted) {
            throw new IllegalStateException("Audio backend activation previously failed");
        }
        backendStarted = true;
        backend.start();
        activated = true;
    }

    public synchronized boolean isActivated() {
        return activated;
    }

    @Override
    public synchronized void close() throws Exception {
        if (closed) {
            return;
        }
        try {
            if (backendStarted) {
                backend.close();
            }
        } finally {
            closed = true;
        }
    }
}

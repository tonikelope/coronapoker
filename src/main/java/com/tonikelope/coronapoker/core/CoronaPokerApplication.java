package com.tonikelope.coronapoker.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Renderer-neutral process bootstrap and owner of shared service lifecycle.
 */
public final class CoronaPokerApplication implements AutoCloseable {

    private final ApplicationLifecycle lifecycle = new ApplicationLifecycle();
    private final List<ApplicationService> services;
    private int startedServices;

    public CoronaPokerApplication(List<? extends ApplicationService> services) {
        Objects.requireNonNull(services, "services");
        this.services = Collections.unmodifiableList(new ArrayList<>(services));
        if (this.services.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("services contains null");
        }
    }

    public static CoronaPokerApplication withoutServices() {
        return new CoronaPokerApplication(List.of());
    }

    public ApplicationLifecycle lifecycle() {
        return lifecycle;
    }

    /** Returns the single service assignable to {@code type}. */
    public <T extends ApplicationService> T service(Class<T> type) {
        Objects.requireNonNull(type, "type");
        T match = null;
        for (ApplicationService service : services) {
            if (type.isInstance(service)) {
                if (match != null) {
                    throw new IllegalStateException("Multiple application services match " + type.getName());
                }
                match = type.cast(service);
            }
        }
        if (match == null) {
            throw new IllegalArgumentException("Application service not registered: " + type.getName());
        }
        return match;
    }

    /** Starts every shared service exactly once. */
    public synchronized void start() {
        lifecycle.beginStartup();
        try {
            for (ApplicationService service : services) {
                service.start();
                startedServices++;
            }
        } catch (Exception ex) {
            closeStartedServices(ex);
            lifecycle.failed(ex);
            throw new ApplicationStartupException("CoronaPoker application startup failed", ex);
        }
    }

    public void menuReady() {
        lifecycle.menuReady();
    }

    public void sessionOpened() {
        lifecycle.sessionOpened();
    }

    public void tableEntered() {
        lifecycle.tableEntered();
    }

    public void returnedToMenu() {
        lifecycle.menuReady();
    }

    /**
     * Closes services without terminating the JVM. Repeated calls are safe.
     */
    @Override
    public synchronized void close() {
        ApplicationLifecycle.State state = lifecycle.state();
        if (state == ApplicationLifecycle.State.TERMINATED
                || state == ApplicationLifecycle.State.FAILED) {
            return;
        }

        lifecycle.beginShutdown();
        Exception firstFailure = closeStartedServices(null);
        lifecycle.terminated();
        if (firstFailure != null) {
            throw new ApplicationShutdownException("CoronaPoker application shutdown failed", firstFailure);
        }
    }

    public synchronized void fail(Throwable cause) {
        lifecycle.failed(cause);
    }

    private Exception closeStartedServices(Exception primary) {
        Exception firstFailure = primary;
        while (startedServices > 0) {
            ApplicationService service = services.get(--startedServices);
            try {
                service.close();
            } catch (Exception closeFailure) {
                if (firstFailure == null) {
                    firstFailure = closeFailure;
                } else {
                    firstFailure.addSuppressed(closeFailure);
                }
            }
        }
        return firstFailure;
    }
}

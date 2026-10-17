package com.tonikelope.coronapoker.core.network;

import java.util.Objects;

/**
 * Owns a concrete UPnP TCP mapping while the create-game workflow is open.
 *
 * <p>The mapping is opened once when the user explicitly enables UPnP. It is
 * either closed when that workflow is cancelled or transferred, unchanged,
 * to the native lobby which then closes it when the lobby ends.</p>
 */
public final class UpnpPortReservation implements AutoCloseable {

    public enum Status {
        OPEN,
        UNAVAILABLE,
        ALREADY_MAPPED,
        FAILED
    }

    public record Attempt(Status status, UpnpPortReservation reservation) {
        public Attempt {
            Objects.requireNonNull(status, "status");
            if ((status == Status.OPEN) != (reservation != null)) {
                throw new IllegalArgumentException(
                        "Only an open UPnP attempt may return a reservation");
            }
        }

        public boolean opened() {
            return status == Status.OPEN;
        }

        /** Closes the reservation when the caller cannot adopt this result. */
        public void closeIfOpen() {
            if (reservation != null) reservation.close();
        }
    }

    private final int port;
    private UpnpPortMapping mapping;
    private boolean transferred;

    private UpnpPortReservation(int port, UpnpPortMapping mapping) {
        this.port = port;
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    /** Opens and owns the selected TCP port on the system UPnP gateway. */
    public static Attempt openSystemTcp(int port) {
        return openTcp(port, UpnpPortMapping.SYSTEM);
    }

    static Attempt openTcp(int port, UpnpPortMapping.Gateway gateway) {
        UpnpPortMapping.Attempt attempt = UpnpPortMapping.openTcp(port,
                gateway);
        if (!attempt.opened()) {
            return new Attempt(switch (attempt.status()) {
                case UNAVAILABLE -> Status.UNAVAILABLE;
                case ALREADY_MAPPED -> Status.ALREADY_MAPPED;
                case FAILED -> Status.FAILED;
                case OPEN -> throw new IllegalStateException(
                        "Open UPnP attempt has no lease");
            }, null);
        }
        return new Attempt(Status.OPEN,
                new UpnpPortReservation(port, attempt.lease()));
    }

    public int port() {
        return port;
    }

    /** True while this workflow still owns an open router mapping. */
    public synchronized boolean active() {
        return mapping != null && !transferred;
    }

    /** Transfers the already-open mapping to the lobby exactly once. */
    synchronized UpnpPortMapping transferToLobby(int expectedPort) {
        if (expectedPort != port) {
            throw new IllegalArgumentException(
                    "UPnP reservation belongs to TCP port " + port
                            + ", not " + expectedPort);
        }
        if (mapping == null || transferred) {
            throw new IllegalStateException(
                    "UPnP reservation is no longer active");
        }
        UpnpPortMapping result = mapping;
        mapping = null;
        transferred = true;
        return result;
    }

    @Override
    public void close() {
        UpnpPortMapping owned;
        synchronized (this) {
            if (mapping == null || transferred) return;
            owned = mapping;
            mapping = null;
        }
        owned.close();
    }
}

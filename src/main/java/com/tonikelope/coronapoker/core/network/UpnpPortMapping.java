package com.tonikelope.coronapoker.core.network;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.dosse.upnp.UPnP;

/** Owns only the TCP mapping created by this native lobby instance. */
final class UpnpPortMapping implements AutoCloseable {

    enum Status { OPEN, UNAVAILABLE, ALREADY_MAPPED, FAILED }

    record Attempt(Status status, UpnpPortMapping lease) {
        Attempt {
            Objects.requireNonNull(status, "status");
            if ((status == Status.OPEN) != (lease != null)) {
                throw new IllegalArgumentException(
                        "Only an open mapping may return a lease");
            }
        }

        boolean opened() {
            return status == Status.OPEN;
        }
    }

    interface Gateway {
        boolean available();
        boolean mappedTcp(int port);
        boolean openTcp(int port);
        boolean closeTcp(int port);
    }

    private static final Gateway SYSTEM = new Gateway() {
        @Override public boolean available() { return UPnP.isUPnPAvailable(); }
        @Override public boolean mappedTcp(int port) { return UPnP.isMappedTCP(port); }
        @Override public boolean openTcp(int port) { return UPnP.openPortTCP(port); }
        @Override public boolean closeTcp(int port) { return UPnP.closePortTCP(port); }
    };

    private final int port;
    private final Gateway gateway;
    private final AtomicBoolean closed = new AtomicBoolean();

    private UpnpPortMapping(int port, Gateway gateway) {
        this.port = port;
        this.gateway = gateway;
    }

    static Attempt openSystemTcp(int port) {
        return openTcp(port, SYSTEM);
    }

    static Attempt openTcp(int port, Gateway gateway) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Invalid TCP port: " + port);
        }
        Objects.requireNonNull(gateway, "gateway");
        try {
            if (!gateway.available()) {
                return new Attempt(Status.UNAVAILABLE, null);
            }
            // Never claim or later remove a mapping that CoronaPoker did not
            // create. This mirrors the legacy frontend's fail-safe behaviour.
            if (gateway.mappedTcp(port)) {
                return new Attempt(Status.ALREADY_MAPPED, null);
            }
            if (!gateway.openTcp(port)) {
                return new Attempt(Status.FAILED, null);
            }
            return new Attempt(Status.OPEN,
                    new UpnpPortMapping(port, gateway));
        } catch (RuntimeException failure) {
            return new Attempt(Status.FAILED, null);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            gateway.closeTcp(port);
        } catch (RuntimeException ignored) {
            // The local socket must still close even if the router disappears.
        }
    }
}

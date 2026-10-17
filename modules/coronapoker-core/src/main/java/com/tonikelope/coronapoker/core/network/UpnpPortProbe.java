package com.tonikelope.coronapoker.core.network;

/**
 * Performs a reversible UPnP mapping probe for a concrete TCP port.
 *
 * <p>The probe uses the same fail-safe mapping owner as a real lobby: it
 * never removes a mapping that CoronaPoker did not create. A successfully
 * created test mapping is released before this method returns.</p>
 */
public final class UpnpPortProbe {

    /** User-visible result of testing the selected TCP port. */
    public enum Status {
        AVAILABLE,
        UNAVAILABLE,
        ALREADY_MAPPED,
        FAILED
    }

    private UpnpPortProbe() {
    }

    /** Tests the system gateway by briefly mapping and then releasing a port. */
    public static Status probeSystemTcp(int port) {
        return probeTcp(port, UpnpPortMapping.SYSTEM);
    }

    static Status probeTcp(int port, UpnpPortMapping.Gateway gateway) {
        UpnpPortMapping.Attempt attempt = UpnpPortMapping.openTcp(port,
                gateway);
        if (!attempt.opened()) {
            return switch (attempt.status()) {
                case UNAVAILABLE -> Status.UNAVAILABLE;
                case ALREADY_MAPPED -> Status.ALREADY_MAPPED;
                case FAILED -> Status.FAILED;
                case OPEN -> throw new IllegalStateException(
                        "Open UPnP attempt has no lease");
            };
        }
        attempt.lease().close();
        return Status.AVAILABLE;
    }
}

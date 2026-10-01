/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxLocalServerAddressesTest {

    @Test
    void keepsLocalhostFirstAndOffersDistinctUsableIpv4Addresses()
            throws Exception {
        List<String> addresses = GdxLocalServerAddresses.fromAddresses(List.of(
                InetAddress.getByName("192.168.1.20"),
                InetAddress.getByName("10.0.0.8"),
                InetAddress.getByName("192.168.1.20"),
                InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("0.0.0.0"),
                InetAddress.getByName("169.254.12.4"),
                InetAddress.getByName("224.0.0.1"),
                InetAddress.getByName("2001:db8::1")));

        assertEquals(List.of("localhost", "10.0.0.8", "192.168.1.20"),
                addresses);
    }

    @Test
    void alwaysKeepsLocalhostWhenNoInterfaceAddressIsUsable()
            throws Exception {
        assertEquals(List.of("localhost"),
                GdxLocalServerAddresses.fromAddresses(List.of(
                        InetAddress.getByName("127.0.0.1"),
                        InetAddress.getByName("::1"))));
    }
}

/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;

/** Addresses that are useful to advertise for a locally hosted table. */
final class GdxLocalServerAddresses {

    static final String LOOPBACK_NAME = "localhost";

    private GdxLocalServerAddresses() {
    }

    static List<String> discover() {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return List.of(LOOPBACK_NAME);
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> interfaceAddresses =
                        networkInterface.getInetAddresses();
                while (interfaceAddresses.hasMoreElements()) {
                    addresses.add(interfaceAddresses.nextElement());
                }
            }
        } catch (SocketException unavailable) {
            return List.of(LOOPBACK_NAME);
        }
        return fromAddresses(addresses);
    }

    static List<String> fromAddresses(Collection<InetAddress> addresses) {
        TreeSet<String> usable = new TreeSet<>();
        for (InetAddress address : addresses) {
            if (address instanceof Inet4Address
                    && !address.isAnyLocalAddress()
                    && !address.isLoopbackAddress()
                    && !address.isLinkLocalAddress()
                    && !address.isMulticastAddress()) {
                usable.add(address.getHostAddress());
            }
        }
        List<String> result = new ArrayList<>(usable.size() + 1);
        result.add(LOOPBACK_NAME);
        result.addAll(usable);
        return List.copyOf(result);
    }
}

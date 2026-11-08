/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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

    static String displayEndpoint(boolean host, String endpoint, String lanAddress) {
        if (!host || LOOPBACK_NAME.equals(lanAddress)) return endpoint;
        int separator = endpoint.lastIndexOf(':');
        if (separator < 0 || separator == endpoint.length() - 1) return endpoint;
        String address = endpoint.substring(0, separator);
        if (LOOPBACK_NAME.equalsIgnoreCase(address) || "127.0.0.1".equals(address)
                || "[::1]".equals(address) || "::1".equals(address)) {
            return lanAddress + endpoint.substring(separator);
        }
        return endpoint;
    }

    static String preferredAddress(List<String> addresses) {
        if (addresses.size() < 2) return LOOPBACK_NAME;
        if (addresses.size() == 2) return addresses.get(1);
        try (DatagramSocket route = new DatagramSocket()) {
            // Ask the OS which source IPv4 its default route uses. Send no data.
            InetAddress defaultRouteProbe = InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 1});
            route.connect(new InetSocketAddress(defaultRouteProbe, 9));
            String address = route.getLocalAddress().getHostAddress();
            if (addresses.contains(address)) return address;
        } catch (IOException | SecurityException unavailable) {
            // Without a default route, use a discovered interface address.
        }
        return addresses.get(1);
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

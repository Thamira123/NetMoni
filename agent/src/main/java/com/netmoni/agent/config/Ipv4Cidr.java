package com.netmoni.agent.config;

import java.util.ArrayList;
import java.util.List;

public record Ipv4Cidr(long network, int prefixLength) {
    public static final int MAX_SCAN_HOSTS = 4096;

    public Ipv4Cidr {
        if (prefixLength < 0 || prefixLength > 32) {
            throw new IllegalArgumentException("IPv4 prefix length must be between 0 and 32");
        }
        long mask = prefixLength == 0 ? 0 : (0xffff_ffffL << (32 - prefixLength)) & 0xffff_ffffL;
        network &= mask;
    }

    public static Ipv4Cidr parse(String value) {
        String[] parts = value == null ? new String[0] : value.trim().split("/", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("CIDR must use IPv4 notation, for example 192.168.1.0/24");
        }
        return new Ipv4Cidr(parseAddress(parts[0]), Integer.parseInt(parts[1]));
    }

    public static long parseAddress(String address) {
        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalArgumentException("Expected an IPv4 address: " + address);
        }
        long result = 0;
        for (String octet : octets) {
            int value;
            try {
                if (octet.isEmpty() || (octet.length() > 1 && octet.charAt(0) == '+')) {
                    throw new NumberFormatException();
                }
                value = Integer.parseInt(octet);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + address, exception);
            }
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + address);
            }
            result = (result << 8) | value;
        }
        return result;
    }

    public boolean contains(String address) {
        long mask = prefixLength == 0 ? 0 : (0xffff_ffffL << (32 - prefixLength)) & 0xffff_ffffL;
        return (parseAddress(address) & mask) == network;
    }

    @Override
    public String toString() {
        return formatAddress(network) + "/" + prefixLength;
    }

    public List<String> hosts(int maxHosts) {
        long count = 1L << (32 - prefixLength);
        long first = network;
        long last = network + count - 1;
        if (prefixLength <= 30) {
            first++;
            last--;
        }
        long hostCount = Math.max(0, last - first + 1);
        if (hostCount > Math.min(maxHosts, MAX_SCAN_HOSTS)) {
            throw new IllegalArgumentException("Subnet has " + hostCount
                    + " usable addresses; the scan limit is " + Math.min(maxHosts, MAX_SCAN_HOSTS));
        }
        List<String> addresses = new ArrayList<>((int) hostCount);
        for (long ip = first; ip <= last; ip++) {
            addresses.add(formatAddress(ip));
        }
        return List.copyOf(addresses);
    }

    private static String formatAddress(long value) {
        return ((value >>> 24) & 255) + "." + ((value >>> 16) & 255) + "."
                + ((value >>> 8) & 255) + "." + (value & 255);
    }
}

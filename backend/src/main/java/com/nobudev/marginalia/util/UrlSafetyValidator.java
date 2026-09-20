package com.nobudev.marginalia.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Utility for validating URLs against Server-Side Request Forgery (SSRF).
 * Rejects non-HTTP(S) schemes, unresolvable hosts, loopback addresses,
 * private network ranges (RFC 1918, RFC 4193), link-local addresses
 * (including cloud metadata services at 169.254.169.254), and multicast addresses.
 */
public final class UrlSafetyValidator {

    private UrlSafetyValidator() {
        // Utility class
    }

    public static void validate(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new IllegalArgumentException("URL must not be empty");
        }

        URI uri;
        try {
            uri = URI.create(urlString.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL syntax: " + urlString, e);
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URL scheme must not be null");
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Invalid URL scheme: " + scheme + ". Only http and https are permitted.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must contain a valid host");
        }

        String lowerHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(lowerHost) || lowerHost.endsWith(".localhost")) {
            throw new IllegalArgumentException("Access to localhost is blocked: " + host);
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve host: " + host, e);
        }

        for (InetAddress addr : addresses) {
            if (isUnsafeAddress(addr)) {
                throw new IllegalArgumentException("Access to internal/private IP address is blocked: " + addr.getHostAddress());
            }
        }
    }

    public static boolean isUnsafeAddress(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }

        if (addr instanceof Inet4Address) {
            return isUnsafeIpv4(addr.getAddress());
        } else if (addr instanceof Inet6Address) {
            return isUnsafeIpv6(addr.getAddress());
        }

        return true;
    }

    private static boolean isUnsafeIpv4(byte[] b) {
        int b0 = b[0] & 0xFF;
        int b1 = b[1] & 0xFF;

        // 0.0.0.0/8 (Current network)
        if (b0 == 0) return true;

        // 10.0.0.0/8 (RFC 1918 Private)
        if (b0 == 10) return true;

        // 127.0.0.0/8 (Loopback)
        if (b0 == 127) return true;

        // 169.254.0.0/16 (Link-local, including 169.254.169.254 AWS metadata)
        if (b0 == 169 && b1 == 254) return true;

        // 172.16.0.0/12 (RFC 1918 Private: 172.16.0.0 - 172.31.255.255)
        if (b0 == 172 && b1 >= 16 && b1 <= 31) return true;

        // 192.168.0.0/16 (RFC 1918 Private)
        if (b0 == 192 && b1 == 168) return true;

        // 100.64.0.0/10 (Shared Address Space / CGNAT: 100.64.0.0 - 100.127.255.255)
        if (b0 == 100 && (b1 & 0xC0) == 64) return true;

        // 198.18.0.0/15 (Benchmarking)
        if (b0 == 198 && (b1 & 0xFE) == 18) return true;

        return false;
    }

    private static boolean isUnsafeIpv6(byte[] b) {
        // Check for IPv4-mapped IPv6 address: ::ffff:a.b.c.d
        boolean isIpv4Mapped = true;
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                isIpv4Mapped = false;
                break;
            }
        }
        if (isIpv4Mapped && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
            byte[] ipv4Bytes = new byte[4];
            System.arraycopy(b, 12, ipv4Bytes, 0, 4);
            return isUnsafeIpv4(ipv4Bytes);
        }

        // Loopback ::1
        boolean isLoopback = true;
        for (int i = 0; i < 15; i++) {
            if (b[i] != 0) {
                isLoopback = false;
                break;
            }
        }
        if (isLoopback && b[15] == 1) return true;

        // Unspecified ::
        boolean isUnspecified = true;
        for (int i = 0; i < 16; i++) {
            if (b[i] != 0) {
                isUnspecified = false;
                break;
            }
        }
        if (isUnspecified) return true;

        int b0 = b[0] & 0xFF;
        int b1 = b[1] & 0xFF;

        // Unique Local Address (ULA) fc00::/7 (fc00:: - fdff::)
        if ((b0 & 0xFE) == 0xFC) return true;

        // Link-local unicast fe80::/10
        if (b0 == 0xFE && (b1 & 0xC0) == 0x80) return true;

        return false;
    }
}

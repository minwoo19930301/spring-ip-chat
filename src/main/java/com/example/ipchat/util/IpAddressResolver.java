package com.example.ipchat.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Component
public class IpAddressResolver {

    public String resolve(HttpHeaders headers, String remoteAddress) {
        String fallback = normalize(remoteAddress);
        if (shouldTrustForwardedHeaders(fallback)) {
            String fromCloudflare = normalize(headers.getFirst("CF-Connecting-IP"));
            if (!fromCloudflare.isBlank()) {
                return fromCloudflare;
            }

            String fromForwardedFor = firstIp(headers.getFirst("X-Forwarded-For"));
            if (!fromForwardedFor.isBlank()) {
                return fromForwardedFor;
            }

            String fromRealIp = normalize(headers.getFirst("X-Real-IP"));
            if (!fromRealIp.isBlank()) {
                return fromRealIp;
            }

            String fromForwarded = parseForwarded(headers.getFirst("Forwarded"));
            if (!fromForwarded.isBlank()) {
                return fromForwarded;
            }
        }

        return fallback.isBlank() ? "unknown" : fallback;
    }

    public String resolve(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("CF-Connecting-IP", request.getHeader("CF-Connecting-IP"));
        headers.add("X-Forwarded-For", request.getHeader("X-Forwarded-For"));
        headers.add("X-Real-IP", request.getHeader("X-Real-IP"));
        headers.add("Forwarded", request.getHeader("Forwarded"));
        return resolve(headers, request.getRemoteAddr());
    }

    private String firstIp(String rawHeader) {
        String normalized = normalize(rawHeader);
        if (normalized.isBlank()) {
            return "";
        }
        int commaIndex = normalized.indexOf(',');
        if (commaIndex == -1) {
            return normalized;
        }
        return normalize(normalized.substring(0, commaIndex));
    }

    private String parseForwarded(String forwardedHeader) {
        String normalized = normalize(forwardedHeader);
        if (normalized.isBlank()) {
            return "";
        }

        for (String segment : normalized.split(";")) {
            String trimmed = segment.trim();
            if (!trimmed.regionMatches(true, 0, "for=", 0, 4)) {
                continue;
            }
            String value = trimmed.substring(4).replace("\"", "").trim();
            if (value.startsWith("[")) {
                int end = value.indexOf(']');
                if (end > 0) {
                    return value.substring(1, end);
                }
            }
            int colonIndex = value.indexOf(':');
            if (colonIndex > -1 && value.indexOf('.') > -1) {
                return value.substring(0, colonIndex);
            }
            return value;
        }
        return "";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private boolean shouldTrustForwardedHeaders(String remoteAddress) {
        if (remoteAddress.isBlank()) {
            return false;
        }

        try {
            InetAddress address = InetAddress.getByName(remoteAddress);
            return address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || isCloudflare(address);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    // https://www.cloudflare.com/ips/
    private static final List<String> CLOUDFLARE_RANGES = List.of(
            "173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22",
            "141.101.64.0/18", "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20",
            "197.234.240.0/22", "198.41.128.0/17", "162.158.0.0/15", "104.16.0.0/13",
            "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22",
            "2400:cb00::/32", "2606:4700::/32", "2803:f800::/32", "2405:b500::/32",
            "2405:8100::/32", "2a06:98c0::/29", "2c0f:f248::/32");

    private boolean isCloudflare(InetAddress address) {
        byte[] ip = address.getAddress();
        for (String cidr : CLOUDFLARE_RANGES) {
            try {
                String[] parts = cidr.split("/");
                byte[] net = InetAddress.getByName(parts[0]).getAddress();
                if (net.length != ip.length) {
                    continue;
                }
                int bits = Integer.parseInt(parts[1]);
                boolean match = true;
                for (int i = 0; i < net.length && bits > 0; i++, bits -= 8) {
                    int mask = bits >= 8 ? 0xFF : (0xFF << (8 - bits)) & 0xFF;
                    if ((ip[i] & mask) != (net[i] & mask)) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return true;
                }
            } catch (UnknownHostException ignored) {
                // literal ranges never fail
            }
        }
        return false;
    }
}

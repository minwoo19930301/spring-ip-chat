package com.example.ipchat.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class IpAddressResolver {

    public String resolve(HttpHeaders headers, String remoteAddress) {
        String fallback = normalize(remoteAddress);
        if (shouldTrustForwardedHeaders(fallback)) {
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
                    || address.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}

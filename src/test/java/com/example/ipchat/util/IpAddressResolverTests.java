package com.example.ipchat.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IpAddressResolverTests {

    private final IpAddressResolver ipAddressResolver = new IpAddressResolver();

    @Test
    void ignoresForwardedHeadersForDirectPublicRequests() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "1.2.3.4");
        headers.add("X-Real-IP", "5.6.7.8");

        String resolvedIp = ipAddressResolver.resolve(headers, "203.0.113.10");

        assertEquals("203.0.113.10", resolvedIp);
    }

    @Test
    void trustsForwardedHeadersForLocalProxyRequests() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "1.2.3.4, 127.0.0.1");

        String resolvedIp = ipAddressResolver.resolve(headers, "127.0.0.1");

        assertEquals("1.2.3.4", resolvedIp);
    }
}

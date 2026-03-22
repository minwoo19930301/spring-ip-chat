package com.example.ipchat.websocket;

import com.example.ipchat.util.IpAddressResolver;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.InetSocketAddress;
import java.util.Map;

@Component
public class ClientIpHandshakeInterceptor implements HandshakeInterceptor {

    public static final String SESSION_IP_KEY = "clientIp";

    private final IpAddressResolver ipAddressResolver;

    public ClientIpHandshakeInterceptor(IpAddressResolver ipAddressResolver) {
        this.ipAddressResolver = ipAddressResolver;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        String remoteIp = remoteAddress == null || remoteAddress.getAddress() == null
                ? ""
                : remoteAddress.getAddress().getHostAddress();

        String resolvedIp = ipAddressResolver.resolve(request.getHeaders(), remoteIp);
        attributes.put(SESSION_IP_KEY, resolvedIp);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // No-op
    }
}

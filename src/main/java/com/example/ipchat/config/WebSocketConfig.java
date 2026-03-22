package com.example.ipchat.config;

import com.example.ipchat.websocket.ClientIpHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ClientIpHandshakeInterceptor clientIpHandshakeInterceptor;

    public WebSocketConfig(ClientIpHandshakeInterceptor clientIpHandshakeInterceptor) {
        this.clientIpHandshakeInterceptor = clientIpHandshakeInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .addInterceptors(clientIpHandshakeInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}

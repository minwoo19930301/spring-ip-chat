package com.example.ipchat.chat;

import com.example.ipchat.chat.dto.ChatIncomingMessage;
import com.example.ipchat.chat.dto.ChatMessageResponse;
import com.example.ipchat.websocket.ClientIpHandshakeInterceptor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class ChatSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatSocketController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(ChatIncomingMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String senderIp = resolveSenderIp(headerAccessor);
        try {
            ChatMessageResponse saved = chatService.saveMessage(senderIp, message == null ? null : message.content());
            messagingTemplate.convertAndSend("/topic/public", saved);
        } catch (IllegalArgumentException ignored) {
            // Ignore empty/invalid messages.
        }
    }

    private String resolveSenderIp(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return "unknown";
        }
        Object value = sessionAttributes.get(ClientIpHandshakeInterceptor.SESSION_IP_KEY);
        if (value instanceof String ip && !ip.isBlank()) {
            return ip;
        }
        return "unknown";
    }
}

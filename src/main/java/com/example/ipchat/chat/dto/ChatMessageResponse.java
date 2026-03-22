package com.example.ipchat.chat.dto;

import com.example.ipchat.chat.ChatMessage;
import com.example.ipchat.chat.RedisQueuedChatMessage;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ChatMessageResponse(Long id, String messageKey, String senderIp, String content, Instant sentAt) {

    public static ChatMessageResponse fromEntity(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getMessageKey(),
                message.getSenderIp(),
                message.getContent(),
                message.getSentAt()
        );
    }

    public static ChatMessageResponse fromQueued(RedisQueuedChatMessage message) {
        return new ChatMessageResponse(
                null,
                message.messageKey(),
                message.senderIp(),
                message.content(),
                message.sentAt()
        );
    }

    @JsonProperty("messageRef")
    public String messageRef() {
        if (messageKey != null && !messageKey.isBlank()) {
            return messageKey;
        }
        return id == null ? null : Long.toString(id);
    }
}

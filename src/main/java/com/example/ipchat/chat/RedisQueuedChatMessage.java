package com.example.ipchat.chat;

import java.time.Instant;

public record RedisQueuedChatMessage(String messageKey, String senderIp, String content, Instant sentAt) {
}

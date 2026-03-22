package com.example.ipchat.chat;

import com.example.ipchat.chat.dto.ChatMessageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class RedisChatBuffer {

    private static final Logger log = LoggerFactory.getLogger(RedisChatBuffer.class);
    private static final String PENDING_MESSAGES_KEY = "chat:pending:messages";
    private static final String PENDING_ORDER_KEY = "chat:pending:order";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RedisChatBuffer(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${chat.redis.enabled:false}") boolean enabled
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ChatMessageResponse enqueueMessage(String senderIp, String content, Instant sentAt) {
        RedisQueuedChatMessage message = new RedisQueuedChatMessage(
                UUID.randomUUID().toString(),
                senderIp,
                content,
                sentAt
        );

        if (enabled) {
            String payload = writeJson(message);
            redisTemplate.opsForHash().put(PENDING_MESSAGES_KEY, message.messageKey(), payload);
            redisTemplate.opsForZSet().add(PENDING_ORDER_KEY, message.messageKey(), sentAt.toEpochMilli());
        }

        return ChatMessageResponse.fromQueued(message);
    }

    public RedisQueuedChatMessage findPendingMessage(String messageKey) {
        if (!enabled || messageKey == null || messageKey.isBlank()) {
            return null;
        }

        Object payload = redisTemplate.opsForHash().get(PENDING_MESSAGES_KEY, messageKey);
        if (!(payload instanceof String rawPayload) || rawPayload.isBlank()) {
            return null;
        }

        return readJson(rawPayload);
    }

    public boolean removePendingMessage(String messageKey) {
        if (!enabled || messageKey == null || messageKey.isBlank()) {
            return false;
        }

        Long removedFromHash = redisTemplate.opsForHash().delete(PENDING_MESSAGES_KEY, messageKey);
        Long removedFromOrder = redisTemplate.opsForZSet().remove(PENDING_ORDER_KEY, messageKey);
        return wasRemoved(removedFromHash) || wasRemoved(removedFromOrder);
    }

    public List<ChatMessageResponse> getRecentMessages(int limit) {
        if (!enabled || limit <= 0) {
            return List.of();
        }

        Set<String> keys = redisTemplate.opsForZSet().reverseRange(PENDING_ORDER_KEY, 0, limit - 1L);
        return getMessagesForKeys(keys).stream()
                .map(ChatMessageResponse::fromQueued)
                .toList();
    }

    public List<RedisQueuedChatMessage> getFlushBatch(int batchSize) {
        if (!enabled || batchSize <= 0) {
            return List.of();
        }

        Set<String> keys = redisTemplate.opsForZSet().range(PENDING_ORDER_KEY, 0, batchSize - 1L);
        return getMessagesForKeys(keys);
    }

    private List<RedisQueuedChatMessage> getMessagesForKeys(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<String> orderedKeys = new ArrayList<>(keys);
        List<Object> rawPayloads = redisTemplate.opsForHash().multiGet(PENDING_MESSAGES_KEY, new ArrayList<>(orderedKeys));
        List<RedisQueuedChatMessage> messages = new ArrayList<>();

        for (int index = 0; index < orderedKeys.size(); index++) {
            String messageKey = orderedKeys.get(index);
            Object payload = rawPayloads == null || rawPayloads.size() <= index ? null : rawPayloads.get(index);

            if (!(payload instanceof String rawPayload) || rawPayload.isBlank()) {
                removePendingMessage(messageKey);
                continue;
            }

            try {
                messages.add(readJson(rawPayload));
            } catch (UncheckedIOException e) {
                log.warn("Removing unreadable pending Redis message {}", messageKey, e);
                removePendingMessage(messageKey);
            }
        }

        messages.sort(Comparator
                .comparing(RedisQueuedChatMessage::sentAt)
                .thenComparing(RedisQueuedChatMessage::messageKey));
        return messages;
    }

    private String writeJson(RedisQueuedChatMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private RedisQueuedChatMessage readJson(String payload) {
        try {
            return objectMapper.readValue(payload, RedisQueuedChatMessage.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean wasRemoved(Long value) {
        return value != null && value > 0;
    }
}

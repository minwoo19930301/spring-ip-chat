package com.example.ipchat.chat;

import com.example.ipchat.chat.dto.ChatMessageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ChatService {

    private static final int MAX_LIMIT = 500;

    private final ChatMessageRepository chatMessageRepository;
    private final RedisChatBuffer redisChatBuffer;

    public ChatService(ChatMessageRepository chatMessageRepository, RedisChatBuffer redisChatBuffer) {
        this.chatMessageRepository = chatMessageRepository;
        this.redisChatBuffer = redisChatBuffer;
    }

    @Transactional
    public ChatMessageResponse saveMessage(String senderIp, String content) {
        String cleanIp = normalizeIp(senderIp);
        String cleanContent = normalizeContent(content);
        Instant sentAt = Instant.now();

        if (redisChatBuffer.isEnabled()) {
            return redisChatBuffer.enqueueMessage(cleanIp, cleanContent, sentAt);
        }

        ChatMessage savedMessage = saveDirectlyToDatabase(
                UUID.randomUUID().toString(),
                cleanIp,
                cleanContent,
                sentAt
        );
        return ChatMessageResponse.fromEntity(savedMessage);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getRecentMessages(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<ChatMessageResponse> persistedMessages = chatMessageRepository.findRecentMessages(PageRequest.of(0, safeLimit)).stream()
                .map(ChatMessageResponse::fromEntity)
                .sorted(Comparator
                        .comparing(ChatMessageResponse::sentAt)
                        .thenComparing(message -> safeMessageRef(message.messageRef())))
                .toList();

        if (!redisChatBuffer.isEnabled()) {
            return persistedMessages;
        }

        List<ChatMessageResponse> pendingMessages = redisChatBuffer.getRecentMessages(safeLimit);
        return mergeRecentMessages(persistedMessages, pendingMessages, safeLimit);
    }

    @Transactional
    public int flushPendingMessages(int batchSize) {
        if (!redisChatBuffer.isEnabled() || batchSize <= 0) {
            return 0;
        }

        int flushedCount = 0;
        for (RedisQueuedChatMessage queuedMessage : redisChatBuffer.getFlushBatch(batchSize)) {
            if (!chatMessageRepository.existsByMessageKey(queuedMessage.messageKey())) {
                saveDirectlyToDatabase(
                        queuedMessage.messageKey(),
                        queuedMessage.senderIp(),
                        queuedMessage.content(),
                        queuedMessage.sentAt()
                );
            }

            redisChatBuffer.removePendingMessage(queuedMessage.messageKey());
            flushedCount++;
        }

        return flushedCount;
    }

    @Transactional
    public String deleteMessage(String messageRef, String requesterIp) {
        String cleanIp = normalizeIp(requesterIp);

        RedisQueuedChatMessage pendingMessage = redisChatBuffer.findPendingMessage(messageRef);
        if (pendingMessage != null) {
            if (!cleanIp.equals(pendingMessage.senderIp())) {
                throw new IllegalStateException("자기 메시지만 삭제할 수 있습니다.");
            }
            redisChatBuffer.removePendingMessage(messageRef);
            return messageRef;
        }

        ChatMessage message = resolvePersistedMessage(messageRef);
        if (!cleanIp.equals(message.getSenderIp())) {
            throw new IllegalStateException("자기 메시지만 삭제할 수 있습니다.");
        }

        chatMessageRepository.delete(message);
        return toMessageRef(ChatMessageResponse.fromEntity(message));
    }

    private ChatMessage saveDirectlyToDatabase(String messageKey, String senderIp, String content, Instant sentAt) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setMessageKey(messageKey);
        chatMessage.setSenderIp(senderIp);
        chatMessage.setContent(content);
        chatMessage.setSentAt(sentAt);
        return chatMessageRepository.save(chatMessage);
    }

    private List<ChatMessageResponse> mergeRecentMessages(
            List<ChatMessageResponse> persistedMessages,
            List<ChatMessageResponse> pendingMessages,
            int limit
    ) {
        Map<String, ChatMessageResponse> mergedByRef = new LinkedHashMap<>();

        for (ChatMessageResponse message : persistedMessages) {
            mergedByRef.put(toMessageRef(message), message);
        }

        for (ChatMessageResponse message : pendingMessages) {
            mergedByRef.put(toMessageRef(message), message);
        }

        List<ChatMessageResponse> mergedMessages = new ArrayList<>(mergedByRef.values());
        mergedMessages.sort(Comparator
                .comparing(ChatMessageResponse::sentAt)
                .thenComparing(message -> safeMessageRef(message.messageRef())));

        if (mergedMessages.size() <= limit) {
            return mergedMessages;
        }

        return new ArrayList<>(mergedMessages.subList(mergedMessages.size() - limit, mergedMessages.size()));
    }

    private ChatMessage resolvePersistedMessage(String messageRef) {
        if (messageRef != null && !messageRef.isBlank()) {
            ChatMessage byMessageKey = chatMessageRepository.findByMessageKey(messageRef).orElse(null);
            if (byMessageKey != null) {
                return byMessageKey;
            }
        }

        try {
            long numericId = Long.parseLong(messageRef);
            return chatMessageRepository.findById(numericId)
                    .orElseThrow(() -> new NoSuchElementException("메시지를 찾을 수 없습니다."));
        } catch (NumberFormatException ignored) {
            throw new NoSuchElementException("메시지를 찾을 수 없습니다.");
        }
    }

    private String toMessageRef(ChatMessageResponse message) {
        String messageRef = message.messageRef();
        if (messageRef == null || messageRef.isBlank()) {
            throw new IllegalStateException("메시지 식별자가 없습니다.");
        }
        return messageRef;
    }

    private String safeMessageRef(String messageRef) {
        return messageRef == null ? "" : messageRef;
    }

    private String normalizeIp(String senderIp) {
        if (senderIp == null || senderIp.isBlank()) {
            return "unknown";
        }
        return senderIp.trim();
    }

    private String normalizeContent(String content) {
        if (content == null) {
            throw new IllegalArgumentException("메시지 내용이 비어 있습니다.");
        }
        String normalized = content.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("메시지 내용이 비어 있습니다.");
        }
        if (normalized.length() > 2000) {
            return normalized.substring(0, 2000);
        }
        return normalized;
    }
}

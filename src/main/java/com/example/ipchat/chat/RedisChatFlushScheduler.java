package com.example.ipchat.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RedisChatFlushScheduler {

    private static final Logger log = LoggerFactory.getLogger(RedisChatFlushScheduler.class);

    private final ChatService chatService;
    private final boolean redisEnabled;
    private final int flushBatchSize;

    public RedisChatFlushScheduler(
            ChatService chatService,
            @Value("${chat.redis.enabled:false}") boolean redisEnabled,
            @Value("${chat.redis.flush-batch-size:100}") int flushBatchSize
    ) {
        this.chatService = chatService;
        this.redisEnabled = redisEnabled;
        this.flushBatchSize = flushBatchSize;
    }

    @Scheduled(
            fixedDelayString = "${chat.redis.flush-interval-ms:5000}",
            initialDelayString = "${chat.redis.flush-initial-delay-ms:5000}"
    )
    public void flushPendingMessages() {
        if (!redisEnabled) {
            return;
        }

        int flushedCount = chatService.flushPendingMessages(Math.max(1, flushBatchSize));
        if (flushedCount > 0) {
            log.info("Flushed {} Redis-backed chat messages to the database", flushedCount);
        }
    }
}

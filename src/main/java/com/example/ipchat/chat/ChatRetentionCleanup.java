package com.example.ipchat.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class ChatRetentionCleanup {

    private static final Logger log = LoggerFactory.getLogger(ChatRetentionCleanup.class);

    private final ChatMessageRepository chatMessageRepository;
    private final long retentionDays;

    public ChatRetentionCleanup(
            ChatMessageRepository chatMessageRepository,
            @Value("${chat.retention-days:30}") long retentionDays
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(
            fixedDelayString = "${chat.retention.cleanup-interval-ms:3600000}",
            initialDelayString = "${chat.retention.cleanup-initial-delay-ms:60000}"
    )
    @Transactional
    public void cleanupExpiredMessages() {
        if (retentionDays <= 0) {
            return;
        }

        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long deletedCount = chatMessageRepository.deleteBySentAtBefore(cutoff);

        if (deletedCount > 0) {
            log.info("Deleted {} chat messages older than {} days", deletedCount, retentionDays);
        }
    }
}

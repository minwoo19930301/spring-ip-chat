package com.example.ipchat.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("select m from ChatMessage m order by m.sentAt desc")
    List<ChatMessage> findRecentMessages(Pageable pageable);

    long deleteBySentAtBefore(Instant cutoff);

    Optional<ChatMessage> findByMessageKey(String messageKey);

    boolean existsByMessageKey(String messageKey);
}

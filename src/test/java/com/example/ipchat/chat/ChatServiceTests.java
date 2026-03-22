package com.example.ipchat.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTests {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private RedisChatBuffer redisChatBuffer;

    @InjectMocks
    private ChatService chatService;

    @Test
    void deleteMessageRemovesOwnMessage() {
        ChatMessage message = new ChatMessage();
        message.setMessageKey("message-1");
        message.setSenderIp("1.2.3.4");
        message.setContent("hello");
        message.setSentAt(Instant.now());

        when(redisChatBuffer.findPendingMessage("1")).thenReturn(null);
        when(chatMessageRepository.findByMessageKey("1")).thenReturn(Optional.empty());
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(message));

        String deletedMessageRef = chatService.deleteMessage("1", "1.2.3.4");

        assertEquals("message-1", deletedMessageRef);
        verify(chatMessageRepository).delete(message);
    }

    @Test
    void deleteMessageRejectsDifferentIp() {
        ChatMessage message = new ChatMessage();
        message.setMessageKey("message-1");
        message.setSenderIp("1.2.3.4");
        message.setContent("hello");
        message.setSentAt(Instant.now());

        when(redisChatBuffer.findPendingMessage("1")).thenReturn(null);
        when(chatMessageRepository.findByMessageKey("1")).thenReturn(Optional.empty());
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(message));

        assertThrows(IllegalStateException.class, () -> chatService.deleteMessage("1", "5.6.7.8"));
        verify(chatMessageRepository, never()).delete(message);
    }

    @Test
    void deleteMessageFailsWhenMissing() {
        when(redisChatBuffer.findPendingMessage("999")).thenReturn(null);
        when(chatMessageRepository.findByMessageKey("999")).thenReturn(Optional.empty());
        when(chatMessageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> chatService.deleteMessage("999", "1.2.3.4"));
    }
}

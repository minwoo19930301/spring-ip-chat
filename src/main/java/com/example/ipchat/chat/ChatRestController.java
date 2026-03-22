package com.example.ipchat.chat;

import com.example.ipchat.chat.dto.ChatMessageResponse;
import com.example.ipchat.chat.dto.ChatMessageDeletedResponse;
import com.example.ipchat.util.IpAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
public class ChatRestController {

    private final ChatService chatService;
    private final IpAddressResolver ipAddressResolver;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatRestController(
            ChatService chatService,
            IpAddressResolver ipAddressResolver,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.chatService = chatService;
        this.ipAddressResolver = ipAddressResolver;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/api/messages")
    public List<ChatMessageResponse> getMessages(@RequestParam(defaultValue = "100") int limit) {
        return chatService.getRecentMessages(limit);
    }

    @GetMapping("/api/me")
    public Map<String, String> myIdentity(HttpServletRequest request) {
        return Map.of("ip", ipAddressResolver.resolve(request));
    }

    @DeleteMapping("/api/messages/{messageRef}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String messageRef, HttpServletRequest request) {
        String requesterIp = ipAddressResolver.resolve(request);

        try {
            String deletedMessageRef = chatService.deleteMessage(messageRef, requesterIp);
            messagingTemplate.convertAndSend(
                    "/topic/public.delete",
                    new ChatMessageDeletedResponse(deletedMessageRef)
            );
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
}

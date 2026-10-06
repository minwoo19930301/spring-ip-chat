package com.example.ipchat.chat;

import com.example.ipchat.chat.dto.ChatMessageResponse;
import com.example.ipchat.chat.dto.ChatMessageDeletedResponse;
import com.example.ipchat.util.IpAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.NoSuchElementException;

/** Intentionally public editing API, as requested by the site owner. */
@RestController
public class GodController {
    private final ChatService service;
    private final SimpMessagingTemplate messaging;
    private final IpAddressResolver ips;
    public GodController(ChatService service, SimpMessagingTemplate messaging, IpAddressResolver ips) {
        this.service=service; this.messaging=messaging; this.ips=ips;
    }
    public record Content(String content) {}
    @GetMapping({"/god", "/god/"})
    public ResponseEntity<Void> page() {
        return ResponseEntity.status(302).header("Location", "/god.html").build();
    }
    @PostMapping("/api/god/messages")
    public ChatMessageResponse create(@RequestBody Content body, HttpServletRequest request) {
        var result=service.saveMessage(ips.resolve(request), body.content());
        messaging.convertAndSend("/topic/public", result); return result;
    }
    @PutMapping("/api/god/messages/{ref}")
    public ChatMessageResponse update(@PathVariable String ref, @RequestBody Content body) {
        var result=service.editMessage(ref, body.content());
        messaging.convertAndSend("/topic/public", result); return result;
    }
    @DeleteMapping("/api/god/messages/{ref}")
    public ResponseEntity<Void> delete(@PathVariable String ref) {
        String deleted=service.deleteAnyMessage(ref);
        messaging.convertAndSend("/topic/public.delete", new ChatMessageDeletedResponse(deleted));
        return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<String> missing(NoSuchElementException e) {return ResponseEntity.status(404).body(e.getMessage());}
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> invalid(IllegalArgumentException e) {return ResponseEntity.badRequest().body(e.getMessage());}
}

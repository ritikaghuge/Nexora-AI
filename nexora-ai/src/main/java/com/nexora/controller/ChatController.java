package com.nexora.controller;

import com.nexora.dto.Dtos.ChatRequest;
import com.nexora.security.CurrentUserService;
import com.nexora.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chat;
    private final CurrentUserService current;

    public ChatController(ChatService chat, CurrentUserService current) {
        this.chat = chat;
        this.current = current;
    }

    /** Server-Sent Events: meta, status, sources, token, error, done. */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest req, Authentication a) {
        return chat.stream(current.require(a), req);
    }
}

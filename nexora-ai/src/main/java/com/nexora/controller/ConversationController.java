package com.nexora.controller;

import com.nexora.dto.Dtos.ConversationDto;
import com.nexora.dto.Dtos.MessageDto;
import com.nexora.dto.Dtos.RenameRequest;
import com.nexora.model.User;
import com.nexora.security.CurrentUserService;
import com.nexora.service.ConversationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ConversationController {
    private final ConversationService service;
    private final CurrentUserService current;

    public ConversationController(ConversationService service, CurrentUserService current) {
        this.service = service;
        this.current = current;
    }

    @GetMapping("/conversations")
    public List<ConversationDto> list(@RequestParam(required = false) String q, Authentication a) {
        return service.list(current.require(a).id, q);
    }

    @PatchMapping("/conversations/{id}")
    public ConversationDto rename(@PathVariable Long id, @Valid @RequestBody RenameRequest r, Authentication a) {
        return service.rename(current.require(a).id, id, r.title());
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication a) {
        service.delete(current.require(a).id, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/conversations")
    public ResponseEntity<Void> clearAll(Authentication a) {
        service.clearAll(current.require(a).id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageDto> messages(@PathVariable Long id, Authentication a) {
        return service.messages(current.require(a).id, id);
    }

    @GetMapping("/messages/{id}/project.zip")
    public ResponseEntity<byte[]> projectZip(@PathVariable Long id, Authentication a) {
        User u = current.require(a);
        byte[] zip = service.projectZip(u, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("nexora-project.zip").build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }
}

package com.nexora.controller;

import com.nexora.dto.Dtos.MemoryDto;
import com.nexora.dto.Dtos.MemoryRequest;
import com.nexora.security.CurrentUserService;
import com.nexora.service.MemoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {
    private final MemoryService memory;
    private final CurrentUserService current;

    public MemoryController(MemoryService memory, CurrentUserService current) {
        this.memory = memory;
        this.current = current;
    }

    @GetMapping
    public List<MemoryDto> list(Authentication a) { return memory.list(current.require(a).id); }

    @PostMapping
    public MemoryDto add(@Valid @RequestBody MemoryRequest r, Authentication a) {
        return memory.add(current.require(a).id, r.content());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication a) {
        memory.delete(current.require(a).id, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(Authentication a) {
        memory.clear(current.require(a).id);
        return ResponseEntity.noContent().build();
    }
}

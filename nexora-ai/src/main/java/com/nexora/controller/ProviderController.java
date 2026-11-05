package com.nexora.controller;

import com.nexora.dto.Dtos.ProviderDto;
import com.nexora.dto.Dtos.ProviderRequest;
import com.nexora.dto.Dtos.TestResult;
import com.nexora.security.CurrentUserService;
import com.nexora.service.ProviderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {
    private final ProviderService providers;
    private final CurrentUserService current;

    public ProviderController(ProviderService providers, CurrentUserService current) {
        this.providers = providers;
        this.current = current;
    }

    @GetMapping
    public List<ProviderDto> list(Authentication a) { return providers.list(current.require(a).id); }

    @PostMapping
    public ProviderDto create(@Valid @RequestBody ProviderRequest r, Authentication a) {
        return providers.create(current.require(a).id, r);
    }

    @PutMapping("/{id}")
    public ProviderDto update(@PathVariable Long id, @Valid @RequestBody ProviderRequest r, Authentication a) {
        return providers.update(current.require(a).id, id, r);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication a) {
        providers.delete(current.require(a).id, id);
        return ResponseEntity.noContent().build();
    }

    /** Checks connectivity and returns the models the provider offers. */
    @PostMapping("/{id}/test")
    public TestResult test(@PathVariable Long id, Authentication a) {
        return providers.test(current.require(a).id, id);
    }
}

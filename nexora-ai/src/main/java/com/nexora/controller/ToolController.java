package com.nexora.controller;

import com.nexora.dto.Dtos.CalcRequest;
import com.nexora.exception.ApiException;
import com.nexora.service.ExpressionEvaluator;
import com.nexora.service.MathTools;
import com.nexora.service.SearchService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tools")
public class ToolController {
    private final SearchService search;

    public ToolController(SearchService search) { this.search = search; }

    @PostMapping("/calc")
    public Map<String, String> calc(@Valid @RequestBody CalcRequest r) {
        try {
            return Map.of("expression", r.expression(), "result", MathTools.format(ExpressionEvaluator.eval(r.expression())));
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot evaluate: " + e.getMessage());
        }
    }

    @GetMapping("/search-provider")
    public Map<String, String> searchProvider() {
        return Map.of("provider", search.providerName());
    }
}

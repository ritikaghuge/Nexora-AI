package com.nexora.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    public record RegisterRequest(@NotBlank @Email @Size(max = 190) String email,
                                  @NotBlank @Size(min = 8, max = 100) String password,
                                  @NotBlank @Size(max = 100) String displayName) {}
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record ProfileRequest(@NotBlank @Size(max = 100) String displayName) {}
    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 100) String newPassword) {}
    public record UserDto(Long id, String email, String displayName) {}

    public record ConversationDto(Long id, String title, String mode, Instant updatedAt) {}
    public record RenameRequest(@NotBlank @Size(max = 200) String title) {}
    public record SourceDto(String title, String url, String snippet) {}
    public record MessageDto(Long id, String role, String content, List<SourceDto> sources, String model,
                             String mode, List<Long> fileIds, Instant createdAt) {}

    public record ChatRequest(Long conversationId, @Size(max = 20000) String message, String mode,
                              List<Long> fileIds, boolean regenerate, Long providerId, String model) {}

    public record FileDto(Long id, String name, String kind, long sizeBytes, int chars, Long conversationId, String warning) {}

    public record SettingsDto(@Pattern(regexp = "light|dark|system") String theme, boolean memoryEnabled,
                              Long defaultProviderId, @Size(max = 190) String defaultModel,
                              @DecimalMin("0.0") @DecimalMax("2.0") double temperature,
                              @Size(max = 2000) String customInstructions) {}

    public record ProviderRequest(@NotBlank @Size(max = 100) String name,
                                  @NotBlank @Pattern(regexp = "OLLAMA|OPENAI_COMPATIBLE") String type,
                                  @NotBlank @Size(max = 300) String baseUrl,
                                  @Size(max = 500) String apiKey, Boolean clearApiKey,
                                  @Size(max = 190) String defaultModel, @Size(max = 190) String visionModel,
                                  @Size(max = 190) String codeModel, Boolean preferred) {}
    public record ProviderDto(Long id, String name, String type, String baseUrl, boolean hasApiKey,
                              String defaultModel, String visionModel, String codeModel, boolean preferred) {}
    public record TestResult(boolean ok, String message, List<String> models) {}

    public record MemoryRequest(@NotBlank @Size(max = 1000) String content) {}
    public record MemoryDto(Long id, String content, Instant createdAt) {}

    public record CalcRequest(@NotBlank @Size(max = 500) String expression) {}
}

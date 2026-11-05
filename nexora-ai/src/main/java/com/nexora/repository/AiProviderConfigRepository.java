package com.nexora.repository;

import com.nexora.model.AiProviderConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, Long> {
    List<AiProviderConfig> findByUserIdOrderByIdAsc(Long userId);
    Optional<AiProviderConfig> findByIdAndUserId(Long id, Long userId);
}

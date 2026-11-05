package com.nexora.repository;

import com.nexora.model.MemoryRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryRecordRepository extends JpaRepository<MemoryRecord, Long> {
    List<MemoryRecord> findByUserIdOrderByIdDesc(Long userId);
    Optional<MemoryRecord> findByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
}

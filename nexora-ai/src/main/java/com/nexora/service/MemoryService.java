package com.nexora.service;

import com.nexora.dto.Dtos.MemoryDto;
import com.nexora.exception.ApiException;
import com.nexora.model.MemoryRecord;
import com.nexora.model.UserSettings;
import com.nexora.repository.MemoryRecordRepository;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoryService {
    private static final Pattern REMEMBER = Pattern.compile("(?is)^\\s*remember(?:\\s+that|\\s*:)?\\s+(.{3,900})$");
    private final MemoryRecordRepository repo;

    public MemoryService(MemoryRecordRepository repo) { this.repo = repo; }

    public List<MemoryDto> list(Long uid) {
        return repo.findByUserIdOrderByIdDesc(uid).stream().map(m -> new MemoryDto(m.id, m.content, m.createdAt)).toList();
    }

    public MemoryDto add(Long uid, String content) {
        MemoryRecord m = new MemoryRecord();
        m.userId = uid;
        m.content = content.trim();
        repo.save(m);
        return new MemoryDto(m.id, m.content, m.createdAt);
    }

    public void delete(Long uid, Long id) {
        MemoryRecord m = repo.findByIdAndUserId(id, uid).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Memory not found"));
        repo.delete(m);
    }

    @Transactional
    public void clear(Long uid) { repo.deleteByUserId(uid); }

    /** "Remember that I prefer Java" -> stored automatically (only if memory is enabled). */
    public Optional<String> tryRemember(Long uid, String text, UserSettings st) {
        if (!st.memoryEnabled) return Optional.empty();
        Matcher m = REMEMBER.matcher(text);
        if (!m.matches()) return Optional.empty();
        String fact = m.group(1).trim();
        add(uid, fact);
        return Optional.of(fact);
    }

    public String promptText(Long uid) {
        StringBuilder sb = new StringBuilder();
        for (MemoryRecord m : repo.findByUserIdOrderByIdDesc(uid).stream().limit(30).toList()) {
            sb.append("- ").append(m.content).append('\n');
        }
        return sb.toString();
    }
}

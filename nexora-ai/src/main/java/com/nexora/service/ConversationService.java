package com.nexora.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.dto.Dtos.ConversationDto;
import com.nexora.dto.Dtos.MessageDto;
import com.nexora.dto.Dtos.SourceDto;
import com.nexora.exception.ApiException;
import com.nexora.model.Conversation;
import com.nexora.model.Message;
import com.nexora.model.User;
import com.nexora.repository.ConversationRepository;
import com.nexora.repository.FileRepository;
import com.nexora.repository.MessageRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {
    private final ConversationRepository convs;
    private final MessageRepository msgs;
    private final FileRepository files;
    private final ProjectZipService zips;
    private final ObjectMapper om;

    public ConversationService(ConversationRepository convs, MessageRepository msgs, FileRepository files,
                               ProjectZipService zips, ObjectMapper om) {
        this.convs = convs;
        this.msgs = msgs;
        this.files = files;
        this.zips = zips;
        this.om = om;
    }

    public List<ConversationDto> list(Long uid, String q) {
        List<Conversation> list = (q == null || q.isBlank()) ? convs.findByUserIdOrderByUpdatedAtDesc(uid) : convs.search(uid, q.trim());
        return list.stream().map(c -> new ConversationDto(c.id, c.title, c.mode, c.updatedAt)).toList();
    }

    public Conversation require(Long uid, Long id) {
        return convs.findByIdAndUserId(id, uid).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    public ConversationDto rename(Long uid, Long id, String title) {
        Conversation c = require(uid, id);
        c.title = title.trim();
        c.updatedAt = Instant.now();
        convs.save(c);
        return new ConversationDto(c.id, c.title, c.mode, c.updatedAt);
    }

    @Transactional
    public void delete(Long uid, Long id) {
        Conversation c = require(uid, id);
        msgs.deleteByConversation(c.id);
        files.unlinkConversation(c.id);
        convs.delete(c);
    }

    @Transactional
    public void clearAll(Long uid) {
        msgs.deleteAllForUser(uid);
        files.unlinkAllForUser(uid);
        convs.deleteAll(convs.findByUserIdOrderByUpdatedAtDesc(uid));
    }

    public List<MessageDto> messages(Long uid, Long convId) {
        Conversation c = require(uid, convId);
        List<MessageDto> out = new ArrayList<>();
        for (Message m : msgs.findByConversationIdOrderByIdAsc(c.id)) out.add(toDto(m));
        return out;
    }

    public byte[] projectZip(User u, Long messageId) {
        Message m = msgs.findById(messageId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message not found"));
        require(u.id, m.conversationId);
        return zips.build(m.content);
    }

    MessageDto toDto(Message m) {
        List<SourceDto> sources = List.of();
        if (m.sourcesJson != null && !m.sourcesJson.isBlank()) {
            try {
                sources = om.readValue(m.sourcesJson, new TypeReference<List<SourceDto>>() {});
            } catch (Exception ignored) { /* show message without sources */ }
        }
        return new MessageDto(m.id, m.role, m.content, sources, m.modelUsed, m.modeUsed, parseIds(m.fileIds), m.createdAt);
    }

    static List<Long> parseIds(String csv) {
        List<Long> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String s : csv.split(",")) {
            try { out.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) { /* skip */ }
        }
        return out;
    }
}

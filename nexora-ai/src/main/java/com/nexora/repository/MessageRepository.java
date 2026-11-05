package com.nexora.repository;

import com.nexora.model.Message;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);
    List<Message> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    @Modifying
    @Query("delete from Message m where m.conversationId = :cid")
    void deleteByConversation(@Param("cid") Long cid);

    @Modifying
    @Query("delete from Message m where m.conversationId in (select c.id from Conversation c where c.userId = :uid)")
    void deleteAllForUser(@Param("uid") Long uid);
}

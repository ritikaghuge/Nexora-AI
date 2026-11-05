package com.nexora.repository;

import com.nexora.model.Conversation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    @Query("select c from Conversation c where c.userId = :uid and (lower(c.title) like lower(concat('%', :q, '%')) "
            + "or exists (select m.id from Message m where m.conversationId = c.id and lower(m.content) like lower(concat('%', :q, '%')))) "
            + "order by c.updatedAt desc")
    List<Conversation> search(@Param("uid") Long uid, @Param("q") String q);
}

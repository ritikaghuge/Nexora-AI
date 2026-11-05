package com.nexora.repository;

import com.nexora.model.UploadedFile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileRepository extends JpaRepository<UploadedFile, Long> {
    List<UploadedFile> findByUserIdOrderByIdDesc(Long userId);
    Optional<UploadedFile> findByIdAndUserId(Long id, Long userId);
    List<UploadedFile> findAllByIdInAndUserId(List<Long> ids, Long userId);
    List<UploadedFile> findByConversationIdAndKind(Long conversationId, String kind);

    @Modifying
    @Query("update UploadedFile f set f.conversationId = null where f.conversationId = :cid")
    void unlinkConversation(@Param("cid") Long cid);

    @Modifying
    @Query("update UploadedFile f set f.conversationId = null where f.userId = :uid")
    void unlinkAllForUser(@Param("uid") Long uid);
}

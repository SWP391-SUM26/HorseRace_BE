package com.SWP391.horserace.attachments.repository;

import com.SWP391.horserace.attachments.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /** Attachments for a given polymorphic owner, newest first (FE-v2 §6). */
    @Query("""
        SELECT a FROM Attachment a
         WHERE a.ownerEntityType = :ownerEntityType
           AND a.ownerEntityId = :ownerEntityId
         ORDER BY a.uploadedAt DESC
        """)
    List<Attachment> findByOwner(@Param("ownerEntityType") String ownerEntityType,
                                 @Param("ownerEntityId") UUID ownerEntityId);
}

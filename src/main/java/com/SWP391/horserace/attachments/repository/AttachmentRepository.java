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

    /** True if any attachment exists for the given polymorphic owner (#7 registration doc gate). */
    boolean existsByOwnerEntityTypeAndOwnerEntityId(String ownerEntityType, UUID ownerEntityId);

    /**
     * True if the given owner-entity has an attachment uploaded BY {@code uploadedByUserId} (#7):
     * the registration's document must come from its own owner, not any authenticated user who tagged
     * a file with the registration id.
     */
    boolean existsByOwnerEntityTypeAndOwnerEntityIdAndUploadedBy_UserId(
            String ownerEntityType, UUID ownerEntityId, UUID uploadedByUserId);
}

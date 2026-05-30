package com.SWP391.horserace.attachments.entity;

import com.SWP391.horserace.users.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Maps the {@code attachment} table (files attached to any entity — polymorphic owner). */
@Entity
@Table(name = "attachment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attachment_id", updatable = false, nullable = false)
    private UUID attachmentId;

    // Polymorphic reference (no FK by design).
    @Column(name = "owner_entity_type", length = 50)
    private String ownerEntityType;

    @Column(name = "owner_entity_id")
    private UUID ownerEntityId;

    @Column(name = "object_key", columnDefinition = "text")
    private String objectKey;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    /** PUBLIC | INTERNAL | CONFIDENTIAL | RESTRICTED */
    @Column(name = "sensitivity_level", length = 50)
    private String sensitivityLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false, nullable = false)
    private OffsetDateTime uploadedAt;
}

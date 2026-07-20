package com.SWP391.horserace.inspections.entity;

import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.users.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps the {@code entry_document_review} table — a referee's per-race review of an entry's owner +
 * horse papers (CN2). Separate entity from {@link RaceEntryInspection} (the steward vet-check) so the
 * two never collide on the same table.
 */
@Entity
@Table(name = "entry_document_review")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntryDocumentReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "review_id", updatable = false, nullable = false)
    private UUID reviewId;

    /** One review per entry (UNIQUE entry_id). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false, unique = true)
    private RaceEntry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    /** PENDING | ACCEPTED | REJECTED */
    @Enumerated(EnumType.STRING)
    @Column(name = "document_status", nullable = false, length = 20)
    @Builder.Default
    private DocumentReviewStatus documentStatus = DocumentReviewStatus.PENDING;

    @Column(name = "review_reason", columnDefinition = "text")
    private String reviewReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

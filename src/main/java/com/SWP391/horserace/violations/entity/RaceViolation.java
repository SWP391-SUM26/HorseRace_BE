package com.SWP391.horserace.violations.entity;

import com.SWP391.horserace.penalties.entity.Penalty;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.reports.entity.SeverityLevel;
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

/** Maps the {@code race_violation} table (structured violations / inquiries — FE-v2 §3). */
@Entity
@Table(name = "race_violation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "violation_id", updatable = false, nullable = false)
    private UUID violationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    /** The offending entry (nullable — a violation may not be tied to a specific horse). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id")
    private RaceEntry entry;

    /** The offending jockey (nullable). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jockey_user_id")
    private User jockey;

    /** BUMPING | INTERFERENCE | WHIP_USAGE | CROWDING | OTHER */
    @Enumerated(EnumType.STRING)
    @Column(name = "infraction_type", nullable = false, length = 50)
    private InfractionType infractionType;

    /** LOW | MEDIUM | HIGH | CRITICAL (nullable). Reuses referee_report's SeverityLevel. */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 50)
    private SeverityLevel severity;

    @Column(name = "turn_no")
    private Integer turnNo;

    @Column(name = "race_time_offset_ms")
    private Long raceTimeOffsetMs;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    @Column(name = "regulatory_ref", length = 255)
    private String regulatoryRef;

    @Column(name = "footage_attachment_id")
    private UUID footageAttachmentId;

    /** PENDING | UNDER_REVIEW | RESOLVED | DISMISSED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private ViolationStatus status = ViolationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by_user_id")
    private User reportedBy;

    // ── official ruling (filled on PATCH /ruling) ──

    /** The penalty created when a ruling applies one (nullable). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "penalty_id")
    private Penalty penalty;

    /** Free-form decision (e.g. PENALTY_APPLIED, DISMISSED, NO_ACTION). */
    @Column(name = "decision_type", length = 50)
    private String decisionType;

    @Column(name = "ruling_notes", columnDefinition = "text")
    private String rulingNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruled_by_user_id")
    private User ruledBy;

    @Column(name = "ruled_at")
    private OffsetDateTime ruledAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

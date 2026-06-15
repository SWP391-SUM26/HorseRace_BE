package com.SWP391.horserace.reports.entity;

import com.SWP391.horserace.races.entity.Race;
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

/** Maps the {@code referee_report} table (incident / violation reports filed by referees). */
@Entity
@Table(name = "referee_report")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefereeReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "report_id", updatable = false, nullable = false)
    private UUID reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id")
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private User author;

    /** INCIDENT | VIOLATION | OBJECTION | GENERAL */
    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", length = 50)
    private ReportType reportType;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "decision", columnDefinition = "text")
    private String decision;

    /** LOW | MEDIUM | HIGH | CRITICAL */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity_level", length = 50)
    private SeverityLevel severityLevel;

    /** DRAFT | SUBMITTED | REVIEWED | CLOSED */
    @Enumerated(EnumType.STRING)
    @Column(name = "report_status", nullable = false, length = 50)
    @Builder.Default
    private ReportStatus reportStatus = ReportStatus.DRAFT;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

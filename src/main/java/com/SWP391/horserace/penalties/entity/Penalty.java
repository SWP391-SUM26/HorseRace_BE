package com.SWP391.horserace.penalties.entity;

import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.reports.entity.RefereeReport;
import com.SWP391.horserace.users.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Maps the {@code penalty} table (a structured sanction tied to a race, optionally an entry/report). */
@Entity
@Table(name = "penalty")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Penalty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "penalty_id", updatable = false, nullable = false)
    private UUID penaltyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id")
    private Race race;

    /** The penalized entry (nullable). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id")
    private RaceEntry entry;

    /** Related referee report (nullable). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private RefereeReport report;

    /** WARNING | TIME_PENALTY | FINE | DISQUALIFICATION | SUSPENSION */
    @Column(name = "penalty_type", nullable = false, length = 50)
    private String penaltyType;

    @Column(name = "time_penalty_ms")
    private Long timePenaltyMs;

    @Column(name = "fine_amount", precision = 18, scale = 2)
    private BigDecimal fineAmount;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by_user_id")
    private User issuedBy;

    /** ISSUED | UPHELD | OVERTURNED | CANCELLED */
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "ISSUED";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

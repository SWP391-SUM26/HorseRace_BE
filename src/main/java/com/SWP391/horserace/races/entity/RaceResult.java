package com.SWP391.horserace.races.entity;

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

/** Maps the {@code race_result} table (current official standing of one entry). */
@Entity
@Table(name = "race_result")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "result_id", updatable = false, nullable = false)
    private UUID resultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id")
    private Race race;

    /** UNIQUE: one result row per entry. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", unique = true)
    private RaceEntry entry;

    @Column(name = "finish_position")
    private Integer finishPosition;

    @Column(name = "finish_time_ms")
    private Long finishTimeMs;

    @Column(name = "score", precision = 10, scale = 2)
    private BigDecimal score;

    @Column(name = "current_version_no", nullable = false)
    @Builder.Default
    private Integer currentVersionNo = 1;

    /** PROVISIONAL | UNDER_REVIEW | OFFICIAL | AMENDED */
    @Enumerated(EnumType.STRING)
    @Column(name = "officiality_status", nullable = false, length = 50)
    @Builder.Default
    private OfficialityStatus officialityStatus = OfficialityStatus.PROVISIONAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

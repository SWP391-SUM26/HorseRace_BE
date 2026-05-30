package com.SWP391.horserace.races.entity;

import com.SWP391.horserace.users.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Maps the {@code race_result_version} table (immutable audit trail of result amendments). */
@Entity
@Table(name = "race_result_version")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceResultVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "result_version_id", updatable = false, nullable = false)
    private UUID resultVersionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id")
    private RaceResult result;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "finish_position")
    private Integer finishPosition;

    @Column(name = "finish_time_ms")
    private Long finishTimeMs;

    @Column(name = "score", precision = 10, scale = 2)
    private BigDecimal score;

    @Column(name = "officiality_status", length = 50)
    private String officialityStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    private User changedBy;

    @Column(name = "change_reason", columnDefinition = "text")
    private String changeReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;
}

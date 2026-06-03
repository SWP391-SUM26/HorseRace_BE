package com.SWP391.horserace.standings.entity;

import com.SWP391.horserace.tournaments.entity.Tournament;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps the {@code standing} table (point standings per tournament).
 * The ranked subject is polymorphic (a horse or a jockey): {@code subjectId} holds the
 * {@code horse_id} or {@code app_user(user_id)} per {@code subjectType}, with no FK by design.
 */
@Entity
@Table(name = "standing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Standing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "standing_id", updatable = false, nullable = false)
    private UUID standingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    /** HORSE | JOCKEY */
    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    /** Polymorphic: horse_id or app_user(user_id) depending on subjectType (no FK by design). */
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "total_points", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalPoints = BigDecimal.ZERO;

    @Column(name = "races_count", nullable = false)
    @Builder.Default
    private Integer racesCount = 0;

    @Column(name = "wins_count", nullable = false)
    @Builder.Default
    private Integer winsCount = 0;

    @Column(name = "rank_position")
    private Integer rankPosition;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

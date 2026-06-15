package com.SWP391.horserace.tournaments.entity;

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

/** Maps the {@code tournament_round} table (a "round" of a tournament: qualifier/heat/semi/final). */
@Entity
@Table(name = "tournament_round")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentRound {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "round_id", updatable = false, nullable = false)
    private UUID roundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    @Column(name = "name", length = 100)
    private String name;

    /** QUALIFIER | HEAT | SEMI | FINAL */
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 30)
    private RoundStage stage;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    /** PLANNED | ONGOING | COMPLETED | CANCELLED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private RoundStatus status = RoundStatus.PLANNED;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

package com.SWP391.horserace.races.entity;

import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.venues.entity.Venue;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Maps the {@code race} table. */
@Entity
@Table(name = "race")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Race {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "race_id", updatable = false, nullable = false)
    private UUID raceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @Column(name = "race_code", nullable = false, unique = true, length = 50)
    private String raceCode;

    @Column(name = "name")
    private String name;

    @Column(name = "race_type", length = 50)
    private String raceType;

    @Column(name = "distance_meter")
    private Integer distanceMeter;

    @Column(name = "track_condition", length = 50)
    private String trackCondition;

    @Column(name = "weather_condition", length = 50)
    private String weatherCondition;

    @Column(name = "scheduled_start_at")
    private OffsetDateTime scheduledStartAt;

    @Column(name = "actual_start_at")
    private OffsetDateTime actualStartAt;

    @Column(name = "actual_end_at")
    private OffsetDateTime actualEndAt;

    @Column(name = "prediction_cutoff_at")
    private OffsetDateTime predictionCutoffAt;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Column(name = "venue")
    private String venue;

    /** §D1 — optional FK to a structured venue (track). The free-text {@code venue} column is kept too. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venue venueRef;

    @Column(name = "going_moisture_pct")
    private Integer goingMoisturePct;

    @Column(name = "total_purse")
    private BigDecimal totalPurse;

    @ElementCollection
    @CollectionTable(name = "race_prize_distribution", joinColumns = @JoinColumn(name = "race_id"))
    @Builder.Default
    private List<PrizeDistributionItem> prizeDistribution = new ArrayList<>();

    /** SCHEDULED | OPEN | CLOSED | RUNNING | FINISHED | OFFICIAL | CANCELLED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private RaceStatus status = RaceStatus.SCHEDULED;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}

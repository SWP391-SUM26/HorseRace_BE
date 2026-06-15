package com.SWP391.horserace.predictions.entity;

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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Maps the {@code prediction} table (a spectator's bet on a race). */
@Entity
@Table(name = "prediction")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "prediction_id", updatable = false, nullable = false)
    private UUID predictionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id")
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spectator_user_id")
    private User spectator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "predicted_entry_id")
    private RaceEntry predictedEntry;

    /** WIN | PLACE | SHOW | EXACTA | QUINELLA */
    @Enumerated(EnumType.STRING)
    @Column(name = "prediction_type", length = 50)
    private PredictionType predictionType;

    @Column(name = "locked_odds", precision = 10, scale = 2)
    private BigDecimal lockedOdds;

    @Column(name = "stake_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal stakeAmount;

    @Column(name = "potential_payout", precision = 18, scale = 2)
    private BigDecimal potentialPayout;

    /** PENDING | CONFIRMED | WON | LOST | VOID | REFUNDED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private PredictionStatus status = PredictionStatus.PENDING;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    /** Client-supplied key to make bet submission idempotent (avoid double-submit). */
    @Column(name = "idempotency_key", length = 255, unique = true)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

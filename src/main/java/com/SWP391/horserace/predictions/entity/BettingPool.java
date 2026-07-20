package com.SWP391.horserace.predictions.entity;

import com.SWP391.horserace.races.entity.Race;
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

/**
 * Maps the {@code betting_pool} table — PARI-MUTUEL model: stakes are pooled per
 * (race, prediction_type) and split among winners at settlement.
 *
 * <p><b>Important:</b> {@link #totalStake} is a DISPLAY-ONLY running counter — it is maintained by
 * unlocked read-modify-write on bet/cancel and can drift/lose updates under concurrent betting.
 * Live odds ({@code getLivePoolOdds}) and settlement MUST recompute the authoritative total by
 * summing live {@code Prediction} rows, NOT by reading {@code totalStake}. The payout formula is
 * {@code payout_i = Σ(live stake) * (1 - rakePercent) * stake_i / Σ(winning stakes)}.
 */
@Entity
@Table(name = "betting_pool")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BettingPool {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pool_id", updatable = false, nullable = false)
    private UUID poolId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id")
    private Race race;

    /** WIN | PLACE | SHOW | EXACTA | QUINELLA */
    @Enumerated(EnumType.STRING)
    @Column(name = "prediction_type", nullable = false, length = 50)
    private PredictionType predictionType;

    @Column(name = "total_stake", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalStake = BigDecimal.ZERO;

    /** House take-out as a FRACTION in [0,1] (e.g. 0.15 = 15%), NOT a 0–100 percentage. */
    @Column(name = "rake_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal rakePercent = BigDecimal.ZERO;

    /** OPEN | CLOSED | SETTLED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private BettingPoolStatus status = BettingPoolStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

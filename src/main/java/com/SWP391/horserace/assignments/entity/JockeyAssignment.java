package com.SWP391.horserace.assignments.entity;

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

/** Maps the {@code jockey_assignment} table (owner invites a jockey to ride an entry). */
@Entity
@Table(name = "jockey_assignment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JockeyAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assignment_id", updatable = false, nullable = false)
    private UUID assignmentId;

    /** UNIQUE: one jockey assignment per entry. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", unique = true)
    private RaceEntry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jockey_user_id")
    private User jockey;

    /** INVITED | ACCEPTED | DECLINED | CANCELLED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private JockeyAssignmentStatus status = JockeyAssignmentStatus.INVITED;

    @Column(name = "invited_at")
    private OffsetDateTime invitedAt;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    /**
     * Share of this entry's prize the jockey takes, agreed when the invitation was sent and frozen
     * once accepted. Deliberately NOT read from {@code jockey_profile.prize_percent} at payout
     * time — that is the jockey's asking rate and they can change it at any moment, including after
     * the race, which would pay out something neither party agreed to.
     */
    @Column(name = "agreed_prize_percent", precision = 5, scale = 2)
    private BigDecimal agreedPrizePercent;

    /** Flat riding fee agreed alongside the share — the amount escrowed from the owner on invite. */
    @Column(name = "agreed_base_fee", precision = 18, scale = 2)
    private BigDecimal agreedBaseFee;

    /**
     * Escrow of the hire fee. The owner must hold the money to invite at all; it is locked out of
     * their spendable balance immediately and only reaches the jockey once the race is certified.
     *
     * {@code feeHeldAmount} is the amount actually locked, never re-read from {@link #agreedBaseFee}
     * at settlement — the same reason the entry fee refunded what it charged: if the agreed figure
     * were ever edited between hold and payout, the lock and the ledger would drift apart and the
     * owner's locked_balance could never be brought back to zero.
     *
     * The three timestamps are atomic claims (UPDATE ... WHERE ... IS NULL), so a retry or a
     * concurrent request cannot pay twice, release twice, or both pay and release.
     */
    @Column(name = "fee_held_amount", precision = 18, scale = 2)
    private BigDecimal feeHeldAmount;

    @Column(name = "fee_held_at")
    private OffsetDateTime feeHeldAt;

    @Column(name = "fee_released_at")
    private OffsetDateTime feeReleasedAt;

    @Column(name = "fee_paid_at")
    private OffsetDateTime feePaidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private User assignedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

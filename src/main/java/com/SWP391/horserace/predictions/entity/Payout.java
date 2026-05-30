package com.SWP391.horserace.predictions.entity;

import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.wallets.entity.WalletTransaction;
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

/** Maps the {@code payout} table (settlement of a winning prediction). */
@Entity
@Table(name = "payout")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payout_id", updatable = false, nullable = false)
    private UUID payoutId;

    /** UNIQUE: one payout per prediction. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prediction_id", unique = true)
    private Prediction prediction;

    @Column(name = "payout_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal payoutAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_txn_id")
    private WalletTransaction walletTransaction;

    /** PENDING | PAID | FAILED | CANCELLED */
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settled_by_user_id")
    private User settledBy;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;
}

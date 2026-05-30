package com.SWP391.horserace.wallets.entity;

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

/** Maps the {@code payment_transaction} table (external / gateway movements). */
@Entity
@Table(name = "payment_transaction")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_txn_id", updatable = false, nullable = false)
    private UUID paymentTxnId;

    // Polymorphic reference (no FK by design) — stored as plain type + id.
    @Column(name = "business_entity_type", length = 50)
    private String businessEntityType;

    @Column(name = "business_entity_id")
    private UUID businessEntityId;

    /** DEPOSIT | WITHDRAWAL | PAYOUT | REFUND */
    @Column(name = "transaction_type", length = 50)
    private String transactionType;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 10)
    @Builder.Default
    private String currencyCode = "VND";

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    /** PENDING | SUCCESS | FAILED | CANCELLED | REFUNDED */
    @Column(name = "payment_status", nullable = false, length = 50)
    @Builder.Default
    private String paymentStatus = "PENDING";

    @Column(name = "external_txn_ref")
    private String externalTxnRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

package com.SWP391.horserace.wallets.repository;

import com.SWP391.horserace.wallets.entity.PaymentTransaction;
import com.SWP391.horserace.wallets.entity.PaymentTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    /** Paged view of withdrawal requests, newest first (admin queue in Phase 04). */
    Page<PaymentTransaction> findByTransactionTypeOrderByCreatedAtDesc(
            PaymentTransactionType transactionType, Pageable pageable);

    /** Admin review queue: withdrawals in a given status (PENDING), newest first. */
    Page<PaymentTransaction> findByTransactionTypeAndPaymentStatusOrderByCreatedAtDesc(
            PaymentTransactionType transactionType,
            com.SWP391.horserace.wallets.entity.PaymentStatus paymentStatus,
            Pageable pageable);

    /**
     * Atomic exactly-once claim: flips PENDING -> SUCCESS only if the txn is still PENDING.
     * Returns the number of rows updated (1 = this caller won the claim and must credit;
     * 0 = another delivery already processed it — credit nothing). This conditional UPDATE
     * (not a read-then-check) is what makes duplicate/concurrent VNPay IPN deliveries credit once.
     */
    @Modifying
    @Query("update PaymentTransaction p set p.paymentStatus = com.SWP391.horserace.wallets.entity.PaymentStatus.SUCCESS "
            + "where p.paymentTxnId = :id and p.paymentStatus = com.SWP391.horserace.wallets.entity.PaymentStatus.PENDING")
    int markSuccessIfPending(@Param("id") UUID id);

    /**
     * Atomic exactly-once claim for a REJECTED withdrawal: flips PENDING -> CANCELLED only while
     * still PENDING. Returns rows updated (1 = this caller won the claim and must release the hold;
     * 0 = already approved/rejected — do nothing). Same conditional-UPDATE guard as
     * {@link #markSuccessIfPending} — blocks a double-reject / approve-then-reject race.
     */
    @Modifying
    @Query("update PaymentTransaction p set p.paymentStatus = com.SWP391.horserace.wallets.entity.PaymentStatus.CANCELLED "
            + "where p.paymentTxnId = :id and p.paymentStatus = com.SWP391.horserace.wallets.entity.PaymentStatus.PENDING")
    int markCancelledIfPending(@Param("id") UUID id);
}

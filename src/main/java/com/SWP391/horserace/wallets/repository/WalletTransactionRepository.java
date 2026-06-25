package com.SWP391.horserace.wallets.repository;

import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    @Query("SELECT COALESCE(SUM(wt.amount), 0) FROM WalletTransaction wt WHERE wt.txnCategory = :category")
    BigDecimal getTotalAmountByCategory(@Param("category") TxnCategory category);

    @Query(value = "SELECT DATE(created_at) as txnDate, txn_category as txnCategory, COALESCE(SUM(amount), 0) as totalAmount "
            +
            "FROM wallet_transaction " +
            "WHERE created_at >= :startDate AND created_at < :endDate AND txn_category IN ('BET_STAKE', 'BET_PAYOUT', 'REFUND') "
            +
            "GROUP BY DATE(created_at), txn_category", nativeQuery = true)
    java.util.List<com.SWP391.horserace.financials.dto.DailyTransactionSumProjection> getDailyTransactionSums(
            @Param("startDate") java.time.OffsetDateTime startDate,
            @Param("endDate") java.time.OffsetDateTime endDate);
}

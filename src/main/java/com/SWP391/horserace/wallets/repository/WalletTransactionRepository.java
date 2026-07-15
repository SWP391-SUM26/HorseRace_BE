package com.SWP391.horserace.wallets.repository;

import com.SWP391.horserace.wallets.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    /** Paged ledger history for a user, newest first. */
    Page<WalletTransaction> findByWallet_User_UserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}

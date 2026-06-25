package com.SWP391.horserace.wallets.repository;

import com.SWP391.horserace.wallets.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByUserUserId(UUID userId);

    @Query("SELECT COALESCE(SUM(w.balance), 0) FROM Wallet w")
    BigDecimal getTotalBalance();

    @Query("SELECT COALESCE(SUM(w.lockedBalance), 0) FROM Wallet w")
    BigDecimal getTotalLockedBalance();
}

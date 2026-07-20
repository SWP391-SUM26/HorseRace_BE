package com.SWP391.horserace.wallets.repository;

import com.SWP391.horserace.wallets.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByUserUserId(UUID userId);

    /**
     * Row-locked wallet lookup (SELECT ... FOR UPDATE). Every money mutation loads the wallet
     * through this so concurrent debits/credits serialize on the wallet row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.user.userId = :userId")
    Optional<Wallet> findByUserUserIdForUpdate(@Param("userId") UUID userId);
}

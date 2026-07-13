package com.SWP391.horserace.wallets.service.impl;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.wallets.service.HouseWalletService;
import com.SWP391.horserace.wallets.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves the escrow/house wallet from the configured owner email (default the seeded admin
 * {@code admin@horserace.local}). Called inside each money {@code @Transactional} so the wallet-row
 * ensure ({@link WalletService#getOrCreateWallet}) joins that same transaction.
 */
@Service
@RequiredArgsConstructor
public class HouseWalletServiceImpl implements HouseWalletService {

    /** House / escrow wallet owner email (config-overridable, defaults to the seeded admin). */
    @Value("${app.house.user-email:admin@horserace.local}")
    private String houseEmail;

    private final UserRepository userRepository;
    private final WalletService walletService;

    @Override
    public UUID houseUserId() {
        // A missing house user is a server misconfiguration (seed/env), not a client error — surface it
        // as SERVICE_UNAVAILABLE so no money moves rather than silently failing a bet/payout.
        User house = userRepository.findByEmail(houseEmail)
                .orElseThrow(() -> new AppException(ErrorCode.HOUSE_WALLET_UNAVAILABLE));
        // Ensure the house wallet row exists so the credit/debit has a target. Pre-seeded in seed.sql
        // to avoid a first-bet bootstrap race; this is the belt for a non-seeded/live DB.
        walletService.getOrCreateWallet(house.getUserId());
        return house.getUserId();
    }
}

package com.SWP391.horserace.wallets.service;

import java.util.UUID;

/**
 * Resolves the house / escrow wallet owner. Every two-sided money move (bet, payout, refund) locks
 * the house wallet row FIRST, so the house is the common counterparty and the total lock order it
 * imposes is what makes concurrent moves deadlock-safe. The house holds every stake on the way in and
 * pays every winner/refund out, leaving exactly the pari-mutuel rake for a fully-settled race.
 */
public interface HouseWalletService {

    /**
     * Resolve the house wallet owner's user id, ensuring the house wallet row exists.
     *
     * @return the house user id
     * @throws com.SWP391.horserace.shared.exception.AppException
     *         {@code HOUSE_WALLET_UNAVAILABLE} if no user matches the configured house email
     */
    UUID houseUserId();
}

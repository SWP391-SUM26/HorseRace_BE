package com.SWP391.horserace.financials.service.impl;

import com.SWP391.horserace.financials.dto.FinancialDashboardResponse;
import com.SWP391.horserace.financials.service.FinancialDashboardService;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.repository.WalletRepository;
import com.SWP391.horserace.wallets.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinancialDashboardServiceImpl implements FinancialDashboardService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    @Transactional(readOnly = true)
    public FinancialDashboardResponse getDashboard() {
        return FinancialDashboardResponse.builder()
                .totalSystemBalance(walletRepository.getTotalBalance())
                .totalLockedBalance(walletRepository.getTotalLockedBalance())
                .totalDeposits(walletTransactionRepository.getTotalAmountByCategory(TxnCategory.DEPOSIT))
                .totalWithdrawals(walletTransactionRepository.getTotalAmountByCategory(TxnCategory.WITHDRAWAL))
                .totalBetStakes(walletTransactionRepository.getTotalAmountByCategory(TxnCategory.BET_STAKE))
                .totalBetPayouts(walletTransactionRepository.getTotalAmountByCategory(TxnCategory.BET_PAYOUT))
                .totalPrizes(walletTransactionRepository.getTotalAmountByCategory(TxnCategory.PRIZE))
                .totalRefunds(walletTransactionRepository.getTotalAmountByCategory(TxnCategory.REFUND))
                .build();
    }
}

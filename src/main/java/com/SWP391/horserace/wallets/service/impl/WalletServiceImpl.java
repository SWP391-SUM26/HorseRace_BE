package com.SWP391.horserace.wallets.service.impl;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.wallets.dto.TopupRequest;
import com.SWP391.horserace.wallets.dto.TopupResponse;
import com.SWP391.horserace.wallets.dto.WalletResponse;
import com.SWP391.horserace.wallets.dto.WalletTransactionResponse;
import com.SWP391.horserace.wallets.dto.WithdrawalRequest;
import com.SWP391.horserace.wallets.dto.WithdrawalResponse;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.PaymentStatus;
import com.SWP391.horserace.wallets.entity.PaymentTransaction;
import com.SWP391.horserace.wallets.entity.PaymentTransactionType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.entity.Wallet;
import com.SWP391.horserace.wallets.entity.WalletStatus;
import com.SWP391.horserace.wallets.entity.WalletTransaction;
import com.SWP391.horserace.wallets.repository.PaymentTransactionRepository;
import com.SWP391.horserace.wallets.repository.WalletRepository;
import com.SWP391.horserace.wallets.repository.WalletTransactionRepository;
import com.SWP391.horserace.wallets.service.VnPayService;
import com.SWP391.horserace.wallets.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    /** Minimum top-up (also bet/withdrawal floor) — 10,000 VND. */
    private static final BigDecimal MIN_TOPUP = new BigDecimal("10000");
    /** Upper bound per top-up — VNPay sandbox caps a single transaction; also a sanity ceiling. */
    private static final BigDecimal MAX_TOPUP = new BigDecimal("500000000"); // 500,000,000 VND

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;
    private final VnPayService vnPayService;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Override
    @Transactional
    public Wallet getOrCreateWallet(UUID userId) {
        return walletRepository.findByUserUserId(userId)
                .orElseGet(() -> walletRepository.save(Wallet.builder()
                        .user(userRepository.getReferenceById(userId))
                        .balance(BigDecimal.ZERO)
                        .lockedBalance(BigDecimal.ZERO)
                        .currencyCode("VND")
                        .status(WalletStatus.ACTIVE)
                        .build()));
    }

    @Override
    @Transactional
    public WalletResponse getBalance(UUID userId) {
        Wallet wallet = getOrCreateWallet(userId);
        return WalletResponse.builder()
                .walletId(wallet.getWalletId())
                .balance(wallet.getBalance())
                .lockedBalance(wallet.getLockedBalance())
                .currencyCode(wallet.getCurrencyCode())
                .status(wallet.getStatus())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getTransactions(UUID userId, Pageable pageable) {
        return walletTransactionRepository
                .findByWallet_User_UserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public TopupResponse createTopup(UUID userId, TopupRequest request) {
        BigDecimal amount = request.getAmount();
        // VND has no sub-unit: reject any fractional amount so `amount*100` sent to VNPay can never
        // truncate below the persisted txn.amount (which would false-mismatch on the IPN amount check).
        if (amount == null
                || amount.compareTo(MIN_TOPUP) < 0
                || amount.compareTo(MAX_TOPUP) > 0
                || amount.stripTrailingZeros().scale() > 0) {
            throw new AppException(ErrorCode.TOPUP_AMOUNT_INVALID);
        }
        // Ensure the wallet exists so the IPN credit has a target.
        getOrCreateWallet(userId);
        String payUrl = vnPayService.buildPaymentUrl(userId, amount);
        return TopupResponse.builder()
                .payUrl(payUrl)
                .amount(amount)
                .build();
    }

    @Override
    @Transactional
    public WithdrawalResponse requestWithdrawal(UUID userId, WithdrawalRequest request) {
        BigDecimal amount = request.getAmount();
        // Amount guard FIRST — must run BEFORE the balance check. A negative amount trivially passes
        // `balance < amount` and the hold mutation `balance -= amount` would INCREASE the balance
        // (free-money exploit). `amount >= MIN_TOPUP` also rejects zero/negative; reject fractional VND.
        if (amount == null
                || amount.compareTo(MIN_TOPUP) < 0
                || amount.stripTrailingZeros().scale() > 0) {
            throw new AppException(ErrorCode.WITHDRAWAL_AMOUNT_INVALID);
        }
        Wallet wallet = walletRepository.findByUserUserIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
        // A frozen/closed wallet cannot move money out.
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new AppException(ErrorCode.WALLET_INACTIVE);
        }
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        // Hold: move from spendable to locked. No wallet_transaction ledger row yet — the ledger DEBIT
        // is written only when the withdrawal settles (approve).
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setLockedBalance(wallet.getLockedBalance().add(amount));
        walletRepository.save(wallet);

        PaymentTransaction txn = PaymentTransaction.builder()
                .transactionType(PaymentTransactionType.WITHDRAWAL)
                .paymentStatus(PaymentStatus.PENDING)
                .amount(amount)
                .currencyCode(wallet.getCurrencyCode())
                .wallet(wallet)
                .createdBy(wallet.getUser())
                .build();
        return toWithdrawalResponse(paymentTransactionRepository.save(txn));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WithdrawalResponse> listWithdrawals(Pageable pageable) {
        // The admin review queue is PENDING-only — settled (SUCCESS/CANCELLED) withdrawals must not
        // linger in the actionable queue.
        return paymentTransactionRepository
                .findByTransactionTypeAndPaymentStatusOrderByCreatedAtDesc(
                        PaymentTransactionType.WITHDRAWAL, PaymentStatus.PENDING, pageable)
                .map(this::toWithdrawalResponse);
    }

    @Override
    @Transactional
    public WithdrawalResponse approveWithdrawal(UUID adminId, UUID withdrawalId) {
        PaymentTransaction txn = paymentTransactionRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_FOUND));
        // Atomic exactly-once claim: PENDING -> SUCCESS. 0 rows => already settled (double-approve /
        // approve-after-reject) — block it so the lock is released and the ledger row written once.
        if (paymentTransactionRepository.markSuccessIfPending(withdrawalId) == 0) {
            throw new AppException(ErrorCode.WITHDRAWAL_INVALID_STATE);
        }
        BigDecimal amount = txn.getAmount();
        UUID ownerId = txn.getWallet().getUser().getUserId();
        Wallet wallet = walletRepository.findByUserUserIdForUpdate(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
        // Balance was already reduced at hold time; here we only release the lock and record the DEBIT.
        wallet.setLockedBalance(wallet.getLockedBalance().subtract(amount));
        walletRepository.save(wallet);

        WalletTransaction ledger = WalletTransaction.builder()
                .wallet(wallet)
                .entryType(EntryType.DEBIT)
                .txnCategory(TxnCategory.WITHDRAWAL)
                .amount(amount)
                .balanceAfter(wallet.getBalance())
                .relatedEntityType("PAYMENT_TRANSACTION")
                .relatedEntityId(withdrawalId)
                .paymentTransaction(txn)
                .build();
        walletTransactionRepository.save(ledger);

        txn.setPaymentStatus(PaymentStatus.SUCCESS); // reflect the claim in the returned view
        log.info("Admin {} APPROVED withdrawal {} ({} VND) for user {}", adminId, withdrawalId, amount, ownerId);
        return toWithdrawalResponse(txn);
    }

    @Override
    @Transactional
    public WithdrawalResponse rejectWithdrawal(UUID adminId, UUID withdrawalId) {
        PaymentTransaction txn = paymentTransactionRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_FOUND));
        // Atomic exactly-once claim: PENDING -> CANCELLED. 0 rows => already settled — block it.
        if (paymentTransactionRepository.markCancelledIfPending(withdrawalId) == 0) {
            throw new AppException(ErrorCode.WITHDRAWAL_INVALID_STATE);
        }
        BigDecimal amount = txn.getAmount();
        UUID ownerId = txn.getWallet().getUser().getUserId();
        Wallet wallet = walletRepository.findByUserUserIdForUpdate(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
        // Release the hold back to spendable — no money left the wallet, so no ledger row.
        wallet.setLockedBalance(wallet.getLockedBalance().subtract(amount));
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        txn.setPaymentStatus(PaymentStatus.CANCELLED);
        log.info("Admin {} REJECTED withdrawal {} ({} VND); hold released to user {}", adminId, withdrawalId, amount, ownerId);
        return toWithdrawalResponse(txn);
    }

    private WithdrawalResponse toWithdrawalResponse(PaymentTransaction txn) {
        User requester = txn.getCreatedBy();
        return WithdrawalResponse.builder()
                .id(txn.getPaymentTxnId())
                .amount(txn.getAmount())
                .status(txn.getPaymentStatus())
                .createdAt(txn.getCreatedAt())
                .requesterId(requester != null ? requester.getUserId() : null)
                .requesterName(requester != null ? requester.getFullName() : null)
                .build();
    }

    private WalletTransactionResponse toResponse(WalletTransaction txn) {
        return WalletTransactionResponse.builder()
                .walletTxnId(txn.getWalletTxnId())
                .entryType(txn.getEntryType())
                .txnCategory(txn.getTxnCategory())
                .amount(txn.getAmount())
                .balanceAfter(txn.getBalanceAfter())
                .relatedEntityType(txn.getRelatedEntityType())
                .relatedEntityId(txn.getRelatedEntityId())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}

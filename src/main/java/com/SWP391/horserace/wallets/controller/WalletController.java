package com.SWP391.horserace.wallets.controller;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.wallets.dto.TopupRequest;
import com.SWP391.horserace.wallets.dto.TopupResponse;
import com.SWP391.horserace.wallets.dto.VnPayIpnResult;
import com.SWP391.horserace.wallets.dto.WalletResponse;
import com.SWP391.horserace.wallets.dto.WalletTransactionResponse;
import com.SWP391.horserace.wallets.dto.WithdrawalRequest;
import com.SWP391.horserace.wallets.dto.WithdrawalResponse;
import com.SWP391.horserace.wallets.service.VnPayService;
import com.SWP391.horserace.wallets.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final VnPayService vnPayService;

    /** Balance/locked/currency/status (auto-creates the wallet on first read). */
    @GetMapping
    public ApiResponse<WalletResponse> getWallet(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.<WalletResponse>builder()
                .success(true)
                .message("Fetched wallet")
                .data(walletService.getBalance(userId))
                .build();
    }

    /** Paged ledger history, newest first. */
    @GetMapping("/transactions")
    public ApiResponse<Page<WalletTransactionResponse>> getTransactions(
            @AuthenticationPrincipal UUID userId, Pageable pageable) {
        return ApiResponse.<Page<WalletTransactionResponse>>builder()
                .success(true)
                .message("Fetched wallet transactions")
                .data(walletService.getTransactions(userId, pageable))
                .build();
    }

    /** Start a VNPay deposit; returns the pay URL to redirect to. */
    @PostMapping("/topup")
    public ApiResponse<TopupResponse> topup(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody TopupRequest request) {
        return ApiResponse.<TopupResponse>builder()
                .success(true)
                .message("Top-up initiated")
                .data(walletService.createTopup(userId, request))
                .build();
    }

    /** Request a withdrawal: holds the amount (balance -> locked) and creates a PENDING request. */
    @PostMapping("/withdraw")
    public ApiResponse<WithdrawalResponse> withdraw(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody WithdrawalRequest request) {
        return ApiResponse.<WithdrawalResponse>builder()
                .success(true)
                .message("Withdrawal requested")
                .data(walletService.requestWithdrawal(userId, request))
                .build();
    }

    /**
     * Authoritative server-to-server IPN (VNPay servers, no JWT — the checksum is the only auth).
     * Returns VNPay's own {@code {RspCode, Message}} JSON — NOT the {@link ApiResponse} envelope.
     */
    @GetMapping("/vnpay-ipn")
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        VnPayIpnResult result = vnPayService.handleIpn(params);
        return Map.of("RspCode", result.getRspCode(), "Message", result.getMessage());
    }

    /** Browser return endpoint (display status; credits only when the guarded fallback is enabled). */
    @GetMapping("/vnpay-return")
    public ApiResponse<VnPayIpnResult> vnpayReturn(@RequestParam Map<String, String> params) {
        VnPayIpnResult result = vnPayService.handleReturn(params);
        return ApiResponse.<VnPayIpnResult>builder()
                .success(result.isSuccess())
                .message(result.getMessage())
                .data(result)
                .build();
    }
}

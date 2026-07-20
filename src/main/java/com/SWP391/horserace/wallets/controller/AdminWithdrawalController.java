package com.SWP391.horserace.wallets.controller;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.wallets.dto.WithdrawalResponse;
import com.SWP391.horserace.wallets.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin review of withdrawal requests (list + approve/reject). Every endpoint is ADMIN-gated. */
@RestController
@RequestMapping("/api/v1/admin/withdrawals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminWithdrawalController {

    private final WalletService walletService;

    /** Paged withdrawal queue, newest first. */
    @GetMapping
    public ApiResponse<Page<WithdrawalResponse>> list(Pageable pageable) {
        return ApiResponse.<Page<WithdrawalResponse>>builder()
                .success(true)
                .message("Fetched withdrawals")
                .data(walletService.listWithdrawals(pageable))
                .build();
    }

    /** Approve a PENDING withdrawal: release the lock + record the DEBIT. */
    @PatchMapping("/{id}/approve")
    public ApiResponse<WithdrawalResponse> approve(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
        return ApiResponse.<WithdrawalResponse>builder()
                .success(true)
                .message("Withdrawal approved")
                .data(walletService.approveWithdrawal(adminId, id))
                .build();
    }

    /** Reject a PENDING withdrawal: release the hold back to spendable balance. */
    @PatchMapping("/{id}/reject")
    public ApiResponse<WithdrawalResponse> reject(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
        return ApiResponse.<WithdrawalResponse>builder()
                .success(true)
                .message("Withdrawal rejected")
                .data(walletService.rejectWithdrawal(adminId, id))
                .build();
    }
}

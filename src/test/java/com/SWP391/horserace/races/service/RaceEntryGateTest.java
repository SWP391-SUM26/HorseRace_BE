package com.SWP391.horserace.races.service;

import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.entity.HorseHealthStatus;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.service.HouseWalletService;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import com.SWP391.horserace.wallets.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceEntryGateTest {

    @Mock WalletLedgerService walletLedgerService;
    @Mock WalletService walletService;
    @Mock HouseWalletService houseWalletService;
    @Mock com.SWP391.horserace.registrations.repository.RegistrationRepository registrationRepository;

    @InjectMocks RaceEntryGate gate;

    private Race raceWithFee(BigDecimal fee) {
        return Race.builder().raceId(UUID.randomUUID()).entryFee(fee).build();
    }

    private TournamentRegistration regWith(User owner) {
        return TournamentRegistration.builder().owner(owner).build();
    }

    @Test
    void checkEligibility_injured_throwsNotFit() {
        Horse h = Horse.builder().healthStatus(HorseHealthStatus.INJURED)
                .dateOfBirth(LocalDate.now().minusYears(5)).build();
        assertThatThrownBy(() -> gate.checkEligibility(h, null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.HORSE_NOT_FIT_TO_RACE);
    }

    @Test
    void checkEligibility_belowMinAge_throwsBelowMinAge() {
        Horse h = Horse.builder().healthStatus(HorseHealthStatus.HEALTHY)
                .dateOfBirth(LocalDate.now().minusYears(2)).build(); // below the default minimum of 3
        assertThatThrownBy(() -> gate.checkEligibility(h, null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.HORSE_BELOW_MIN_AGE);
    }

    @Test
    void checkEligibility_healthyAdult_passes() {
        Horse h = Horse.builder().healthStatus(HorseHealthStatus.HEALTHY)
                .dateOfBirth(LocalDate.now().minusYears(4)).build();
        assertThatCode(() -> gate.checkEligibility(h, null)).doesNotThrowAnyException();
    }

    // ── money: charge / refund are exactly-once ──

    private static final String REG_REF = "TOURNAMENT_REGISTRATION";

    private TournamentRegistration paidReg(User owner, UUID regId, String amount) {
        return TournamentRegistration.builder().registrationId(regId).owner(owner)
                .entryFeeAmount(new BigDecimal(amount)).build();
    }

    /**
     * chargeEntryFeeOnce is intentionally a no-op (see RaceEntryGate — owners stopped paying entry
     * fees once the purse became sponsor-funded; the only money an owner spends now is the jockey's
     * wage). This replaces the old "charges on first claim" test, which asserted a ledger write that
     * no longer happens.
     */
    @Test
    void charge_isANoOp_regardlessOfFeeOrRegistration() {
        UUID regId = UUID.randomUUID();
        User owner = User.builder().userId(UUID.randomUUID()).build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(regId).owner(owner).build();

        gate.chargeEntryFeeOnce(reg, raceWithFee(new BigDecimal("50000")));

        verify(registrationRepository, never()).claimEntryFeeCharge(any(), any(), any());
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void charge_noFee_neverEvenClaims() {
        User owner = User.builder().userId(UUID.randomUUID()).build();
        gate.chargeEntryFeeOnce(regWith(owner), raceWithFee(null));
        verify(registrationRepository, never()).claimEntryFeeCharge(any(), any(), any());
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void refund_claimWon_reversesToOwner() {
        UUID houseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID regId = UUID.randomUUID();
        User owner = User.builder().userId(ownerId).build();
        TournamentRegistration reg = paidReg(owner, regId, "50000");
        when(registrationRepository.claimEntryFeeRefund(eq(regId), any())).thenReturn(1);
        when(houseWalletService.houseUserId()).thenReturn(houseId);

        gate.refundEntryFeeOnce(reg);

        verify(walletLedgerService).applyEntry(eq(houseId), eq(EntryType.DEBIT), eq(TxnCategory.REFUND),
                eq(new BigDecimal("50000")), eq(REG_REF), eq(regId));
        verify(walletLedgerService).applyEntry(eq(ownerId), eq(EntryType.CREDIT), eq(TxnCategory.REFUND),
                eq(new BigDecimal("50000")), eq(REG_REF), eq(regId));
        assertThat(reg.getEntryFeeRefundedAt()).isNotNull();
    }

    @Test
    void refund_alreadyRefundedOrNeverPaid_movesNoMoney() {
        UUID regId = UUID.randomUUID();
        User owner = User.builder().userId(UUID.randomUUID()).build();
        when(registrationRepository.claimEntryFeeRefund(eq(regId), any())).thenReturn(0);

        gate.refundEntryFeeOnce(paidReg(owner, regId, "50000"));

        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    /** Never charged -> nothing to give back, and no claim is even attempted. */
    @Test
    void refund_registrationThatNeverPaid_movesNoMoney() {
        User owner = User.builder().userId(UUID.randomUUID()).build();
        gate.refundEntryFeeOnce(regWith(owner));   // entryFeeAmount is null
        verify(registrationRepository, never()).claimEntryFeeRefund(any(), any());
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    /**
     * Refund the amount CHARGED, not the race's current fee. An admin editing the fee between
     * charge and refund would otherwise unbalance the ENTRY_FEE/REFUND ledger permanently.
     */
    @Test
    void refund_usesAmountCharged_notTheRacesCurrentFee() {
        UUID houseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID regId = UUID.randomUUID();
        User owner = User.builder().userId(ownerId).build();
        TournamentRegistration reg = paidReg(owner, regId, "50000");   // paid 50,000
        reg.setRace(raceWithFee(new BigDecimal("90000")));             // fee later raised to 90,000
        when(registrationRepository.claimEntryFeeRefund(eq(regId), any())).thenReturn(1);
        when(houseWalletService.houseUserId()).thenReturn(houseId);

        gate.refundEntryFeeOnce(reg);

        verify(walletLedgerService).applyEntry(eq(ownerId), eq(EntryType.CREDIT), eq(TxnCategory.REFUND),
                eq(new BigDecimal("50000")), eq(REG_REF), eq(regId));
    }
}

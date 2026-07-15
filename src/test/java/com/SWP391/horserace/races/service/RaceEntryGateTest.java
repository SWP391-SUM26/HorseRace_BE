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

    @Test
    void chargeEntryFee_debitsOwnerCreditsHouse() {
        UUID houseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().userId(ownerId).build();
        Race race = raceWithFee(new BigDecimal("50000"));
        when(houseWalletService.houseUserId()).thenReturn(houseId);

        gate.chargeEntryFee(regWith(owner), race);

        verify(walletService).getOrCreateWallet(ownerId);
        verify(walletLedgerService).applyEntry(eq(houseId), eq(EntryType.CREDIT), eq(TxnCategory.ENTRY_FEE),
                eq(new BigDecimal("50000")), eq("RACE"), eq(race.getRaceId()));
        verify(walletLedgerService).applyEntry(eq(ownerId), eq(EntryType.DEBIT), eq(TxnCategory.ENTRY_FEE),
                eq(new BigDecimal("50000")), eq("RACE"), eq(race.getRaceId()));
    }

    @Test
    void chargeEntryFee_noFee_noWalletCalls() {
        User owner = User.builder().userId(UUID.randomUUID()).build();
        gate.chargeEntryFee(regWith(owner), raceWithFee(null));
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void refundEntryFee_reversesToOwner() {
        UUID houseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().userId(ownerId).build();
        Race race = raceWithFee(new BigDecimal("50000"));
        when(houseWalletService.houseUserId()).thenReturn(houseId);

        gate.refundEntryFee(regWith(owner), race);

        verify(walletLedgerService).applyEntry(eq(houseId), eq(EntryType.DEBIT), eq(TxnCategory.REFUND),
                eq(new BigDecimal("50000")), eq("RACE"), eq(race.getRaceId()));
        verify(walletLedgerService).applyEntry(eq(ownerId), eq(EntryType.CREDIT), eq(TxnCategory.REFUND),
                eq(new BigDecimal("50000")), eq("RACE"), eq(race.getRaceId()));
    }
}

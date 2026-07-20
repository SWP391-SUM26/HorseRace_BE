package com.SWP391.horserace.races.service;

import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.entity.HorseHealthStatus;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.tournaments.entity.EligibilityCriteria;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.service.HouseWalletService;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import com.SWP391.horserace.wallets.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/**
 * Enforces race-entry eligibility (health + minimum age) and charges/refunds the per-race entry fee.
 * Every path that creates or scratches a {@link com.SWP391.horserace.races.entity.RaceEntry} funnels
 * through this gate so the rules hold no matter who triggers entry (owner, admin, or referee approval).
 *
 * <p>Fee model: the owner's wallet is DEBITed into the house wallet on entry (like a bet stake) and
 * reversed on a pre-finalization scratch. Both moves run in the caller's transaction, so a failed
 * entry insert also rolls back the debit.
 */
@Service
@RequiredArgsConstructor
public class RaceEntryGate {

    private final WalletLedgerService walletLedgerService;
    private final WalletService walletService;
    private final HouseWalletService houseWalletService;

    private static final int DEFAULT_MIN_AGE_YEARS = 3;

    /** Eligibility gate + entry-fee charge. Call immediately before saving a new RaceEntry. */
    public void admit(TournamentRegistration registration, Race race) {
        checkEligibility(registration != null ? registration.getHorse() : null,
                race != null ? race.getTournament() : null);
        chargeEntryFee(registration, race);
    }

    /** Eligibility only (health + minimum age) against a tournament's criteria — used at approval time. */
    public void checkEligibility(Horse horse, Tournament tournament) {
        if (horse == null) {
            return;
        }
        HorseHealthStatus health = horse.getHealthStatus();
        if (health == HorseHealthStatus.INJURED
                || health == HorseHealthStatus.UNFIT
                || health == HorseHealthStatus.QUARANTINE) {
            throw new AppException(ErrorCode.HORSE_NOT_FIT_TO_RACE);
        }
        LocalDate dob = horse.getDateOfBirth();
        if (dob != null && Period.between(dob, LocalDate.now()).getYears() < resolveMinAge(tournament)) {
            throw new AppException(ErrorCode.HORSE_BELOW_MIN_AGE);
        }
    }

    /** Debit the owner's entry fee into the house wallet (no-op when the race has no fee). */
    public void chargeEntryFee(TournamentRegistration registration, Race race) {
        BigDecimal fee = race != null ? race.getEntryFee() : null;
        User owner = registration != null ? registration.getOwner() : null;
        if (fee == null || fee.signum() <= 0 || owner == null) {
            return;
        }
        UUID houseUserId = houseWalletService.houseUserId();
        walletService.getOrCreateWallet(owner.getUserId());
        // House-first ordering (matches betting) keeps concurrent money moves deadlock-safe.
        walletLedgerService.applyEntry(houseUserId, EntryType.CREDIT, TxnCategory.ENTRY_FEE, fee, "RACE", race.getRaceId());
        walletLedgerService.applyEntry(owner.getUserId(), EntryType.DEBIT, TxnCategory.ENTRY_FEE, fee, "RACE", race.getRaceId());
    }

    /** Reverse the entry fee to the owner on scratch (no-op when the race has no fee). */
    public void refundEntryFee(TournamentRegistration registration, Race race) {
        BigDecimal fee = race != null ? race.getEntryFee() : null;
        User owner = registration != null ? registration.getOwner() : null;
        if (fee == null || fee.signum() <= 0 || owner == null) {
            return;
        }
        UUID houseUserId = houseWalletService.houseUserId();
        walletService.getOrCreateWallet(owner.getUserId());
        walletLedgerService.applyEntry(houseUserId, EntryType.DEBIT, TxnCategory.REFUND, fee, "RACE", race.getRaceId());
        walletLedgerService.applyEntry(owner.getUserId(), EntryType.CREDIT, TxnCategory.REFUND, fee, "RACE", race.getRaceId());
    }

    private int resolveMinAge(Tournament tournament) {
        EligibilityCriteria elig = tournament != null ? tournament.getEligibility() : null;
        if (elig != null && elig.getMinAgeYears() != null) {
            return elig.getMinAgeYears();
        }
        return DEFAULT_MIN_AGE_YEARS;
    }
}

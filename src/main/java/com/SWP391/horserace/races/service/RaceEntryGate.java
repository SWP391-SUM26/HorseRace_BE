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
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.UUID;

/**
 * Enforces race-entry eligibility (health + minimum age) and charges/refunds the per-race entry fee.
 * Every path that creates or scratches a {@link com.SWP391.horserace.races.entity.RaceEntry} funnels
 * through this gate so the rules hold no matter who triggers entry (owner, admin, or referee approval).
 *
 * <p>Fee model: the owner's wallet is DEBITed into the house wallet when they commit to a race, and
 * reversed on rejection, withdrawal, scratch or race cancellation. Both moves run in the caller's
 * transaction, so a failed entry insert also rolls back the debit.
 *
 * <p>Both money methods are exactly-once: they take an atomic claim on the registration first, so a
 * retry, a concurrent request, or a second code path cannot charge or refund twice.
 */
@Service
@RequiredArgsConstructor
public class RaceEntryGate {

    private final WalletLedgerService walletLedgerService;
    private final WalletService walletService;
    private final HouseWalletService houseWalletService;
    private final com.SWP391.horserace.registrations.repository.RegistrationRepository registrationRepository;

    /** Ledger tag for both halves of the entry fee — the registration, not the race. */
    private static final String REGISTRATION_REF = "TOURNAMENT_REGISTRATION";

    private static final int DEFAULT_MIN_AGE_YEARS = 3;

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

    /**
     * Entering a race is free. Retained as a no-op so the three services that enter horses still
     * funnel through one place if charging ever comes back.
     *
     * <p>Entry fees were how the platform pretended to fund prize money, and they never came close:
     * ~105M collected against ~950M already awarded. The purse is sponsor money now — the organiser
     * funds it into the house wallet when a tournament is published (see
     * {@code TxnCategory#SPONSOR}). With that settled there is no reason to charge the owner to
     * enter, so the only money an owner spends is the jockey's hire fee, escrowed by
     * {@link com.SWP391.horserace.assignments.service.JockeyFeeGate}.
     *
     * <p>{@link #refundEntryFeeOnce} is deliberately NOT a no-op: registrations charged before this
     * change still hold owners' money, and their four refund paths must keep returning it.
     */
    public void chargeEntryFeeOnce(TournamentRegistration registration, Race race) {
        // Intentionally empty — see the note above.
    }

    /**
     * Return the entry fee to the owner, at most once per registration.
     *
     * <p>Refunds {@code registration.entryFeeAmount} — the amount actually charged — never the
     * race's current fee. An admin editing the fee between charge and refund would otherwise
     * unbalance the ENTRY_FEE/REFUND ledger permanently.
     *
     * <p>No-op when the registration never paid, which is why no caller needs a special case for it.
     */
    public void refundEntryFeeOnce(TournamentRegistration registration) {
        User owner = registration != null ? registration.getOwner() : null;
        BigDecimal fee = registration != null ? registration.getEntryFeeAmount() : null;
        if (owner == null || fee == null || fee.signum() <= 0) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (registrationRepository.claimEntryFeeRefund(registration.getRegistrationId(), now) == 0) {
            return; // never paid, or already refunded
        }
        registration.setEntryFeeRefundedAt(now);

        UUID houseUserId = houseWalletService.houseUserId();
        walletService.getOrCreateWallet(owner.getUserId());
        walletLedgerService.applyEntry(houseUserId, EntryType.DEBIT, TxnCategory.REFUND, fee,
                REGISTRATION_REF, registration.getRegistrationId());
        walletLedgerService.applyEntry(owner.getUserId(), EntryType.CREDIT, TxnCategory.REFUND, fee,
                REGISTRATION_REF, registration.getRegistrationId());
    }

    private int resolveMinAge(Tournament tournament) {
        EligibilityCriteria elig = tournament != null ? tournament.getEligibility() : null;
        if (elig != null && elig.getMinAgeYears() != null) {
            return elig.getMinAgeYears();
        }
        return DEFAULT_MIN_AGE_YEARS;
    }
}

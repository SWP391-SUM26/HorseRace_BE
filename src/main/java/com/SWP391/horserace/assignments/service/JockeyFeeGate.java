package com.SWP391.horserace.assignments.service;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.entity.Wallet;
import com.SWP391.horserace.wallets.entity.WalletStatus;
import com.SWP391.horserace.wallets.repository.WalletRepository;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import com.SWP391.horserace.wallets.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Escrows the jockey's hire fee for the life of an invitation.
 *
 * <p>The owner pays exactly one thing now — the rider's wage. (The prize purse is sponsor money and
 * entering a race is free.) But the wage is not handed over when the invitation is sent, because at
 * that point nobody has ridden anything: it is <em>locked</em> out of the owner's spendable balance
 * and only crosses into the jockey's wallet once the race has actually run and been certified.
 *
 * <pre>
 *   invite                                   balance -> locked_balance   (hold)
 *   decline / cancel / withdraw / race off   locked_balance -> balance   (release)
 *   race certified                           locked_balance -> jockey    (payout, ledger DEBIT+CREDIT)
 * </pre>
 *
 * <p>A hold that never settles writes no ledger row at all — nothing moved between wallets, only
 * between two columns of the same wallet. The ledger is written once, at payout.
 *
 * <p>All three methods are exactly-once: each takes an atomic claim on the assignment before it
 * touches money, so a retry, a concurrent request, or a second code path cannot double-hold,
 * double-release, or both release and pay the same fee. They mirror
 * {@link com.SWP391.horserace.races.service.RaceEntryGate} deliberately — same shape, same reasons.
 */
@Service
@RequiredArgsConstructor
public class JockeyFeeGate {

    private final JockeyAssignmentRepository assignmentRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final WalletLedgerService walletLedgerService;

    /** Ledger tag for the payout — the invitation, which is what identifies one hire. */
    private static final String ASSIGNMENT_REF = "JOCKEY_ASSIGNMENT";

    /**
     * Lock the agreed fee in the owner's wallet, at most once per invitation.
     *
     * <p>Throws {@link ErrorCode#INSUFFICIENT_BALANCE} when the owner cannot cover it — an owner who
     * cannot pay must not be able to promise a ride, or the jockey rides for money that was never
     * there. This is the one path here that rejects rather than no-ops, because it runs while the
     * user is watching and the invitation must not be created.
     *
     * <p>No-op when the invitation carries no fee.
     */
    public void holdFeeOnce(JockeyAssignment assignment, User owner) {
        BigDecimal fee = assignment != null ? assignment.getAgreedBaseFee() : null;
        if (fee == null || fee.signum() <= 0 || owner == null) {
            return;
        }
        Wallet wallet = walletRepository.findByUserUserIdForUpdate(owner.getUserId())
                .orElseGet(() -> {
                    walletService.getOrCreateWallet(owner.getUserId());
                    return walletRepository.findByUserUserIdForUpdate(owner.getUserId())
                            .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
                });
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new AppException(ErrorCode.WALLET_INACTIVE);
        }
        // Check before claiming: a rejected hire must leave no trace on the assignment.
        if (wallet.getBalance().compareTo(fee) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (assignmentRepository.claimFeeHold(assignment.getAssignmentId(), fee, now) == 0) {
            return; // already held — another path got here first
        }
        // Keep the in-memory entity truthful; the caller maps it to a response straight after.
        assignment.setFeeHeldAmount(fee);
        assignment.setFeeHeldAt(now);

        wallet.setBalance(wallet.getBalance().subtract(fee));
        wallet.setLockedBalance(wallet.getLockedBalance().add(fee));
        walletRepository.save(wallet);
    }

    /**
     * Give the hold back to the owner's spendable balance, at most once per invitation.
     *
     * <p>Releases {@code feeHeldAmount} — what was actually locked — never the current
     * {@code agreedBaseFee}: an edit between hold and release would otherwise strand the difference
     * in {@code locked_balance} forever.
     *
     * <p>No-op when nothing was held or it already settled, which is why no caller needs a special
     * case for invitations created before escrow existed.
     */
    public void releaseFeeOnce(JockeyAssignment assignment) {
        BigDecimal fee = assignment != null ? assignment.getFeeHeldAmount() : null;
        User owner = resolveOwner(assignment);
        if (fee == null || fee.signum() <= 0 || owner == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (assignmentRepository.claimFeeRelease(assignment.getAssignmentId(), now) == 0) {
            return; // never held, or already released/paid
        }
        assignment.setFeeReleasedAt(now);

        Wallet wallet = walletRepository.findByUserUserIdForUpdate(owner.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
        wallet.setLockedBalance(wallet.getLockedBalance().subtract(fee));
        wallet.setBalance(wallet.getBalance().add(fee));
        walletRepository.save(wallet);
    }

    /**
     * Move the held fee from the owner to the jockey, at most once per invitation.
     *
     * <p>Called when the race is certified — the rider has done the job. The lock is dropped and the
     * two ledger halves are written together, so JOCKEY_FEE nets to zero across all wallets.
     *
     * <p>No-op when nothing was held or it already settled.
     */
    public void payJockeyOnce(JockeyAssignment assignment) {
        BigDecimal fee = assignment != null ? assignment.getFeeHeldAmount() : null;
        User owner = resolveOwner(assignment);
        User jockey = assignment != null ? assignment.getJockey() : null;
        if (fee == null || fee.signum() <= 0 || owner == null || jockey == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (assignmentRepository.claimFeePayout(assignment.getAssignmentId(), now) == 0) {
            return; // never held, or already released/paid
        }
        assignment.setFeePaidAt(now);

        // Drop the lock first. The money is leaving this wallet, so it must not stay counted as the
        // owner's — and applyEntry's DEBIT works off `balance`, which the hold already reduced.
        Wallet ownerWallet = walletRepository.findByUserUserIdForUpdate(owner.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
        ownerWallet.setLockedBalance(ownerWallet.getLockedBalance().subtract(fee));
        ownerWallet.setBalance(ownerWallet.getBalance().add(fee));
        walletRepository.save(ownerWallet);

        walletService.getOrCreateWallet(jockey.getUserId());
        // Payer first, then beneficiary — the same lock order every two-sided move here uses, so
        // concurrent settlements cannot deadlock against each other.
        walletLedgerService.applyEntry(owner.getUserId(), EntryType.DEBIT, TxnCategory.JOCKEY_FEE,
                fee, ASSIGNMENT_REF, assignment.getAssignmentId());
        walletLedgerService.applyEntry(jockey.getUserId(), EntryType.CREDIT, TxnCategory.JOCKEY_FEE,
                fee, ASSIGNMENT_REF, assignment.getAssignmentId());
    }

    /** Release every outstanding hold on a race — used when the race itself is cancelled. */
    public void releaseAllForRace(UUID raceId) {
        for (JockeyAssignment assignment : assignmentRepository.findByRaceId(raceId)) {
            releaseFeeOnce(assignment);
        }
    }

    /** The owner is the one who registered the horse this entry belongs to. */
    private User resolveOwner(JockeyAssignment assignment) {
        if (assignment == null || assignment.getEntry() == null
                || assignment.getEntry().getRegistration() == null) {
            return null;
        }
        return assignment.getEntry().getRegistration().getOwner();
    }
}

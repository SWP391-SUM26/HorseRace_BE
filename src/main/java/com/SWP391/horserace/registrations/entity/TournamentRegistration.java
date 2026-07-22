package com.SWP391.horserace.registrations.entity;

import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.users.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Maps the {@code tournament_registration} table (owner enters a horse into a tournament). */
@Entity
@Table(name = "tournament_registration")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "registration_id", updatable = false, nullable = false)
    private UUID registrationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id")
    private Horse horse;

    /** Optional race the owner chose at registration time. When set, approval auto-creates a race_entry. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id")
    private Race race;

    @Column(name = "registration_code", nullable = false, unique = true, length = 50)
    private String registrationCode;

    /** DRAFT | SUBMITTED | UNDER_REVIEW | APPROVED | REJECTED | WITHDRAWN */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private RegistrationStatus status = RegistrationStatus.SUBMITTED;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "legal_basis_ref")
    private String legalBasisRef;

    /** FE-v2 Registration Management (mục 8): category filter, e.g. "GROUP_1". */
    @Column(name = "category", length = 50)
    private String category;

    /**
     * The entry fee actually charged, frozen at charge time.
     *
     * <p>Deliberately NOT re-read from {@code race.entryFee} when refunding: an admin editing a
     * race's fee between charge and refund would otherwise unbalance the ENTRY_FEE/REFUND ledger
     * permanently. Refund what was taken, never what the fee happens to be now.
     */
    @Column(name = "entry_fee_amount", precision = 18, scale = 2)
    private BigDecimal entryFeeAmount;

    /**
     * Set when the fee is taken, cleared to NULL only on a resubmit. Together with
     * {@link #entryFeeRefundedAt} this is the exactly-once claim: charge and refund are conditional
     * UPDATEs on these being NULL / non-NULL, so concurrent callers cannot double-charge or
     * double-refund. The ledger cannot answer this on its own — its rows are tagged by race, not
     * by registration.
     */
    @Column(name = "entry_fee_paid_at")
    private OffsetDateTime entryFeePaidAt;

    @Column(name = "entry_fee_refunded_at")
    private OffsetDateTime entryFeeRefundedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

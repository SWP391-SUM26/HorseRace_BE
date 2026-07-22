package com.SWP391.horserace.registrations.service.impl;

import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceEntryStatus;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.registrations.dto.RegistrationFilterRequest;
import com.SWP391.horserace.registrations.dto.RegistrationRequest;
import com.SWP391.horserace.registrations.dto.RegistrationResponse;
import com.SWP391.horserace.registrations.dto.RegistrationStatsResponse;
import com.SWP391.horserace.registrations.dto.RejectRegistrationRequest;
import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.registrations.repository.RegistrationSpecification;
import com.SWP391.horserace.registrations.service.RegistrationService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.tournaments.entity.TournamentStatus;
import com.SWP391.horserace.tournaments.repository.TournamentRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final int MAX_PAGE_SIZE = 100;

    private final RegistrationRepository registrationRepository;
    private final TournamentRepository tournamentRepository;
    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final com.SWP391.horserace.races.service.RaceEntryGate raceEntryGate;
    private final com.SWP391.horserace.attachments.repository.AttachmentRepository attachmentRepository;

    @Override
    @Transactional
    public RegistrationResponse submitRegistration(UUID currentUserId, RegistrationRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // Horse must exist and the caller must own it (ADMIN may register on anyone's behalf).
        Horse horse = horseRepository.findByHorseIdAndDeletedFalse(request.horseId())
                .orElseThrow(() -> new AppException(ErrorCode.HORSE_NOT_FOUND));
        if (!isOwnerOrAdmin(horse, currentUserId)) {
            throw new AppException(ErrorCode.NOT_HORSE_OWNER);
        }

        // Tournament must exist and currently accept registrations.
        Tournament tournament = tournamentRepository.findById(request.tournamentId())
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }
        if (tournament.getStatus() != TournamentStatus.PUBLISHED
                && tournament.getStatus() != TournamentStatus.REGISTRATION_OPEN) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_ACCEPTING_REGISTRATION);
        }
        // The status alone used to be the whole gate, so a PUBLISHED tournament whose window opens
        // next month still accepted entries. Both bounds are optional: a null bound means "unbounded
        // on that side", which keeps tournaments that never set a window behaving as before.
        OffsetDateTime now = OffsetDateTime.now();
        if (tournament.getRegistrationOpenAt() != null && now.isBefore(tournament.getRegistrationOpenAt())) {
            throw new AppException(ErrorCode.REGISTRATION_WINDOW_NOT_OPEN);
        }
        if (tournament.getRegistrationCloseAt() != null && now.isAfter(tournament.getRegistrationCloseAt())) {
            throw new AppException(ErrorCode.REGISTRATION_WINDOW_CLOSED);
        }

        // One live registration per (tournament, horse). A registration that ended — rejected,
        // withdrawn or removed — must NOT block a fresh attempt: the owner has already paid the fee
        // and been refunded, and blocking them here would leave them permanently unable to enter a
        // tournament they just paid for. The DB UNIQUE(tournament_id, horse_id) still holds, so the
        // dead row is REUSED rather than a second one inserted.
        TournamentRegistration existing = registrationRepository
                .findFirstByTournament_TournamentIdAndHorse_HorseId(
                        tournament.getTournamentId(), horse.getHorseId())
                .orElse(null);
        if (existing != null && !isTerminal(existing.getStatus())) {
            throw new AppException(ErrorCode.REGISTRATION_ALREADY_EXISTS);
        }

        // Optional chosen race: must exist and belong to the same tournament.
        Race chosenRace = null;
        if (request.raceId() != null) {
            chosenRace = raceRepository.findByRaceIdAndDeletedFalse(request.raceId())
                    .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));
            UUID raceTournamentId = chosenRace.getTournament() != null
                    ? chosenRace.getTournament().getTournamentId() : null;
            if (!tournament.getTournamentId().equals(raceTournamentId)) {
                throw new AppException(ErrorCode.RACE_TOURNAMENT_MISMATCH);
            }
        }

        // The registering user (owner of record on the registration).
        User owner = userRepository.findByUserIdAndDeletedFalse(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        TournamentRegistration registration;
        if (existing != null) {
            // Reuse the terminated row (see the duplicate check above). Both fee stamps are cleared
            // so the new attempt is charged again — the previous cycle was already refunded.
            registration = existing;
            registration.setOwner(owner);
            registration.setRace(chosenRace);
            registration.setStatus(RegistrationStatus.SUBMITTED);
            registration.setSubmittedAt(OffsetDateTime.now());
            registration.setReviewedAt(null);
            registration.setApprovedBy(null);
            registration.setRejectionReason(null);
            registration.setEntryFeeAmount(null);
            registration.setEntryFeePaidAt(null);
            registration.setEntryFeeRefundedAt(null);
        } else {
            registration = TournamentRegistration.builder()
                    .owner(owner)
                    .tournament(tournament)
                    .horse(horse)
                    .race(chosenRace)
                    .registrationCode(generateRegistrationCode())
                    .status(RegistrationStatus.SUBMITTED)
                    .submittedAt(OffsetDateTime.now())
                    .build();
        }

        TournamentRegistration saved = registrationRepository.save(registration);

        // Charge HERE, not at approval. Inside the same transaction and after every validation, so
        // an insufficient balance rolls the whole submission back and the error reaches the person
        // whose wallet it is. It used to fire inside the REFEREE's approve transaction, telling them
        // about someone else's money and silently discarding their approval.
        //
        // No race chosen means no fee is knowable (the fee lives on the race); the charge then
        // happens wherever the race is first picked.
        if (chosenRace != null) {
            raceEntryGate.chargeEntryFeeOnce(saved, chosenRace);
        }

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RegistrationResponse> listRegistrations(RegistrationFilterRequest filter) {
        return registrationRepository
                .findAll(RegistrationSpecification.withFilters(filter), buildPageable(filter))
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public RegistrationResponse getRegistrationById(UUID id) {
        TournamentRegistration registration = registrationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));
        return mapToResponse(registration);
    }

    @Override
    @Transactional(readOnly = true)
    public RegistrationStatsResponse getStats(UUID tournamentId) {
        List<RegistrationRepository.StatusCount> rows = (tournamentId == null)
                ? registrationRepository.countGroupByStatus()
                : registrationRepository.countGroupByStatusForTournament(tournamentId);

        long total = 0, pending = 0, approved = 0, rejected = 0;
        for (RegistrationRepository.StatusCount row : rows) {
            long cnt = row.getCnt();
            total += cnt;
            switch (row.getStatus()) {
                case SUBMITTED, UNDER_REVIEW -> pending += cnt;
                case APPROVED -> approved += cnt;
                case REJECTED -> rejected += cnt;
                default -> { /* DRAFT / WITHDRAWN count toward total only */ }
            }
        }

        return RegistrationStatsResponse.builder()
                .total(total)
                .pending(pending)
                .approved(approved)
                .rejected(rejected)
                .build();
    }

    @Override
    @Transactional
    public RegistrationResponse approveRegistration(UUID currentUserId, UUID id) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        TournamentRegistration registration = loadRegistration(id);
        requireSourceStatus(registration,
                RegistrationStatus.SUBMITTED, RegistrationStatus.UNDER_REVIEW);

        // #7: a registration can't be approved without a vaccination/medical document uploaded by its
        // OWNER (an attachment merely tagged with this id by some other user does not satisfy the gate).
        UUID ownerUserId = registration.getOwner() != null ? registration.getOwner().getUserId() : null;
        if (ownerUserId == null || !attachmentRepository
                .existsByOwnerEntityTypeAndOwnerEntityIdAndUploadedBy_UserId("TOURNAMENT_REGISTRATION", id, ownerUserId)) {
            throw new AppException(ErrorCode.REGISTRATION_DOCUMENT_REQUIRED);
        }

        // Don't approve an ineligible horse (unfit/injured/quarantine or below minimum age).
        raceEntryGate.checkEligibility(registration.getHorse(), registration.getTournament());

        User reviewer = userRepository.findByUserIdAndDeletedFalse(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        registration.setStatus(RegistrationStatus.APPROVED);
        registration.setApprovedBy(reviewer);
        registration.setReviewedAt(OffsetDateTime.now());

        TournamentRegistration saved = registrationRepository.save(registration);

        // "Approved into the race": if the owner chose a race, auto-create the race entry.
        if (saved.getRace() != null) {
            enterIntoRace(saved);
        }

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public RegistrationResponse rejectRegistration(UUID currentUserId, UUID id, RejectRegistrationRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        TournamentRegistration registration = loadRegistration(id);
        requireSourceStatus(registration,
                RegistrationStatus.SUBMITTED, RegistrationStatus.UNDER_REVIEW);

        // Reject does NOT set approvedBy — that field records the approver only. A rejected
        // registration leaves approvedBy null; the reviewer is captured by reviewedAt + reason.
        registration.setStatus(RegistrationStatus.REJECTED);
        registration.setRejectionReason(request.reason());
        registration.setReviewedAt(OffsetDateTime.now());
        // The owner paid at submit; a rejection must give it back. No-op if they never paid.
        raceEntryGate.refundEntryFeeOnce(registration);

        return mapToResponse(registrationRepository.save(registration));
    }

    @Override
    @Transactional
    public RegistrationResponse withdrawRegistration(UUID currentUserId, UUID id) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        TournamentRegistration registration = loadRegistration(id);

        if (!isOwnerOrAdmin(registration, currentUserId)) {
            throw new AppException(ErrorCode.NOT_REGISTRATION_OWNER);
        }
        requireSourceStatus(registration,
                RegistrationStatus.DRAFT, RegistrationStatus.SUBMITTED, RegistrationStatus.UNDER_REVIEW);

        registration.setStatus(RegistrationStatus.WITHDRAWN);
        raceEntryGate.refundEntryFeeOnce(registration);

        return mapToResponse(registrationRepository.save(registration));
    }

    @Override
    @Transactional
    public void deleteRegistration(UUID currentUserId, UUID id) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        TournamentRegistration registration = loadRegistration(id);

        // Only an APPROVED reg has a race entry to reconcile. Do this BEFORE flipping status so a
        // finalized-race refusal changes nothing.
        if (registration.getStatus() == RegistrationStatus.APPROVED) {
            raceEntryRepository.findByRegistration_RegistrationId(id).ifPresent(entry -> {
                Race race = entry.getRace();
                // Risk (b): never pull an entry from a race whose result is already locked in.
                if (race != null && (race.getStatus() == RaceStatus.FINISHED
                        || race.getStatus() == RaceStatus.OFFICIAL)) {
                    throw new AppException(ErrorCode.RACE_ALREADY_FINALIZED);
                }
                // RT-CRITICAL: SCRATCH (soft) — never .delete(): downstream FKs (jockey_assignment,
                // race_result, inspections) are RESTRICT and would throw a DataIntegrityViolation.
                entry.setStatus(RaceEntryStatus.SCRATCHED);
                raceEntryRepository.save(entry);
                // (refund happens below, outside this branch — an unapproved registration can
                //  also hold a paid fee)
            });
        }

        // Outside the APPROVED branch on purpose: under charge-at-submit a registration that was
        // never approved can still hold a paid fee, and leaving the refund inside would strand it.
        raceEntryGate.refundEntryFeeOnce(registration);

        registration.setStatus(RegistrationStatus.REMOVED);
        registrationRepository.save(registration);
    }

    // ── helpers ──

    /** A registration in one of these states is finished with — it no longer holds its slot. */
    private static boolean isTerminal(RegistrationStatus status) {
        return status == RegistrationStatus.REJECTED
                || status == RegistrationStatus.WITHDRAWN
                || status == RegistrationStatus.REMOVED;
    }

    private TournamentRegistration loadRegistration(UUID id) {
        return registrationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));
    }

    /**
     * Create the race entry for an approved registration that carries a chosen race.
     * Mirrors RaceServiceImpl.assignParticipant guards (race open + capacity). The DB
     * UNIQUE(race_id, registration_id) is the final guard against a double-insert on
     * re-approve, surfacing as a 409 via the existing DataIntegrityViolation handler.
     */
    private void enterIntoRace(TournamentRegistration registration) {
        Race race = registration.getRace();

        // Only OPEN races accept entries (approval creates the entry).
        if (race.getStatus() != RaceStatus.OPEN) {
            throw new AppException(ErrorCode.RACE_NOT_OPEN_FOR_ENTRY);
        }
        if (race.getMaxParticipants() != null
                && raceEntryRepository.countByRace_RaceId(race.getRaceId()) >= race.getMaxParticipants()) {
            throw new AppException(ErrorCode.RACE_FULL);
        }

        // Eligibility must re-run at approval — a horse can be injured between submit and approve.
        // The charge is a safety net only: a registration carrying a race already paid at submit,
        // so the claim returns 0 and nothing moves. It exists for rows created before this change.
        raceEntryGate.checkEligibility(registration.getHorse(), race.getTournament());
        raceEntryGate.chargeEntryFeeOnce(registration, race);

        RaceEntry entry = RaceEntry.builder()
                .registration(registration)
                .race(race)
                .entryCode(generateEntryCode())
                .status(RaceEntryStatus.ENTERED)
                .build();
        raceEntryRepository.save(entry);
    }

    /** Sequential code ENTnnnnn, skipping any already taken (the DB UNIQUE is the final guard). */
    private String generateEntryCode() {
        long n = raceEntryRepository.count() + 1;
        String code;
        do {
            code = String.format("ENT%05d", n++);
        } while (raceEntryRepository.existsByEntryCode(code));
        return code;
    }

    private void requireSourceStatus(TournamentRegistration registration, RegistrationStatus... allowed) {
        for (RegistrationStatus s : allowed) {
            if (registration.getStatus() == s) {
                return;
            }
        }
        throw new AppException(ErrorCode.REGISTRATION_INVALID_STATUS);
    }

    private boolean isOwnerOrAdmin(Horse horse, UUID currentUserId) {
        if (horse.getOwner() != null && currentUserId.equals(horse.getOwner().getUserId())) {
            return true;
        }
        return isAdmin(currentUserId);
    }

    private boolean isOwnerOrAdmin(TournamentRegistration registration, UUID currentUserId) {
        if (registration.getOwner() != null && currentUserId.equals(registration.getOwner().getUserId())) {
            return true;
        }
        return isAdmin(currentUserId);
    }

    private boolean isAdmin(UUID currentUserId) {
        User current = userRepository.findByUserIdAndDeletedFalse(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return current.getRole() != null && ADMIN_ROLE_CODE.equals(current.getRole().getRoleCode());
    }

    /** Sequential code REGnnnnn, skipping any already taken (the DB UNIQUE is the final guard). */
    private String generateRegistrationCode() {
        long n = registrationRepository.count() + 1;
        String code;
        do {
            code = String.format("REG%05d", n++);
        } while (registrationRepository.existsByRegistrationCode(code));
        return code;
    }

    private Pageable buildPageable(RegistrationFilterRequest f) {
        int page = (f.getPage() != null && f.getPage() >= 0) ? f.getPage() : 0;
        int size = (f.getSize() != null && f.getSize() > 0) ? Math.min(f.getSize(), MAX_PAGE_SIZE) : 10;
        String field = switch (f.getSortBy() != null ? f.getSortBy().trim().toLowerCase() : "createdat") {
            case "submittedat" -> "submittedAt";
            case "reviewedat" -> "reviewedAt";
            case "registrationcode", "code" -> "registrationCode";
            case "status" -> "status";
            default -> "createdAt";
        };
        Sort.Direction dir = "asc".equalsIgnoreCase(f.getSortDir()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(dir, field));
    }

    private RegistrationResponse mapToResponse(TournamentRegistration r) {
        User owner = r.getOwner();
        Tournament tournament = r.getTournament();
        Horse horse = r.getHorse();
        Race race = r.getRace();
        User approvedBy = r.getApprovedBy();

        return RegistrationResponse.builder()
                .registrationId(r.getRegistrationId())
                .registrationCode(r.getRegistrationCode())
                .status(r.getStatus())
                .ownerUserId(owner != null ? owner.getUserId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .tournamentId(tournament != null ? tournament.getTournamentId() : null)
                .tournamentName(tournament != null ? tournament.getName() : null)
                .horseId(horse != null ? horse.getHorseId() : null)
                .horseName(horse != null ? horse.getName() : null)
                .horseCode(horse != null ? horse.getHorseCode() : null)
                .raceId(race != null ? race.getRaceId() : null)
                .raceName(race != null ? race.getName() : null)
                .category(r.getCategory())
                .submittedAt(r.getSubmittedAt())
                .reviewedAt(r.getReviewedAt())
                .approvedByUserId(approvedBy != null ? approvedBy.getUserId() : null)
                .rejectionReason(r.getRejectionReason())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}

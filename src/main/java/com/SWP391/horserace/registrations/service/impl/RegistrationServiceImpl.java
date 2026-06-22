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

        // No duplicate (tournament, horse) pair.
        if (registrationRepository.existsByTournament_TournamentIdAndHorse_HorseId(
                tournament.getTournamentId(), horse.getHorseId())) {
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

        TournamentRegistration registration = TournamentRegistration.builder()
                .owner(owner)
                .tournament(tournament)
                .horse(horse)
                .race(chosenRace)
                .registrationCode(generateRegistrationCode())
                .status(RegistrationStatus.SUBMITTED)
                .submittedAt(OffsetDateTime.now())
                .build();

        return mapToResponse(registrationRepository.save(registration));
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
    @Transactional
    public RegistrationResponse approveRegistration(UUID currentUserId, UUID id) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        TournamentRegistration registration = loadRegistration(id);
        requireSourceStatus(registration,
                RegistrationStatus.SUBMITTED, RegistrationStatus.UNDER_REVIEW);

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

        return mapToResponse(registrationRepository.save(registration));
    }

    // ── helpers ──

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

        if (race.getStatus() != RaceStatus.SCHEDULED && race.getStatus() != RaceStatus.OPEN) {
            throw new AppException(ErrorCode.RACE_NOT_OPEN_FOR_ENTRY);
        }
        if (race.getMaxParticipants() != null
                && raceEntryRepository.countByRace_RaceId(race.getRaceId()) >= race.getMaxParticipants()) {
            throw new AppException(ErrorCode.RACE_FULL);
        }

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
                .submittedAt(r.getSubmittedAt())
                .reviewedAt(r.getReviewedAt())
                .approvedByUserId(approvedBy != null ? approvedBy.getUserId() : null)
                .rejectionReason(r.getRejectionReason())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}

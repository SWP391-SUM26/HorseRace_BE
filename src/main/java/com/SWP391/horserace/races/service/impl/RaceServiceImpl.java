package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.races.dto.AssignParticipantRequest;
import com.SWP391.horserace.races.dto.RaceEntryResponse;
import com.SWP391.horserace.races.dto.RaceFilterRequest;
import com.SWP391.horserace.races.dto.RaceRequest;
import com.SWP391.horserace.races.dto.RaceResponse;
import com.SWP391.horserace.races.dto.ScheduleRaceRequest;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceEntryStatus;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.races.service.RaceService;
import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.tournaments.repository.TournamentRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RaceServiceImpl implements RaceService {

    private static final int MAX_PAGE_SIZE = 100;

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final RegistrationRepository registrationRepository;
    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<RaceResponse> listRaces(RaceFilterRequest filter) {
        Specification<Race> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isFalse(root.get("deleted")));

            if (filter.getQ() != null && !filter.getQ().isBlank()) {
                String like = "%" + filter.getQ().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("raceCode")), like),
                        cb.like(cb.lower(root.get("name")), like)));
            }
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getTournamentId() != null) {
                predicates.add(cb.equal(root.get("tournament").get("tournamentId"), filter.getTournamentId()));
            }
            if (filter.getRaceType() != null && !filter.getRaceType().isBlank()) {
                predicates.add(cb.equal(root.get("raceType"), filter.getRaceType()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return raceRepository.findAll(spec, buildPageable(filter)).map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public RaceResponse getRaceById(UUID id) {
        return mapToResponse(loadRace(id));
    }

    @Override
    @Transactional
    public RaceResponse createRace(UUID currentUserId, RaceRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Tournament tournament = tournamentRepository.findById(request.tournamentId())
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }

        Race race = Race.builder()
                .tournament(tournament)
                .raceCode(generateRaceCode())
                .name(request.name())
                .raceType(request.raceType())
                .distanceMeter(request.distanceMeter())
                .trackCondition(request.trackCondition())
                .weatherCondition(request.weatherCondition())
                .scheduledStartAt(request.scheduledStartAt())
                .predictionCutoffAt(request.predictionCutoffAt())
                .maxParticipants(request.maxParticipants())
                .status(request.status() != null ? request.status() : RaceStatus.SCHEDULED)
                .build();

        return mapToResponse(raceRepository.save(race));
    }

    @Override
    @Transactional
    public RaceResponse updateRace(UUID currentUserId, UUID id, RaceRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Race race = loadRace(id);

        if (race.getStatus() == RaceStatus.CANCELLED
                || race.getStatus() == RaceStatus.FINISHED
                || race.getStatus() == RaceStatus.OFFICIAL) {
            throw new AppException(ErrorCode.RACE_INVALID_STATUS);
        }

        // Partial update: apply only non-null fields. Tournament and code are immutable.
        if (request.name() != null) race.setName(request.name());
        if (request.raceType() != null) race.setRaceType(request.raceType());
        if (request.distanceMeter() != null) race.setDistanceMeter(request.distanceMeter());
        if (request.trackCondition() != null) race.setTrackCondition(request.trackCondition());
        if (request.weatherCondition() != null) race.setWeatherCondition(request.weatherCondition());
        if (request.scheduledStartAt() != null) race.setScheduledStartAt(request.scheduledStartAt());
        if (request.predictionCutoffAt() != null) race.setPredictionCutoffAt(request.predictionCutoffAt());
        if (request.maxParticipants() != null) race.setMaxParticipants(request.maxParticipants());
        if (request.status() != null) race.setStatus(request.status());

        return mapToResponse(raceRepository.save(race));
    }

    @Override
    @Transactional
    public void deleteRace(UUID currentUserId, UUID id) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Race race = loadRace(id);
        race.setDeleted(true);
        race.setDeletedAt(OffsetDateTime.now());
        raceRepository.save(race);
    }

    @Override
    @Transactional
    public RaceResponse scheduleRace(UUID currentUserId, UUID id, ScheduleRaceRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Race race = loadRace(id);

        if (race.getStatus() != RaceStatus.SCHEDULED) {
            throw new AppException(ErrorCode.RACE_INVALID_STATUS);
        }

        race.setScheduledStartAt(request.scheduledStartAt());
        race.setPredictionCutoffAt(request.predictionCutoffAt());
        race.setStatus(RaceStatus.OPEN);

        return mapToResponse(raceRepository.save(race));
    }

    @Override
    @Transactional
    public RaceResponse cancelRace(UUID currentUserId, UUID id) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Race race = loadRace(id);

        if (race.getStatus() == RaceStatus.FINISHED
                || race.getStatus() == RaceStatus.OFFICIAL
                || race.getStatus() == RaceStatus.CANCELLED) {
            throw new AppException(ErrorCode.RACE_INVALID_STATUS);
        }

        race.setStatus(RaceStatus.CANCELLED);

        return mapToResponse(raceRepository.save(race));
    }

    @Override
    @Transactional
    public RaceEntryResponse assignParticipant(UUID currentUserId, UUID raceId, AssignParticipantRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Race race = loadRace(raceId);

        if (race.getStatus() != RaceStatus.SCHEDULED && race.getStatus() != RaceStatus.OPEN) {
            throw new AppException(ErrorCode.RACE_NOT_OPEN_FOR_ENTRY);
        }

        TournamentRegistration registration = registrationRepository.findById(request.registrationId())
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));
        if (registration.getStatus() != RegistrationStatus.APPROVED) {
            throw new AppException(ErrorCode.REGISTRATION_NOT_APPROVED);
        }

        UUID regTournamentId = registration.getTournament() != null
                ? registration.getTournament().getTournamentId() : null;
        UUID raceTournamentId = race.getTournament() != null
                ? race.getTournament().getTournamentId() : null;
        if (regTournamentId == null || !regTournamentId.equals(raceTournamentId)) {
            throw new AppException(ErrorCode.RACE_TOURNAMENT_MISMATCH);
        }

        if (race.getMaxParticipants() != null
                && raceEntryRepository.countByRace_RaceId(raceId) >= race.getMaxParticipants()) {
            throw new AppException(ErrorCode.RACE_FULL);
        }

        RaceEntry entry = RaceEntry.builder()
                .registration(registration)
                .race(race)
                .entryCode(generateEntryCode())
                .entryNo(request.entryNo())
                .laneNo(request.laneNo())
                .status(RaceEntryStatus.ENTERED)
                .build();

        // DB UNIQUE (race_id, registration_id)/(race_id, lane_no)/(race_id, entry_no)
        // guards dupes -> DataIntegrityViolationException -> existing 409 handler.
        return mapToEntryResponse(raceEntryRepository.save(entry));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceEntryResponse> listEntries(UUID raceId) {
        loadRace(raceId);
        return raceEntryRepository.findByRace_RaceId(raceId).stream()
                .map(this::mapToEntryResponse)
                .toList();
    }

    // ── helpers ──

    private Race loadRace(UUID id) {
        return raceRepository.findByRaceIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));
    }

    /** Sequential code RACEnnnnn, skipping any already taken (the DB UNIQUE is the final guard). */
    private String generateRaceCode() {
        long n = raceRepository.count() + 1;
        String code;
        do {
            code = String.format("RACE%05d", n++);
        } while (raceRepository.existsByRaceCode(code));
        return code;
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

    private Pageable buildPageable(RaceFilterRequest f) {
        int page = (f.getPage() != null && f.getPage() >= 0) ? f.getPage() : 0;
        int size = (f.getSize() != null && f.getSize() > 0) ? Math.min(f.getSize(), MAX_PAGE_SIZE) : 10;
        String field = switch (f.getSortBy() != null ? f.getSortBy().trim().toLowerCase() : "createdat") {
            case "scheduledstartat" -> "scheduledStartAt";
            case "name" -> "name";
            default -> "createdAt";
        };
        Sort.Direction dir = "asc".equalsIgnoreCase(f.getSortDir()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(dir, field));
    }

    private RaceResponse mapToResponse(Race r) {
        Tournament t = r.getTournament();
        return RaceResponse.builder()
                .raceId(r.getRaceId())
                .raceCode(r.getRaceCode())
                .name(r.getName())
                .raceType(r.getRaceType())
                .distanceMeter(r.getDistanceMeter())
                .trackCondition(r.getTrackCondition())
                .weatherCondition(r.getWeatherCondition())
                .scheduledStartAt(r.getScheduledStartAt())
                .actualStartAt(r.getActualStartAt())
                .actualEndAt(r.getActualEndAt())
                .predictionCutoffAt(r.getPredictionCutoffAt())
                .maxParticipants(r.getMaxParticipants())
                .status(r.getStatus())
                .tournamentId(t != null ? t.getTournamentId() : null)
                .tournamentName(t != null ? t.getName() : null)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private RaceEntryResponse mapToEntryResponse(RaceEntry e) {
        TournamentRegistration reg = e.getRegistration();
        Horse horse = reg != null ? reg.getHorse() : null;
        User owner = reg != null ? reg.getOwner() : null;
        return RaceEntryResponse.builder()
                .entryId(e.getEntryId())
                .entryCode(e.getEntryCode())
                .entryNo(e.getEntryNo())
                .laneNo(e.getLaneNo())
                .status(e.getStatus())
                .raceId(e.getRace() != null ? e.getRace().getRaceId() : null)
                .registrationId(reg != null ? reg.getRegistrationId() : null)
                .horseId(horse != null ? horse.getHorseId() : null)
                .horseName(horse != null ? horse.getName() : null)
                .ownerUserId(owner != null ? owner.getUserId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .createdAt(e.getCreatedAt())
                .build();
    }
}

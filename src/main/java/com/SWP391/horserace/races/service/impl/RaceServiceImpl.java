package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.staffing.repository.RefereeAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.races.dto.AssignParticipantRequest;
import com.SWP391.horserace.races.dto.MyEntryResponse;
import com.SWP391.horserace.races.dto.PrizeDistributionDto;
import com.SWP391.horserace.races.dto.RaceEntryResponse;
import com.SWP391.horserace.races.dto.RaceFilterRequest;
import com.SWP391.horserace.races.dto.RaceRequest;
import com.SWP391.horserace.races.dto.RaceResponse;
import com.SWP391.horserace.races.dto.RaceStatsResponse;
import com.SWP391.horserace.races.dto.ScheduleRaceRequest;
import com.SWP391.horserace.races.entity.PrizeDistributionItem;
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
import com.SWP391.horserace.venues.entity.Venue;
import com.SWP391.horserace.venues.repository.VenueRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RaceServiceImpl implements RaceService {

    private static final int MAX_PAGE_SIZE = 100;

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final RegistrationRepository registrationRepository;
    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final JockeyAssignmentRepository jockeyAssignmentRepository;
    private final VenueRepository venueRepository;
    private final RefereeAssignmentRepository refereeAssignmentRepository;
    private final com.SWP391.horserace.notifications.service.NotificationService notificationService;

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
            if (filter.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStartAt"), filter.getDateFrom()));
            }
            if (filter.getDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledStartAt"), filter.getDateTo()));
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

        if (request.tournamentId() == null) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
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
                .minParticipants(request.minParticipants())
                .venue(request.venue())
                .venueRef(resolveVenue(request.venueId()))
                // Status is always SCHEDULED on create; lifecycle changes go through
                // schedule()/cancel(), never a client-supplied status.
                .status(RaceStatus.SCHEDULED)
                .build();

        return mapToResponse(raceRepository.save(race));
    }

    @Override
    @Transactional(readOnly = true)
    public RaceStatsResponse getRaceStats(UUID tournamentId) {
        long scheduled = 0, active = 0, cancelled = 0, total = 0;
        for (Object[] row : raceRepository.countGroupByStatus(tournamentId)) {
            RaceStatus status = (RaceStatus) row[0];
            long count = ((Number) row[1]).longValue();
            total += count;
            switch (status) {
                case SCHEDULED -> scheduled += count;
                case OPEN -> active += count;       // "active" = the open/running state
                case CANCELLED -> cancelled += count;
                default -> { /* CLOSED/RUNNING/FINISHED/OFFICIAL counted only in total */ }
            }
        }
        return RaceStatsResponse.builder()
                .total(total)
                .scheduled(scheduled)
                .active(active)
                .cancelled(cancelled)
                .build();
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
        if (request.minParticipants() != null) race.setMinParticipants(request.minParticipants());
        if (request.venue() != null) race.setVenue(request.venue());
        if (request.venueId() != null) race.setVenueRef(resolveVenue(request.venueId()));
        // Status is intentionally NOT updatable here — transitions go through schedule()/cancel().

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
        // predictionCutoffAt is optional — only overwrite when supplied so an omitted
        // value doesn't wipe a cutoff already set at create time.
        if (request.predictionCutoffAt() != null) {
            race.setPredictionCutoffAt(request.predictionCutoffAt());
        }
        // Guard: cutoff must precede start, otherwise the race would be un-startable
        // (the ready-to-run gate needs now > cutoff AND now <= start).
        if (race.getPredictionCutoffAt() != null && race.getScheduledStartAt() != null
                && !race.getPredictionCutoffAt().isBefore(race.getScheduledStartAt())) {
            throw new AppException(ErrorCode.RACE_INVALID_TIMING);
        }
        race.setStatus(RaceStatus.OPEN);

        return mapToResponse(raceRepository.save(race));
    }

    @Override
    @Transactional
    public RaceResponse startRace(UUID currentUserId, UUID id) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Race race = loadRace(id);

        // Conduct the race: only from OPEN (or CLOSED entries). Locks further entries.
        if (race.getStatus() != RaceStatus.OPEN && race.getStatus() != RaceStatus.CLOSED) {
            throw new AppException(ErrorCode.RACE_INVALID_STATUS);
        }

        // "Ready to run" gate (FR-03): collect ALL unmet conditions so the admin sees every gap.
        int min = effectiveMinParticipants(race);
        long entries = raceEntryRepository.countByRace_RaceId(id);
        long acceptedJockeys = jockeyAssignmentRepository.countAcceptedByRaceId(id);
        boolean hasReferee = !refereeAssignmentRepository
                .findByRace_RaceIdAndStatusNot(id, RefereeAssignmentStatus.REVOKED)
                .isEmpty();
        OffsetDateTime now = OffsetDateTime.now();

        List<String> unmet = new ArrayList<>();
        if (entries < min) {
            unmet.add("needs at least " + min + " participants (has " + entries + ")");
        }
        if (acceptedJockeys != entries) {
            unmet.add("every horse must have a confirmed jockey (" + acceptedJockeys + "/" + entries + ")");
        }
        if (!hasReferee) {
            unmet.add("at least one referee must be assigned");
        }
        // null-safe: a race with no registration deadline is treated as "registration not closed"
        if (race.getPredictionCutoffAt() == null || !now.isAfter(race.getPredictionCutoffAt())) {
            unmet.add("registration is not closed yet");
        }
        if (race.getScheduledStartAt() != null && now.isAfter(race.getScheduledStartAt())) {
            unmet.add("scheduled start time has already passed");
        }
        if (!unmet.isEmpty()) {
            throw new AppException(ErrorCode.RACE_NOT_READY, "Race is not ready to run: " + String.join("; ", unmet));
        }

        race.setStatus(RaceStatus.RUNNING);
        if (race.getActualStartAt() == null) {
            race.setActualStartAt(OffsetDateTime.now());
        }

        return mapToResponse(raceRepository.save(race));
    }

    @Override
    @Transactional
    public RaceResponse finishRace(UUID currentUserId, UUID id) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Race race = loadRace(id);

        // End the race: only from RUNNING. Opens the referee reporting window.
        if (race.getStatus() != RaceStatus.RUNNING) {
            throw new AppException(ErrorCode.RACE_INVALID_STATUS);
        }

        race.setStatus(RaceStatus.FINISHED);
        race.setActualEndAt(OffsetDateTime.now());

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
        RaceResponse response = mapToResponse(raceRepository.save(race));
        notifyRaceCancelled(race); // best-effort notify referee/owner/jockey (FR-07)
        return response;
    }

    // =========================================================================
    // Auto-cancel (semi-automatic) — FR-05
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findRacesToProposeCancel() {
        return raceRepository.findOpenRacesPastCutoffWithoutCancelProposal(OffsetDateTime.now());
    }

    @Override
    @Transactional
    public void proposeCancel(UUID raceId) {
        Race race = raceRepository.findByRaceIdAndDeletedFalse(raceId).orElse(null);
        // Re-check under the transaction; idempotent (cancelProposedAt already set → skip).
        if (race == null || race.getStatus() != RaceStatus.OPEN || race.getCancelProposedAt() != null
                || race.getPredictionCutoffAt() == null
                || !OffsetDateTime.now().isAfter(race.getPredictionCutoffAt())) {
            return;
        }
        int min = effectiveMinParticipants(race);
        long confirmed = jockeyAssignmentRepository.countAcceptedByRaceId(raceId);
        if (confirmed >= min) {
            return; // enough runners — no proposal
        }
        // Atomic, conditional flag: writes ONLY cancel_proposed_at and only while the race is still
        // OPEN + unproposed, so a concurrent cancelRace is never reverted by a stale full-row update.
        int flagged = raceRepository.markCancelProposed(raceId, OffsetDateTime.now());
        if (flagged == 0) {
            return; // lost the race to a concurrent writer (already cancelled or already proposed)
        }
        String title = "Race under-filled — cancel proposed";
        String msg = "Race \"" + race.getName() + "\" is past its registration cutoff with only "
                + confirmed + "/" + min + " confirmed runners. Please review and confirm cancellation.";
        for (User admin : userRepository.findByRole_RoleCodeAndDeletedFalse("ADMIN")) {
            notificationService.notifyUser(admin.getUserId(), title, msg); // REQUIRES_NEW + best-effort
        }
    }

    /** Minimum runners required to run a race — the configured floor, never below 1. */
    private int effectiveMinParticipants(Race race) {
        return race.getMinParticipants() != null ? Math.max(race.getMinParticipants(), 1) : 1;
    }

    /** Best-effort notification of referee(s), owner(s), and jockey(s) when a race is cancelled. */
    private void notifyRaceCancelled(Race race) {
        String title = "Race cancelled";
        String msg = "Race \"" + race.getName() + "\" has been cancelled.";
        java.util.Set<UUID> recipients = new java.util.HashSet<>();
        refereeAssignmentRepository.findByRace_RaceIdAndStatusNot(race.getRaceId(), RefereeAssignmentStatus.REVOKED)
                .forEach(ra -> { if (ra.getReferee() != null) recipients.add(ra.getReferee().getUserId()); });
        recipients.addAll(jockeyAssignmentRepository.findJockeyIdsAcceptedInRace(race.getRaceId()));
        raceEntryRepository.findByRace_RaceId(race.getRaceId()).forEach(e -> {
            if (e.getRegistration() != null && e.getRegistration().getOwner() != null) {
                recipients.add(e.getRegistration().getOwner().getUserId());
            }
        });
        recipients.stream().filter(java.util.Objects::nonNull)
                .forEach(uid -> notificationService.notifyUser(uid, title, msg));
    }

    @Override
    @Transactional
    public RaceEntryResponse assignParticipant(UUID currentUserId, UUID raceId, AssignParticipantRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Race race = loadRace(raceId);

        // Only OPEN races accept participant entries.
        if (race.getStatus() != RaceStatus.OPEN) {
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
        List<RaceEntry> entries = raceEntryRepository.findByRace_RaceId(raceId);

        // Batch-fetch the ACCEPTED jockey for every entry in one query (avoids N+1).
        List<UUID> entryIds = entries.stream().map(RaceEntry::getEntryId).toList();
        Map<UUID, User> jockeyByEntryId = entryIds.isEmpty()
                ? Map.of()
                : jockeyAssignmentRepository.findAcceptedByEntryIds(entryIds).stream()
                        .filter(ja -> ja.getEntry() != null && ja.getJockey() != null)
                        .collect(Collectors.toMap(
                                ja -> ja.getEntry().getEntryId(),
                                JockeyAssignment::getJockey,
                                (a, b) -> a));

        return entries.stream()
                .map(e -> mapToEntryResponse(e, jockeyByEntryId.get(e.getEntryId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MyEntryResponse getMyEntry(UUID raceId, UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        loadRace(raceId);

        RaceEntry entry = raceEntryRepository.findByRaceIdAndOwnerUserId(raceId, ownerUserId)
                .orElseThrow(() -> new AppException(ErrorCode.ENTRY_NOT_FOUND));

        Horse horse = entry.getRegistration() != null ? entry.getRegistration().getHorse() : null;
        String jockeyName = jockeyAssignmentRepository.findAcceptedByEntryId(entry.getEntryId())
                .map(JockeyAssignment::getJockey)
                .map(u -> u.getFullName())
                .orElse(null);

        return MyEntryResponse.builder()
                .horseName(horse != null ? horse.getName() : null)
                .drawStall(toDrawStall(entry.getLaneNo()))
                .jockeyName(jockeyName)
                .weightCarriedLbs(entry.getWeightCarriedLbs())
                .entryStatus(entry.getStatus())
                .build();
    }

    // ── helpers ──

    private Race loadRace(UUID id) {
        return raceRepository.findByRaceIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));
    }

    /** Resolve a venue FK; null id = no venue. Throws VENUE_NOT_FOUND if the id is unknown. */
    private Venue resolveVenue(UUID venueId) {
        if (venueId == null) {
            return null;
        }
        return venueRepository.findById(venueId)
                .orElseThrow(() -> new AppException(ErrorCode.VENUE_NOT_FOUND));
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
            case "racecode" -> "raceCode";
            case "status" -> "status";
            case "distancemeter" -> "distanceMeter";
            default -> "createdAt";
        };
        Sort.Direction dir = "asc".equalsIgnoreCase(f.getSortDir()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(dir, field));
    }

    private RaceResponse mapToResponse(Race r) {
        Tournament t = r.getTournament();
        Venue v = r.getVenueRef();
        // §D1 — surface the linked venue name (fall back to the free-text venue when no FK).
        String venueName = v != null ? v.getName() : r.getVenue();
        long entriesCount = r.getRaceId() == null ? 0L
                : raceEntryRepository.countByRace_RaceId(r.getRaceId());
        long confirmedCount = r.getRaceId() == null ? 0L
                : jockeyAssignmentRepository.countAcceptedByRaceId(r.getRaceId());
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
                .cancelProposedAt(r.getCancelProposedAt())
                .maxParticipants(r.getMaxParticipants())
                .minParticipants(r.getMinParticipants())
                .venue(r.getVenue())
                .venueId(v != null ? v.getVenueId() : null)
                .venueName(venueName)
                .entriesCount(entriesCount)
                .confirmedCount(confirmedCount)
                .goingMoisturePct(r.getGoingMoisturePct())
                .totalPurse(r.getTotalPurse())
                .prizeDistribution(mapPrizeDistribution(r.getPrizeDistribution()))
                .status(r.getStatus())
                .tournamentId(t != null ? t.getTournamentId() : null)
                .tournamentName(t != null ? t.getName() : null)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private List<PrizeDistributionDto> mapPrizeDistribution(List<PrizeDistributionItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(i -> new PrizeDistributionDto(i.getPlace(), i.getAmount()))
                .toList();
    }

    /** Map an entry whose riding jockey has not been resolved (e.g. just after creation). */
    private RaceEntryResponse mapToEntryResponse(RaceEntry e) {
        return mapToEntryResponse(e, (User) null);
    }

    private RaceEntryResponse mapToEntryResponse(RaceEntry e, User jockey) {
        TournamentRegistration reg = e.getRegistration();
        Horse horse = reg != null ? reg.getHorse() : null;
        User owner = reg != null ? reg.getOwner() : null;
        return RaceEntryResponse.builder()
                .entryId(e.getEntryId())
                .entryCode(e.getEntryCode())
                .entryNo(e.getEntryNo())
                .laneNo(e.getLaneNo())
                .drawStall(toDrawStall(e.getLaneNo()))
                .status(e.getStatus())
                .raceId(e.getRace() != null ? e.getRace().getRaceId() : null)
                .registrationId(reg != null ? reg.getRegistrationId() : null)
                .horseId(horse != null ? horse.getHorseId() : null)
                .horseName(horse != null ? horse.getName() : null)
                .ownerUserId(owner != null ? owner.getUserId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .jockeyUserId(jockey != null ? jockey.getUserId() : null)
                .jockeyName(jockey != null ? jockey.getFullName() : null)
                .weightCarriedLbs(e.getWeightCarriedLbs())
                .recentForm(e.getRecentForm())
                .odds(e.getOdds())
                .createdAt(e.getCreatedAt())
                .build();
    }

    /** Draw stall is the rendered lane number; null lane -> null stall. */
    private String toDrawStall(Integer laneNo) {
        return laneNo != null ? String.valueOf(laneNo) : null;
    }
}

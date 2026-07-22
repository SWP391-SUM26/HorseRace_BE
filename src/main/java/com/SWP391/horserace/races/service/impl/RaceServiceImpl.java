package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.inspections.entity.DocumentReviewStatus;
import com.SWP391.horserace.inspections.entity.EntryDocumentReview;
import com.SWP391.horserace.inspections.entity.InspectionStatus;
import com.SWP391.horserace.inspections.entity.RaceEntryInspection;
import com.SWP391.horserace.inspections.repository.EntryDocumentReviewRepository;
import com.SWP391.horserace.inspections.repository.RaceEntryInspectionRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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
    private final com.SWP391.horserace.races.service.RaceEntryGate raceEntryGate;
    private final com.SWP391.horserace.notifications.service.NotificationService notificationService;
    private final EntryDocumentReviewRepository entryDocumentReviewRepository;
    private final RaceEntryInspectionRepository raceEntryInspectionRepository;

    /** Auto-close lead: how far before the prediction cutoff (fallback: start) an OPEN race auto-closes. */
    @Value("${app.race.auto-close-lead-ms:1800000}")
    private long autoCloseLeadMs;

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

        validateDates(request.scheduledStartAt(), request.predictionCutoffAt());

        // FR-12: min must not exceed max when both are supplied.
        if (request.minParticipants() != null && request.maxParticipants() != null
                && request.minParticipants() > request.maxParticipants()) {
            throw new AppException(ErrorCode.RACE_INVALID_PARTICIPANT_RANGE);
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
                .totalPurse(request.totalPurse())
                .entryFee(request.entryFee())
                .prizeDistribution(toPrizeItems(request.prizeDistribution()))
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

        // #2 date validation (partial update): range check on the EFFECTIVE values; the past-check
        // only applies to a newly-supplied start (don't reject an unrelated edit of an existing race).
        OffsetDateTime effStart = request.scheduledStartAt() != null ? request.scheduledStartAt() : race.getScheduledStartAt();
        OffsetDateTime effCutoff = request.predictionCutoffAt() != null ? request.predictionCutoffAt() : race.getPredictionCutoffAt();
        if (effStart != null && effCutoff != null && appDate(effCutoff).isAfter(appDate(effStart))) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }
        if (request.scheduledStartAt() != null
                && appDate(request.scheduledStartAt()).isBefore(java.time.LocalDate.now(APP_ZONE))) {
            throw new AppException(ErrorCode.DATE_IN_PAST);
        }

        // FR-12: min must not exceed max on the EFFECTIVE (new-or-existing) values.
        Integer effMin = request.minParticipants() != null ? request.minParticipants() : race.getMinParticipants();
        Integer effMax = request.maxParticipants() != null ? request.maxParticipants() : race.getMaxParticipants();
        if (effMin != null && effMax != null && effMin > effMax) {
            throw new AppException(ErrorCode.RACE_INVALID_PARTICIPANT_RANGE);
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
        if (request.totalPurse() != null) race.setTotalPurse(request.totalPurse());
        if (request.entryFee() != null) race.setEntryFee(request.entryFee());
        if (request.prizeDistribution() != null) {
            // Replace the @ElementCollection in place (clear+addAll) so Hibernate tracks it cleanly.
            List<PrizeDistributionItem> items = toPrizeItems(request.prizeDistribution());
            if (race.getPrizeDistribution() == null) {
                race.setPrizeDistribution(new java.util.ArrayList<>(items));
            } else {
                race.getPrizeDistribution().clear();
                race.getPrizeDistribution().addAll(items);
            }
        }
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
        // FR-04: reject a past start (mirrors create/update). Day-granularity in APP_ZONE so a
        // same-day (today) start sent as T00:00:00Z is accepted, not rejected.
        if (race.getScheduledStartAt() != null
                && appDate(race.getScheduledStartAt()).isBefore(java.time.LocalDate.now(APP_ZONE))) {
            throw new AppException(ErrorCode.DATE_IN_PAST);
        }
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
    public RaceResponse closeRace(UUID currentUserId, UUID id) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Race race = loadRace(id);

        // Lock the lineup: OPEN → CLOSED. CLOSED is the ONLY state betting is accepted in (FR-01/FR-03).
        if (race.getStatus() != RaceStatus.OPEN) {
            throw new AppException(ErrorCode.RACE_INVALID_STATUS);
        }

        race.setStatus(RaceStatus.CLOSED);
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
        // FR-02 (revised): kept lenient OPEN||CLOSED — the now>cutoff gate already ensures betting
        // closed; requiring CLOSED would strand un-closed races.
        if (race.getStatus() != RaceStatus.OPEN && race.getStatus() != RaceStatus.CLOSED) {
            throw new AppException(ErrorCode.RACE_INVALID_STATUS);
        }

        // Admin override: the admin's decision is final. Starting a race is a deliberate admin action,
        // so it is NOT gated on the old "ready to run" checks (min participants, confirmed jockeys/referee,
        // documents accepted, vet-cleared, registration closed, scheduled time). Admin clicks Start → it runs.
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

    // =========================================================================
    // Auto-close sweep — FR-07
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findRacesToAutoClose() {
        // CUTOFF-relative trigger (fallback to start when cutoff is null): a race is due once its cutoff
        // is within `lead` of now, guaranteeing a non-empty [cutoff − lead, cutoff) betting window.
        return raceRepository.findOpenRacesToAutoClose(
                OffsetDateTime.now().plus(autoCloseLeadMs, ChronoUnit.MILLIS));
    }

    @Override
    @Transactional
    public void autoClose(UUID raceId) {
        // Atomic, conditional OPEN → CLOSED. rows == 0 → already CLOSED/CANCELLED/RUNNING/deleted or lost
        // to a concurrent writer → silent no-op (idempotent), never a throw.
        raceRepository.markClosed(raceId);
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

        // Eligibility (health + age) + entry-fee debit before the entry is created.
        raceEntryGate.admit(registration, race);

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

        // An owner may run several horses in one race, so this lookup returns a list. It used to be
        // an Optional, which threw NonUniqueResultException as soon as that happened. The card shows
        // the lowest lane; listEntries() is the endpoint for the full picture.
        RaceEntry entry = raceEntryRepository.findAllByRaceIdAndOwnerUserId(raceId, ownerUserId)
                .stream()
                .min(java.util.Comparator.comparing(RaceEntry::getLaneNo,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .orElseThrow(() -> new AppException(ErrorCode.ENTRY_NOT_FOUND));

        return toMyEntryResponse(entry);
    }

    /** Shared by getMyEntry and confirmParticipation so both report the same shape. */
    private MyEntryResponse toMyEntryResponse(RaceEntry entry) {
        Horse horse = entry.getRegistration() != null ? entry.getRegistration().getHorse() : null;
        String jockeyName = jockeyAssignmentRepository.findAcceptedByEntryId(entry.getEntryId())
                .map(JockeyAssignment::getJockey)
                .map(User::getFullName)
                .orElse(null);

        return MyEntryResponse.builder()
                .horseName(horse != null ? horse.getName() : null)
                .drawStall(toDrawStall(entry.getLaneNo()))
                .jockeyName(jockeyName)
                .weightCarriedLbs(entry.getWeightCarriedLbs())
                .entryStatus(entry.getStatus())
                .build();
    }

    @Override
    @Transactional
    public MyEntryResponse confirmParticipation(UUID raceId, UUID entryId, UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Race race = loadRace(raceId);

        // Keyed on a specific entry because an owner can run several horses in the same race —
        // "confirm my entry" would be ambiguous. The owner id in the query is the IDOR guard: an
        // entry belonging to someone else simply does not resolve.
        RaceEntry entry = raceEntryRepository
                .findByEntryIdAndRaceIdAndOwnerUserId(entryId, raceId, ownerUserId)
                .orElseThrow(() -> new AppException(ErrorCode.ENTRY_NOT_FOUND));

        // Confirming is only meaningful while the lineup can still change. Once the race is running
        // or over, the entry status is a record of what happened and must not be rewritten.
        if (race.getStatus() != RaceStatus.OPEN && race.getStatus() != RaceStatus.CLOSED
                && race.getStatus() != RaceStatus.SCHEDULED) {
            throw new AppException(ErrorCode.RACE_INVALID_STATUS);
        }
        if (entry.getStatus() == RaceEntryStatus.SCRATCHED
                || entry.getStatus() == RaceEntryStatus.DISQUALIFIED) {
            throw new AppException(ErrorCode.ENTRY_INVALID_STATUS);
        }

        // Idempotent: already confirmed → return the current state rather than erroring.
        if (entry.getStatus() != RaceEntryStatus.CHECKED_IN) {
            entry.setStatus(RaceEntryStatus.CHECKED_IN);
            entry.setCheckedInAt(OffsetDateTime.now());
            raceEntryRepository.save(entry);
        }

        return toMyEntryResponse(entry);
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

    /**
     * Validate race dates on create (#2). NULL-safe (dates optional — a race can be scheduled later via
     * scheduleRace). Day granularity in UTC so a same-day start the FE sends as {@code T00:00:00Z} passes.
     */
    private void validateDates(OffsetDateTime scheduledStartAt, OffsetDateTime predictionCutoffAt) {
        java.time.LocalDate today = java.time.LocalDate.now(APP_ZONE);
        if (scheduledStartAt != null && predictionCutoffAt != null
                && appDate(predictionCutoffAt).isAfter(appDate(scheduledStartAt))) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE); // betting cutoff can't be after the start
        }
        if (scheduledStartAt != null && appDate(scheduledStartAt).isBefore(today)) {
            throw new AppException(ErrorCode.DATE_IN_PAST);
        }
        if (predictionCutoffAt != null && appDate(predictionCutoffAt).isBefore(today)) {
            throw new AppException(ErrorCode.DATE_IN_PAST);
        }
    }

    private static final java.time.ZoneId APP_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    private static java.time.LocalDate appDate(OffsetDateTime odt) {
        return odt.atZoneSameInstant(APP_ZONE).toLocalDate();
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
                .entryFee(r.getEntryFee())
                .prizeDistribution(mapPrizeDistribution(r.getPrizeDistribution()))
                .status(r.getStatus())
                .tournamentId(t != null ? t.getTournamentId() : null)
                .tournamentName(t != null ? t.getName() : null)
                .tournamentImageUrl(t != null ? t.getImageUrl() : null)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    /** Convert request DTOs → embeddable prize-distribution items (skips blank/incomplete rows). */
    private List<PrizeDistributionItem> toPrizeItems(List<PrizeDistributionDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .filter(d -> d != null && d.place() != null && !d.place().isBlank() && d.amount() != null)
                .map(d -> PrizeDistributionItem.builder().place(d.place().trim()).amount(d.amount()).build())
                .toList();
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

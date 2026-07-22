package com.SWP391.horserace.tournaments.service.impl;

import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.storage.ImageUploadService;
import com.SWP391.horserace.tournaments.dto.EligibilityDto;
import com.SWP391.horserace.tournaments.dto.TournamentFilterRequest;
import com.SWP391.horserace.tournaments.dto.TournamentRequest;
import com.SWP391.horserace.tournaments.dto.TournamentResponse;
import com.SWP391.horserace.tournaments.entity.EligibilityCriteria;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.tournaments.entity.TournamentStatus;
import com.SWP391.horserace.tournaments.entity.TournamentVenue;
import com.SWP391.horserace.tournaments.repository.TournamentRepository;
import com.SWP391.horserace.tournaments.service.TournamentService;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.venues.dto.VenueResponse;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {

    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final RegistrationRepository registrationRepository;
    private final ImageUploadService imageUploadService;
    /** Only for the purse ceiling: a tournament's total must cover what its races were allocated. */
    private final com.SWP391.horserace.races.repository.RaceRepository raceRepository;
    /** Sponsor funding: the organiser's purse is credited to the house, then paid out as prizes. */
    private final com.SWP391.horserace.wallets.service.HouseWalletService houseWalletService;
    private final com.SWP391.horserace.wallets.service.WalletLedgerService walletLedgerService;
    private final com.SWP391.horserace.prizes.repository.PrizeRepository prizeRepository;

    @Override
    @Transactional
    public TournamentResponse createTournament(TournamentRequest request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        validateDates(request);

        Tournament tournament = Tournament.builder()
                .tournamentCode(generateTournamentCode())
                .name(request.getName())
                .description(request.getDescription())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .registrationOpenAt(request.getRegistrationOpenAt())
                .registrationCloseAt(request.getRegistrationCloseAt())
                .location(request.getLocation())
                .circuitTier(request.getCircuitTier())
                .totalPurse(request.getTotalPurse())
                .entryCap(request.getEntryCap())
                .eligibility(toEligibilityEntity(request.getEligibility()))
                // FR-03: a tournament is ALWAYS created in DRAFT — any client-supplied status is ignored.
                // The publish→open→…→complete state machine is the only legal path onward.
                .status(TournamentStatus.DRAFT)
                .createdBy(user)
                .build();

        applyVenues(tournament, request.getVenueIds());

        tournament = tournamentRepository.save(tournament);
        return mapToResponse(tournament);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TournamentResponse> getTournaments(TournamentFilterRequest filter,
                                                   UUID viewerUserId, boolean isAdmin) {
        Sort sort = buildSort(filter.getSortBy(), filter.getSortDir());
        Pageable pageable = PageRequest.of(
                filter.getPage() != null ? filter.getPage() : 0,
                filter.getSize() != null ? filter.getSize() : 10,
                sort);

        // Tournaments the viewer took part in stay visible to them after they end. Loaded once,
        // outside the Specification, so it is a single query rather than a correlated subquery.
        List<UUID> involvedIds = (isAdmin || viewerUserId == null)
                ? List.of()
                : tournamentRepository.findTournamentIdsInvolvingUser(viewerUserId);

        Specification<Tournament> spec = (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Only non-deleted tournaments
            predicates.add(criteriaBuilder.isFalse(root.get("deleted")));

            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), 
                        "%" + filter.getName().toLowerCase() + "%"));
            }
            if (filter.getTournamentCode() != null && !filter.getTournamentCode().isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("tournamentCode")), 
                        "%" + filter.getTournamentCode().toLowerCase() + "%"));
            }
            if (filter.getLocation() != null && !filter.getLocation().isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("location")), 
                        "%" + filter.getLocation().toLowerCase() + "%"));
            }
            if (filter.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.getStatus()));
            }

            if (!isAdmin) {
                // Drafts are the admin's working copy — they were being shipped to every browser and
                // only hidden by a client-side filter.
                predicates.add(criteriaBuilder.notEqual(root.get("status"), TournamentStatus.DRAFT));

                // "Finished" is decided by the DATE, not the status. Today the two happen to agree,
                // but no scheduler advances a tournament to COMPLETED, so the moment one drifts past
                // its end date a status-based rule would keep showing it to everyone.
                // Start-of-today in APP_ZONE, so a tournament ending today stays visible all day.
                OffsetDateTime startOfToday = java.time.LocalDate.now(APP_ZONE)
                        .atStartOfDay(APP_ZONE).toOffsetDateTime();
                Predicate notEnded = criteriaBuilder.or(
                        criteriaBuilder.isNull(root.get("endDate")),
                        criteriaBuilder.greaterThanOrEqualTo(root.get("endDate"), startOfToday));
                // An empty IN list is invalid SQL on some providers — keep the guard.
                predicates.add(involvedIds.isEmpty()
                        ? notEnded
                        : criteriaBuilder.or(notEnded, root.get("tournamentId").in(involvedIds)));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<Tournament> page = tournamentRepository.findAll(spec, pageable);
        return page.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TournamentResponse getTournamentById(UUID id) {
        Tournament tournament = tournamentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }
        return mapToResponse(tournament);
    }

    @Override
    @Transactional
    public TournamentResponse updateTournament(UUID id, TournamentRequest request) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
        
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }

        validateDatesForUpdate(tournament, request);

        // A tournament's purse is the ceiling for its races. Lowering it below what the races have
        // already been allocated would leave the schedule paying out more than the tournament holds.
        if (request.getTotalPurse() != null) {
            BigDecimal allocated = raceRepository.sumAllocatedPurse(id, null);
            if (request.getTotalPurse().compareTo(allocated) < 0) {
                throw new AppException(ErrorCode.TOURNAMENT_PURSE_BELOW_ALLOCATED,
                        "Đã phân bổ " + allocated.toPlainString() + " cho các cuộc đua");
            }
        }

        // Raising the purse AFTER publishing needs matching sponsor money, or the house is funded
        // for the old figure and the first certify past that point fails with INSUFFICIENT_BALANCE.
        // Only the delta is topped up; a DRAFT tournament is funded in full at publish instead.
        BigDecimal purseBefore = tournament.getTotalPurse() != null
                ? tournament.getTotalPurse() : BigDecimal.ZERO;

        // tournamentCode is immutable — auto-generated at creation, never changed on update.
        // Every field coalesces to its stored value: the FE omits blank inputs, so assigning the
        // raw request wiped dates, purse and tier whenever the admin left a field empty.
        tournament.setName(request.getName());
        tournament.setDescription(request.getDescription());
        if (request.getStartDate() != null) tournament.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) tournament.setEndDate(request.getEndDate());
        if (request.getRegistrationOpenAt() != null) tournament.setRegistrationOpenAt(request.getRegistrationOpenAt());
        if (request.getRegistrationCloseAt() != null) tournament.setRegistrationCloseAt(request.getRegistrationCloseAt());
        tournament.setLocation(request.getLocation());
        if (request.getCircuitTier() != null) tournament.setCircuitTier(request.getCircuitTier());
        if (request.getTotalPurse() != null) tournament.setTotalPurse(request.getTotalPurse());
        if (request.getEntryCap() != null) tournament.setEntryCap(request.getEntryCap());
        if (request.getEligibility() != null) {
            tournament.setEligibility(toEligibilityEntity(request.getEligibility()));
        }

        // Re-sync venue links only when the client supplies a venueIds list (null = leave untouched).
        if (request.getVenueIds() != null) {
            tournament.getVenues().clear();
            applyVenues(tournament, request.getVenueIds());
        }

        // A DRAFT tournament has not been funded yet — publish will credit the full (new) purse.
        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            BigDecimal purseAfter = tournament.getTotalPurse() != null
                    ? tournament.getTotalPurse() : BigDecimal.ZERO;
            fundPurse(tournament, purseAfter.subtract(purseBefore), "TOURNAMENT_PURSE_INCREASE");
        }

        tournament = tournamentRepository.save(tournament);
        return mapToResponse(tournament);
    }

    @Override
    @Transactional
    public void deleteTournament(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
        
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }

        tournament.setDeleted(true);
        tournament.setDeletedAt(OffsetDateTime.now());
        tournamentRepository.save(tournament);
    }

    @Override
    @Transactional
    public TournamentResponse publishTournament(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
                
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }

        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            throw new AppException(ErrorCode.TOURNAMENT_INVALID_STATUS, "Only DRAFT tournaments can be published");
        }

        // Publishing commits the purse: the organiser funds it into the house wallet, and each
        // certify later debits the house as prizes go out. Before this, PRIZE was the one money
        // category minted from nothing — the ledger could not say where the purse came from.
        //
        // Idempotency is structural, not defensive: this method is the ONLY transition out of DRAFT
        // and it is @Transactional, so a second call throws above before any money moves. No
        // ledger-existence probe is needed.
        fundPurse(tournament, tournament.getTotalPurse(), "TOURNAMENT_PUBLISH");

        tournament.setStatus(TournamentStatus.PUBLISHED);
        tournament = tournamentRepository.save(tournament);

        return mapToResponse(tournament);
    }

    /**
     * Credit the house wallet with sponsor money for a tournament's purse.
     *
     * <p>Amounts of zero or less are a no-op — a tournament may legitimately carry no prize money,
     * and {@code applyEntry} rejects a non-positive amount outright.
     */
    private void fundPurse(Tournament tournament, BigDecimal amount, String reason) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        UUID houseUserId = houseWalletService.houseUserId();
        walletLedgerService.applyEntry(houseUserId, EntryType.CREDIT, TxnCategory.SPONSOR,
                amount, reason, tournament.getTournamentId());
    }

    @Override
    @Transactional
    public TournamentResponse closeRegistration(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
                
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }

        if (tournament.getStatus() != TournamentStatus.REGISTRATION_OPEN
                && tournament.getStatus() != TournamentStatus.PUBLISHED) {
            throw new AppException(ErrorCode.TOURNAMENT_INVALID_STATUS, "Tournament is not open for registration");
        }

        tournament.setStatus(TournamentStatus.REGISTRATION_CLOSED);
        tournament = tournamentRepository.save(tournament);

        return mapToResponse(tournament);
    }

    // =========================================================================
    // §C5 — STATUS TRANSITIONS
    // Canonical set: DRAFT → PUBLISHED → REGISTRATION_OPEN → REGISTRATION_CLOSED
    //                → ONGOING → COMPLETED  (+ CANCELLED). Matches the schema CHECK.
    // =========================================================================

    @Override
    @Transactional
    public TournamentResponse openRegistration(UUID id) {
        Tournament tournament = loadActive(id);
        if (tournament.getStatus() != TournamentStatus.PUBLISHED) {
            throw new AppException(ErrorCode.TOURNAMENT_INVALID_STATUS,
                    "Only PUBLISHED tournaments can open registration");
        }
        tournament.setStatus(TournamentStatus.REGISTRATION_OPEN);
        return mapToResponse(tournamentRepository.save(tournament));
    }

    @Override
    @Transactional
    public TournamentResponse startTournament(UUID id) {
        Tournament tournament = loadActive(id);
        if (tournament.getStatus() != TournamentStatus.REGISTRATION_CLOSED) {
            throw new AppException(ErrorCode.TOURNAMENT_INVALID_STATUS,
                    "Only REGISTRATION_CLOSED tournaments can start");
        }
        tournament.setStatus(TournamentStatus.ONGOING);
        return mapToResponse(tournamentRepository.save(tournament));
    }

    @Override
    @Transactional
    public TournamentResponse completeTournament(UUID id) {
        Tournament tournament = loadActive(id);

        TournamentStatus status = tournament.getStatus();
        if (status != TournamentStatus.ONGOING && status != TournamentStatus.COMPLETED) {
            throw new AppException(ErrorCode.TOURNAMENT_INVALID_STATUS,
                    "Only ONGOING tournaments can be completed");
        }

        // The gate runs UNCONDITIONALLY, before the status branch. Checking it only on the ONGOING
        // path would let a re-run on an already-COMPLETED tournament sweep prizes for races that
        // were never certified — precisely what the gate exists to stop.
        long unfinished = raceRepository.countNonTerminalByTournament(id);
        if (unfinished > 0) {
            throw new AppException(ErrorCode.TOURNAMENT_HAS_NON_TERMINAL_RACES,
                    "Còn " + unfinished + " cuộc đua chưa công nhận kết quả hoặc chưa huỷ");
        }

        // Close the books. This is a STATUS FLIP, not a payment: the money left the house and
        // reached the winners at certify. Paying here as well would pay every winner twice.
        int paid = prizeRepository.markPaidForTournament(id, OffsetDateTime.now());
        log.info("Tournament {} reconcile: {} prize(s) AWARDED -> PAID", id, paid);

        if (status == TournamentStatus.ONGOING) {
            tournament.setStatus(TournamentStatus.COMPLETED);
            tournamentRepository.save(tournament);
        }

        // markPaidForTournament clears the persistence context, so `tournament` is now detached and
        // mapToResponse would blow up reading its lazy venue collection. Re-load a managed instance.
        return mapToResponse(loadActive(id));
    }

    @Override
    @Transactional
    public TournamentResponse uploadImage(UUID id, org.springframework.web.multipart.MultipartFile file) {
        Tournament tournament = loadActive(id);
        String oldImageUrl = tournament.getImageUrl();
        // Validates (type/size) and stores via the active provider (Cloudinary CDN or local disk).
        tournament.setImageUrl(imageUploadService.storeImageAsUrl(file, "tournaments"));
        TournamentResponse response = mapToResponse(tournamentRepository.save(tournament));
        imageUploadService.deleteByUrl(oldImageUrl); // best-effort cleanup of the replaced image
        return response;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /** Sequential code TRNnnnnn, skipping any already taken (the DB UNIQUE is the final guard). */
    private String generateTournamentCode() {
        long n = tournamentRepository.count() + 1;
        String code;
        do {
            code = String.format("TRN%05d", n++);
        } while (tournamentRepository.existsByTournamentCode(code));
        return code;
    }

    /**
     * Validate tournament dates (#2). NULL-safe — dates are optional, so a comparison only runs when
     * the operand(s) are present. Compares at DAY granularity in the app's timezone so a same-day
     * (today) date the FE sends as {@code T00:00:00Z} is accepted, not rejected as past, and "today"
     * matches the user's local calendar day (not the server's UTC clock).
     */
    private void validateDates(TournamentRequest r) {
        java.time.LocalDate today = java.time.LocalDate.now(APP_ZONE);
        if (r.getEndDate() != null && r.getStartDate() != null
                && appDate(r.getEndDate()).isBefore(appDate(r.getStartDate()))) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }
        if (r.getRegistrationCloseAt() != null && r.getRegistrationOpenAt() != null
                && appDate(r.getRegistrationCloseAt()).isBefore(appDate(r.getRegistrationOpenAt()))) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }
        // No supplied date may be in the past (each checked independently).
        requireNotPast(r.getStartDate(), today);
        requireNotPast(r.getEndDate(), today);
        requireNotPast(r.getRegistrationOpenAt(), today);
        requireNotPast(r.getRegistrationCloseAt(), today);
        validateRegistrationWindow(r.getStartDate(), r.getEndDate(),
                r.getRegistrationOpenAt(), r.getRegistrationCloseAt());
    }

    /**
     * Registration must open and close no later than the tournament ends.
     *
     * <p>It deliberately does NOT require registration to open after the tournament starts. That was
     * the previous rule and it is backwards for racing — entries are taken weeks in advance. All ten
     * seeded tournaments open registration 30–60 days before their start date, so every one of them
     * violated it, and any edit to a tournament failed with INVALID_REGISTRATION_WINDOW even when
     * the admin only changed the name.
     */
    private void validateRegistrationWindow(OffsetDateTime startDate, OffsetDateTime endDate,
                                            OffsetDateTime registrationOpenAt,
                                            OffsetDateTime registrationCloseAt) {
        if (endDate != null && registrationOpenAt != null
                && appDate(registrationOpenAt).isAfter(appDate(endDate))) {
            throw new AppException(ErrorCode.INVALID_REGISTRATION_WINDOW);
        }
        if (endDate != null && registrationCloseAt != null
                && appDate(registrationCloseAt).isAfter(appDate(endDate))) {
            throw new AppException(ErrorCode.INVALID_REGISTRATION_WINDOW);
        }
    }

    /**
     * Date rules for a partial update: ranges are checked on the EFFECTIVE (new-or-stored) values,
     * but the not-in-the-past rule applies only to dates the admin actually changed.
     *
     * <p>Without that distinction a completed tournament could never be edited again — seven of the
     * ten seeded tournaments carry at least one past date, so even a rename was rejected.
     */
    private void validateDatesForUpdate(Tournament existing, TournamentRequest r) {
        java.time.LocalDate today = java.time.LocalDate.now(APP_ZONE);
        OffsetDateTime effStart = r.getStartDate() != null ? r.getStartDate() : existing.getStartDate();
        OffsetDateTime effEnd = r.getEndDate() != null ? r.getEndDate() : existing.getEndDate();
        OffsetDateTime effOpen = r.getRegistrationOpenAt() != null
                ? r.getRegistrationOpenAt() : existing.getRegistrationOpenAt();
        OffsetDateTime effClose = r.getRegistrationCloseAt() != null
                ? r.getRegistrationCloseAt() : existing.getRegistrationCloseAt();

        if (effStart != null && effEnd != null && appDate(effEnd).isBefore(appDate(effStart))) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }
        if (effOpen != null && effClose != null && appDate(effClose).isBefore(appDate(effOpen))) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }
        requireNotPastIfChanged(r.getStartDate(), existing.getStartDate(), today);
        requireNotPastIfChanged(r.getEndDate(), existing.getEndDate(), today);
        requireNotPastIfChanged(r.getRegistrationOpenAt(), existing.getRegistrationOpenAt(), today);
        requireNotPastIfChanged(r.getRegistrationCloseAt(), existing.getRegistrationCloseAt(), today);
        validateRegistrationWindow(effStart, effEnd, effOpen, effClose);
    }

    /** Day granularity: the FE round-trips dates as {@code YYYY-MM-DDT00:00:00Z}, so comparing
     *  instants would flag untouched fields as changed. */
    private void requireNotPastIfChanged(OffsetDateTime supplied, OffsetDateTime stored,
                                         java.time.LocalDate today) {
        if (supplied == null) {
            return;
        }
        boolean unchanged = stored != null && appDate(supplied).equals(appDate(stored));
        if (!unchanged && appDate(supplied).isBefore(today)) {
            throw new AppException(ErrorCode.DATE_IN_PAST);
        }
    }

    private void requireNotPast(OffsetDateTime date, java.time.LocalDate today) {
        if (date != null && appDate(date).isBefore(today)) {
            throw new AppException(ErrorCode.DATE_IN_PAST);
        }
    }

    private static final java.time.ZoneId APP_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    private static java.time.LocalDate appDate(OffsetDateTime odt) {
        return odt.atZoneSameInstant(APP_ZONE).toLocalDate();
    }

    private Tournament loadActive(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOURNAMENT_NOT_FOUND));
        if (tournament.isDeleted()) {
            throw new AppException(ErrorCode.TOURNAMENT_NOT_FOUND);
        }
        return tournament;
    }

    /** Link the given venue ids to the tournament (validates each exists). Null/empty = no change. */
    private void applyVenues(Tournament tournament, List<UUID> venueIds) {
        if (venueIds == null || venueIds.isEmpty()) {
            return;
        }
        for (UUID venueId : venueIds.stream().distinct().toList()) {
            Venue venue = venueRepository.findById(venueId)
                    .orElseThrow(() -> new AppException(ErrorCode.VENUE_NOT_FOUND));
            tournament.getVenues().add(TournamentVenue.builder()
                    .tournament(tournament)
                    .venue(venue)
                    .build());
        }
    }

    private EligibilityCriteria toEligibilityEntity(EligibilityDto dto) {
        if (dto == null) {
            return null;
        }
        return EligibilityCriteria.builder()
                .thoroughbredsOnly(dto.getThoroughbredsOnly())
                .minAgeYears(dto.getMinAgeYears())
                .requiresPreviousGroupWin(dto.getRequiresPreviousGroupWin())
                .build();
    }

    private EligibilityDto toEligibilityDto(EligibilityCriteria e) {
        if (e == null
                || (e.getThoroughbredsOnly() == null && e.getMinAgeYears() == null
                    && e.getRequiresPreviousGroupWin() == null)) {
            return null;
        }
        return EligibilityDto.builder()
                .thoroughbredsOnly(e.getThoroughbredsOnly())
                .minAgeYears(e.getMinAgeYears())
                .requiresPreviousGroupWin(e.getRequiresPreviousGroupWin())
                .build();
    }

    private List<VenueResponse> mapVenues(Tournament tournament) {
        if (tournament.getVenues() == null || tournament.getVenues().isEmpty()) {
            return List.of();
        }
        return tournament.getVenues().stream()
                .map(TournamentVenue::getVenue)
                .filter(v -> v != null)
                .map(v -> VenueResponse.builder()
                        .venueId(v.getVenueId())
                        .name(v.getName())
                        .trackName(v.getTrackName())
                        .city(v.getCity())
                        .country(v.getCountry())
                        .capacity(v.getCapacity())
                        .surface(v.getSurface())
                        .build())
                .toList();
    }

    private TournamentResponse mapToResponse(Tournament tournament) {
        long registeredCount = tournament.getTournamentId() == null ? 0L
                : registrationRepository.countByTournament_TournamentIdAndStatus(
                        tournament.getTournamentId(), RegistrationStatus.APPROVED);

        return TournamentResponse.builder()
                .tournamentId(tournament.getTournamentId())
                .tournamentCode(tournament.getTournamentCode())
                .name(tournament.getName())
                .description(tournament.getDescription())
                .startDate(tournament.getStartDate())
                .endDate(tournament.getEndDate())
                .registrationOpenAt(tournament.getRegistrationOpenAt())
                .registrationCloseAt(tournament.getRegistrationCloseAt())
                .location(tournament.getLocation())
                .circuitTier(tournament.getCircuitTier())
                .totalPurse(tournament.getTotalPurse())
                .entryCap(tournament.getEntryCap())
                .eligibility(toEligibilityDto(tournament.getEligibility()))
                .venues(mapVenues(tournament))
                .registeredEntriesCount(registeredCount)
                .status(tournament.getStatus())
                .imageUrl(tournament.getImageUrl())
                .createdAt(tournament.getCreatedAt())
                .updatedAt(tournament.getUpdatedAt())
                .build();
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = switch (sortBy != null ? sortBy : "createdAt") {
            case "name" -> "name";
            case "startDate" -> "startDate";
            case "endDate" -> "endDate";
            case "registrationOpenAt" -> "registrationOpenAt";
            default -> "createdAt";
        };

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return Sort.by(direction, field);
    }
}

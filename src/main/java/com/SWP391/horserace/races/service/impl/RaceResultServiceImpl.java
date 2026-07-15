package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.jockeys.entity.JockeyProfile;
import com.SWP391.horserace.jockeys.repository.JockeyProfileRepository;
import com.SWP391.horserace.prizes.entity.BeneficiaryType;
import com.SWP391.horserace.prizes.entity.Prize;
import com.SWP391.horserace.penalties.entity.Penalty;
import com.SWP391.horserace.penalties.entity.PenaltyStatus;
import com.SWP391.horserace.penalties.entity.PenaltyType;
import com.SWP391.horserace.penalties.repository.PenaltyRepository;
import com.SWP391.horserace.prizes.entity.PrizeStatus;
import com.SWP391.horserace.prizes.repository.PrizeRepository;
import com.SWP391.horserace.races.dto.CertifyResultsRequest;
import com.SWP391.horserace.races.dto.CertifyResultsResponse;
import com.SWP391.horserace.races.dto.RaceResultsResponse;
import com.SWP391.horserace.races.dto.RecordResultsRequest;
import com.SWP391.horserace.races.dto.ResultRowResponse;
import com.SWP391.horserace.races.dto.SubmitReportRequest;
import com.SWP391.horserace.races.dto.SubmitReportResponse;
import com.SWP391.horserace.races.dto.UpdateResultRequest;
import com.SWP391.horserace.races.dto.UpdateResultResponse;
import com.SWP391.horserace.races.entity.OfficialityStatus;
import com.SWP391.horserace.races.entity.PrizeDistributionItem;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceEntryStatus;
import com.SWP391.horserace.races.entity.RaceFraction;
import com.SWP391.horserace.races.entity.RaceResult;
import com.SWP391.horserace.races.entity.RaceResultVersion;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.races.repository.RaceResultVersionRepository;
import com.SWP391.horserace.races.service.RaceResultService;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.violations.dto.CreateViolationRequest;
import com.SWP391.horserace.violations.dto.ViolationDetailResponse;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import com.SWP391.horserace.wallets.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RaceResultServiceImpl implements RaceResultService {

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final RaceResultRepository raceResultRepository;
    private final RaceResultVersionRepository raceResultVersionRepository;
    private final JockeyAssignmentRepository jockeyAssignmentRepository;
    private final UserRepository userRepository;
    private final com.SWP391.horserace.notifications.service.NotificationService notificationService;
    private final com.SWP391.horserace.staffing.service.RefereeSubmissionCodeService refereeSubmissionCodeService;
    private final com.SWP391.horserace.violations.service.ViolationService violationService;
    private final RegistrationRepository registrationRepository;
    private final PenaltyRepository penaltyRepository;
    private final PrizeRepository prizeRepository;
    private final JockeyProfileRepository jockeyProfileRepository;
    private final WalletLedgerService walletLedgerService;
    private final WalletService walletService;

    private static final String ADMIN_ROLE_CODE = "ADMIN";
    /** Default jockey cut of a horse's prize when the jockey profile has no prizePercent set. */
    private static final BigDecimal DEFAULT_JOCKEY_PRIZE_PCT = new BigDecimal("10");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @Override
    @Transactional
    public List<ResultRowResponse> recordResults(UUID currentUserId, UUID raceId, RecordResultsRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Race race = raceRepository.findByRaceIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        // Results may only be filed in the post-race window (after the admin ends the race).
        if (race.getStatus() != RaceStatus.FINISHED) {
            throw new AppException(ErrorCode.RACE_NOT_FINISHED);
        }

        List<RecordResultsRequest.ResultRow> rows =
                request != null && request.results() != null ? request.results() : List.of();

        List<RaceResult> saved = upsertResults(race, rows);

        // Resolve riding jockey names for all touched entries in one query.
        List<UUID> entryIds = saved.stream()
                .map(r -> r.getEntry().getEntryId())
                .toList();
        Map<UUID, String> jockeyNameByEntry = jockeyNamesByEntryIds(entryIds);

        return saved.stream()
                .map(r -> toRowResponse(r, jockeyNameByEntry.get(r.getEntry().getEntryId())))
                .toList();
    }

    @Override
    @Transactional
    public SubmitReportResponse submitReport(UUID currentUserId, UUID raceId, SubmitReportRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        User user = userRepository.findByUserIdAndDeletedFalse(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        boolean isAdmin = isAdmin(user);

        // A referee must validate + consume the emailed OTP; ADMINs on this endpoint skip it.
        if (!isAdmin) {
            refereeSubmissionCodeService.validateAndConsume(currentUserId, raceId,
                    request != null ? request.getOtp() : null);
        }

        // Referees are held to the one-time lock; admins are not.
        return publishReport(user, raceId, request, /* enforceLock= */ !isAdmin);
    }

    @Override
    @Transactional
    public SubmitReportResponse adminOverridePublish(UUID adminUserId, UUID raceId, SubmitReportRequest request) {
        if (adminUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        User admin = userRepository.findByUserIdAndDeletedFalse(adminUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        // No OTP, and never blocked by the referee one-time lock.
        return publishReport(admin, raceId, request, /* enforceLock= */ false);
    }

    /**
     * Shared publish path (CN3). CRITICAL ORDERING for the atomic lock (RT-CRITICAL-3):
     * upsert the rows FIRST (do NOT stamp {@code refereeSubmittedAt} in the upsert), THEN run the
     * atomic compare-and-swap {@code markRefereeSubmitted}. When the caller is a referee and it
     * stamps 0 rows, the report was already published → {@code RESULT_ALREADY_SUBMITTED}. The whole
     * method is transactional, so the upsert rolls back on that reject.
     */
    private SubmitReportResponse publishReport(User user, UUID raceId, SubmitReportRequest request,
                                               boolean enforceLock) {
        Race race = raceRepository.findByRaceIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        // Reports may only be filed after the race has finished.
        if (race.getStatus() != RaceStatus.FINISHED) {
            throw new AppException(ErrorCode.RACE_NOT_FINISHED);
        }

        List<RecordResultsRequest.ResultRow> rows =
                request != null && request.getResults() != null ? request.getResults() : List.of();
        // A report must carry the finish order — an empty payload is invalid (and would otherwise make
        // the lock-stamp match 0 rows and mis-report "already submitted" on a first submit).
        if (rows.isEmpty()) {
            throw new AppException(ErrorCode.REPORT_RESULTS_REQUIRED);
        }

        // (d) One-time lock (referee): the race report is published once. Authoritative guard, checked
        // BEFORE upsert so a legitimate first submit is never mis-flagged. Concurrency: the OTP consume
        // CAS serializes the same referee, and the race_result.entry_id UNIQUE constraint stops two
        // different referees from double-inserting the same entries.
        if (enforceLock && raceResultRepository.existsByRace_RaceIdAndRefereeSubmittedAtIsNotNull(raceId)) {
            throw new AppException(ErrorCode.RESULT_ALREADY_SUBMITTED);
        }

        // (e) upsert this report's rows, then stamp ONLY those rows (payload-scoped).
        List<RaceResult> saved = upsertResults(race, rows);
        OffsetDateTime now = OffsetDateTime.now();
        List<UUID> stampEntryIds = saved.stream().map(r -> r.getEntry().getEntryId()).toList();
        raceResultRepository.markRefereeSubmitted(raceId, stampEntryIds, now);

        // (f) create violations WITHOUT the refCode gate (the OTP already authenticated the report).
        List<UUID> violationIds = new ArrayList<>();
        List<CreateViolationRequest> violations =
                request != null && request.getViolations() != null ? request.getViolations() : List.of();
        for (CreateViolationRequest v : violations) {
            ViolationDetailResponse created = violationService.createViolationTrusted(user.getUserId(), raceId, v);
            if (created != null) {
                violationIds.add(created.getViolationId());
            }
        }

        // (g) audit: snapshot each published row (who published each entry — FR-21).
        for (RaceResult r : saved) {
            RaceResultVersion version = RaceResultVersion.builder()
                    .result(r)
                    .versionNo(r.getCurrentVersionNo())
                    .finishPosition(r.getFinishPosition())
                    .finishTimeMs(r.getFinishTimeMs())
                    .score(r.getScore())
                    .officialityStatus(r.getOfficialityStatus() != null ? r.getOfficialityStatus().name() : null)
                    .changedBy(user)
                    .changeReason("Referee report published")
                    .build();
            raceResultVersionRepository.save(version);
        }

        notifyAdmins("Race report submitted",
                (race.getName() != null ? race.getName() : "A race") + " has a new referee report to certify.");

        List<UUID> entryIds = saved.stream().map(r -> r.getEntry().getEntryId()).toList();
        Map<UUID, String> jockeyNameByEntry = jockeyNamesByEntryIds(entryIds);
        List<ResultRowResponse> resultRows = saved.stream()
                .map(r -> toRowResponse(r, jockeyNameByEntry.get(r.getEntry().getEntryId())))
                .toList();

        return SubmitReportResponse.builder()
                .raceId(raceId)
                .results(resultRows)
                .violationIds(violationIds)
                .submittedAt(now)
                .build();
    }

    @Override
    @Transactional
    public void flagUnderReview(UUID currentUserId, UUID raceId, UUID resultId) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Race race = raceRepository.findByRaceIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        RaceResult result = raceResultRepository.findById(resultId)
                .orElseThrow(() -> new AppException(ErrorCode.RESULT_NOT_FOUND));

        if (result.getRace() == null || !race.getRaceId().equals(result.getRace().getRaceId())) {
            throw new AppException(ErrorCode.RESULT_ENTRY_RACE_MISMATCH);
        }

        // A certified (OFFICIAL) result is immutable — it cannot be re-opened for review (RT-MEDIUM-1).
        if (result.getOfficialityStatus() == OfficialityStatus.OFFICIAL) {
            throw new AppException(ErrorCode.RESULT_ALREADY_OFFICIAL);
        }

        User reviewer = userRepository.findByUserIdAndDeletedFalse(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // Audit who raised the inquiry (FR-21) — snapshot the prior state before flipping.
        RaceResultVersion version = RaceResultVersion.builder()
                .result(result)
                .versionNo(result.getCurrentVersionNo())
                .finishPosition(result.getFinishPosition())
                .finishTimeMs(result.getFinishTimeMs())
                .score(result.getScore())
                .officialityStatus(result.getOfficialityStatus() != null ? result.getOfficialityStatus().name() : null)
                .changedBy(reviewer)
                .changeReason("Flagged under review")
                .build();
        raceResultVersionRepository.save(version);

        result.setOfficialityStatus(OfficialityStatus.UNDER_REVIEW);
        raceResultRepository.save(result);
    }

    /** Upsert the finish order (one race_result per entry, status PROVISIONAL). */
    private List<RaceResult> upsertResults(Race race, List<RecordResultsRequest.ResultRow> rows) {
        UUID raceId = race.getRaceId();

        // FR-01: guard duplicate finish positions BEFORE any save — under the pessimistic lock so
        // concurrent writers serialize. Covers within-payload dupes AND payload-vs-persisted.
        Map<UUID, Integer> desiredByEntry = new HashMap<>();
        for (RecordResultsRequest.ResultRow row : rows) {
            if (row.finishPosition() != null) {
                desiredByEntry.put(row.entryId(), row.finishPosition());
            }
        }
        assertNoDuplicateFinishPositions(raceId, desiredByEntry);

        List<RaceResult> saved = new ArrayList<>();
        for (RecordResultsRequest.ResultRow row : rows) {
            RaceEntry entry = raceEntryRepository.findByIdWithDetails(row.entryId())
                    .orElseThrow(() -> new AppException(ErrorCode.ENTRY_NOT_FOUND));

            // Entry must belong to the race named in the path.
            if (entry.getRace() == null || !raceId.equals(entry.getRace().getRaceId())) {
                throw new AppException(ErrorCode.RESULT_ENTRY_RACE_MISMATCH);
            }

            // One result row per entry — upsert.
            RaceResult result = raceResultRepository.findByEntry_EntryId(row.entryId())
                    .orElseGet(() -> RaceResult.builder()
                            .race(race)
                            .entry(entry)
                            .currentVersionNo(1)
                            .build());

            result.setRace(race);
            result.setEntry(entry);
            result.setFinishPosition(row.finishPosition());
            result.setFinishTimeMs(row.finishTimeMs());
            result.setLengthsBehind(row.lengthsBehind());
            result.setScore(row.score());
            // Never silently downgrade an in-progress inquiry (UNDER_REVIEW) or a certified (OFFICIAL)
            // result back to PROVISIONAL — those must be resolved/amended through their own paths.
            OfficialityStatus current = result.getOfficialityStatus();
            if (current != OfficialityStatus.UNDER_REVIEW && current != OfficialityStatus.OFFICIAL) {
                result.setOfficialityStatus(OfficialityStatus.PROVISIONAL);
            }

            saved.add(raceResultRepository.save(result));
        }
        return saved;
    }

    /**
     * FR-01: reject duplicate finish positions within a race. Acquires a PESSIMISTIC_WRITE lock on the
     * parent {@code race} row FIRST (it always exists, so it serializes concurrent writers even when no
     * {@code race_result} rows exist yet — a {@code FOR UPDATE} over the result rows can't lock
     * not-yet-inserted rows), then reads the committed results and checks that the union of the desired
     * positions for THIS operation ({@code desiredByEntry}) plus the persisted positions of every OTHER
     * entry (not in {@code desiredByEntry}) has no repeated non-null value. A collision →
     * {@link ErrorCode#DUPLICATE_FINISH_POSITION}. Re-submitting an entry's own position is safe (its
     * persisted row is excluded because that entry is in {@code desiredByEntry}).
     */
    private void assertNoDuplicateFinishPositions(UUID raceId, Map<UUID, Integer> desiredByEntry) {
        // Serialize on the always-present race row: a second concurrent writer blocks here until the
        // first commits, then reads the first writer's committed positions below and detects the clash.
        raceRepository.findByRaceIdForUpdate(raceId);

        List<Integer> effective = new ArrayList<>();
        // Desired positions from this operation (non-null only).
        for (Integer pos : desiredByEntry.values()) {
            if (pos != null) {
                effective.add(pos);
            }
        }
        // Persisted positions of every OTHER entry (those not being written in this operation).
        // Read AFTER acquiring the race lock so we see the latest committed rows (READ COMMITTED).
        for (RaceResult r : raceResultRepository.findByRace_RaceId(raceId)) {
            UUID entryId = r.getEntry() != null ? r.getEntry().getEntryId() : null;
            if (entryId != null && desiredByEntry.containsKey(entryId)) {
                continue; // this entry's position is being (re)set by the current operation
            }
            if (r.getFinishPosition() != null) {
                effective.add(r.getFinishPosition());
            }
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer pos : effective) {
            if (!seen.add(pos)) {
                throw new AppException(ErrorCode.DUPLICATE_FINISH_POSITION);
            }
        }
    }

    /** Best-effort in-app notification to every active ADMIN (never throws to the caller). */
    private void notifyAdmins(String title, String message) {
        try {
            for (User admin : userRepository.findByRole_RoleCodeAndDeletedFalse(ADMIN_ROLE_CODE)) {
                notificationService.notifyUser(admin.getUserId(), title, message);
            }
        } catch (RuntimeException e) {
            // best-effort — a notify failure must not fail the publish.
        }
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() != null && ADMIN_ROLE_CODE.equals(user.getRole().getRoleCode());
    }

    @Override
    @Transactional(readOnly = true)
    public RaceResultsResponse getResults(UUID raceId) {
        Race race = raceRepository.findByRaceIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        List<RaceResult> results = raceResultRepository.findByRaceIdWithEntry(raceId);

        List<UUID> entryIds = results.stream()
                .map(r -> r.getEntry().getEntryId())
                .toList();
        Map<UUID, String> jockeyNameByEntry = jockeyNamesByEntryIds(entryIds);

        // winningTimeMs = the minimum finish time among recorded results (null if none).
        Long winningTimeMs = results.stream()
                .map(RaceResult::getFinishTimeMs)
                .filter(t -> t != null)
                .min(Comparator.naturalOrder())
                .orElse(null);

        List<String> fractions = race.getFractions() == null ? List.of()
                : race.getFractions().stream()
                        .sorted(Comparator.comparing(RaceFraction::getSplitNo,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(f -> f.getTimeMs() != null ? String.valueOf(f.getTimeMs()) : null)
                        .toList();

        List<RaceResultsResponse.OrderRow> order = results.stream()
                .sorted(Comparator.comparing(RaceResult::getFinishPosition,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(r -> {
                    RaceEntry e = r.getEntry();
                    Horse horse = e.getRegistration() != null ? e.getRegistration().getHorse() : null;
                    return RaceResultsResponse.OrderRow.builder()
                            .resultId(r.getResultId())
                            .finishPosition(r.getFinishPosition())
                            .entryNo(e.getEntryNo())
                            .horseName(horse != null ? horse.getName() : null)
                            .jockeyName(jockeyNameByEntry.get(e.getEntryId()))
                            .weightCarriedLbs(e.getWeightCarriedLbs())
                            .finishTimeMs(r.getFinishTimeMs())
                            .lengthsBehind(r.getLengthsBehind())
                            .odds(e.getOdds())
                            .build();
                })
                .toList();

        // #8: horses APPROVED into this tournament but with no entry in THIS race. The NOT-EXISTS /
        // APPROVED-only filter lives in JPQL (findApprovedNotEnteredInRace) — E2E-covered, not unit-reachable.
        UUID tournamentId = race.getTournament() != null ? race.getTournament().getTournamentId() : null;
        List<RaceResultsResponse.RegisteredNotEnteredRow> registeredNotEntered = tournamentId == null ? List.of()
                : registrationRepository.findApprovedNotEnteredInRace(tournamentId, raceId).stream()
                    .map(r -> RaceResultsResponse.RegisteredNotEnteredRow.builder()
                            .registrationId(r.getRegistrationId())
                            .registrationCode(r.getRegistrationCode())
                            .horseId(r.getHorse() != null ? r.getHorse().getHorseId() : null)
                            .horseName(r.getHorse() != null ? r.getHorse().getName() : null)
                            .ownerName(r.getOwner() != null ? r.getOwner().getFullName() : null)
                            .build())
                    .toList();

        return RaceResultsResponse.builder()
                .raceId(raceId)
                .officialityStatus(representativeStatus(results))
                .winningTimeMs(winningTimeMs)
                .trackCondition(race.getTrackCondition())
                .trackBias(race.getTrackBias())
                .windSpeedKph(race.getWindSpeedKph())
                .fractions(fractions)
                .photofinishUrl(race.getPhotofinishUrl())
                .stewardsReport(race.getStewardsReport())
                .order(order)
                .registeredNotEntered(registeredNotEntered)
                .build();
    }

    @Override
    @Transactional
    public UpdateResultResponse updateResult(UUID currentUserId, UUID raceId, UUID resultId,
                                             UpdateResultRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Race race = raceRepository.findByRaceIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        RaceResult result = raceResultRepository.findById(resultId)
                .orElseThrow(() -> new AppException(ErrorCode.RESULT_NOT_FOUND));

        // Result must belong to the race named in the path.
        if (result.getRace() == null || !race.getRaceId().equals(result.getRace().getRaceId())) {
            throw new AppException(ErrorCode.RESULT_ENTRY_RACE_MISMATCH);
        }

        // FR-01: a new finish position must not collide with another result in the race (checked under
        // the pessimistic lock, BEFORE any version/row write). Re-submitting this row's own position is
        // fine — the guard excludes the target entry's persisted row.
        if (request != null && request.finishPosition() != null && result.getEntry() != null) {
            assertNoDuplicateFinishPositions(raceId,
                    Map.of(result.getEntry().getEntryId(), request.finishPosition()));
        }

        User changedBy = userRepository.findByUserIdAndDeletedFalse(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // Snapshot the CURRENT values into a new version BEFORE applying any change.
        RaceResultVersion version = RaceResultVersion.builder()
                .result(result)
                .versionNo(result.getCurrentVersionNo())
                .finishPosition(result.getFinishPosition())
                .finishTimeMs(result.getFinishTimeMs())
                .score(result.getScore())
                .officialityStatus(result.getOfficialityStatus() != null
                        ? result.getOfficialityStatus().name() : null)
                .changedBy(changedBy)
                .changeReason(request != null ? request.changeReason() : null)
                .build();
        raceResultVersionRepository.save(version);

        // Apply non-null changes.
        if (request != null) {
            if (request.finishPosition() != null) result.setFinishPosition(request.finishPosition());
            if (request.finishTimeMs() != null) result.setFinishTimeMs(request.finishTimeMs());
            if (request.lengthsBehind() != null) result.setLengthsBehind(request.lengthsBehind());
        }
        result.setCurrentVersionNo(result.getCurrentVersionNo() + 1);
        result.setOfficialityStatus(OfficialityStatus.AMENDED);

        RaceResult savedResult = raceResultRepository.save(result);

        return UpdateResultResponse.builder()
                .resultId(savedResult.getResultId())
                .finishPosition(savedResult.getFinishPosition())
                .finishTimeMs(savedResult.getFinishTimeMs())
                .lengthsBehind(savedResult.getLengthsBehind())
                .currentVersionNo(savedResult.getCurrentVersionNo())
                .officialityStatus(savedResult.getOfficialityStatus())
                .build();
    }

    @Override
    @Transactional
    public void deleteResult(UUID currentUserId, UUID raceId, UUID resultId) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Race race = raceRepository.findByRaceIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        RaceResult result = raceResultRepository.findById(resultId)
                .orElseThrow(() -> new AppException(ErrorCode.RESULT_NOT_FOUND));

        if (result.getRace() == null || !race.getRaceId().equals(result.getRace().getRaceId())) {
            throw new AppException(ErrorCode.RESULT_ENTRY_RACE_MISMATCH);
        }

        // Published (certified) results are immutable — they can no longer be deleted.
        if (result.getOfficialityStatus() == OfficialityStatus.OFFICIAL) {
            throw new AppException(ErrorCode.RESULT_ALREADY_OFFICIAL);
        }

        // Remove the audit trail first (FK), then the result row itself.
        raceResultVersionRepository.deleteByResult_ResultId(resultId);
        raceResultRepository.delete(result);
    }

    @Override
    @Transactional
    // TODO(authz #0): restrict to RESULT_PUBLISH
    public CertifyResultsResponse certify(UUID currentUserId, UUID raceId, CertifyResultsRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (request == null || !Boolean.TRUE.equals(request.acknowledgeInquiriesResolved())) {
            throw new AppException(ErrorCode.RESULT_INQUIRIES_UNRESOLVED);
        }

        Race race = raceRepository.findByRaceIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        // Publish only after the race has finished (admin consolidates all referees' reports first).
        if (race.getStatus() != RaceStatus.FINISHED) {
            throw new AppException(ErrorCode.RACE_NOT_FINISHED);
        }

        User certifier = userRepository.findByUserIdAndDeletedFalse(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        OffsetDateTime now = OffsetDateTime.now();

        List<RaceResult> results = raceResultRepository.findByRaceIdWithEntry(raceId);

        // FR-19: a result still under review must be resolved before the race can be certified.
        if (results.stream().anyMatch(r -> r.getOfficialityStatus() == OfficialityStatus.UNDER_REVIEW)) {
            throw new AppException(ErrorCode.RESULT_UNDER_REVIEW_BLOCKS_CERTIFY);
        }

        // Apply stewards' rulings (time penalties / disqualifications) to the order of finish BEFORE
        // it is frozen OFFICIAL — so both prizes and bet settlement use the corrected placings.
        applyPenaltiesAndRerank(race, results);

        for (RaceResult result : results) {
            result.setOfficialityStatus(OfficialityStatus.OFFICIAL);
            result.setApprovedBy(certifier);
            result.setPublishedAt(now);
            raceResultRepository.save(result);
        }

        race.setStatus(RaceStatus.OFFICIAL);
        race.setCertifiedByUserId(certifier.getUserId());
        race.setCertifiedAt(now);
        race.setStewardsReport(request.stewardsReport());
        raceRepository.save(race);

        // Pay the purse: credit each finishing horse's owner + jockey (mint) per the prize distribution.
        creditPrizes(race, results);

        // Notify each owner whose horse ran that the official result is published.
        for (RaceResult result : results) {
            RaceEntry entry = result.getEntry();
            if (entry == null || entry.getRegistration() == null) continue;
            User owner = entry.getRegistration().getOwner();
            if (owner == null) continue;
            String horse = entry.getRegistration().getHorse() != null
                    ? entry.getRegistration().getHorse().getName() : "Your horse";
            String pos = result.getFinishPosition() != null ? ordinal(result.getFinishPosition()) : "—";
            notificationService.notifyUser(owner.getUserId(),
                    "Official results published",
                    (race.getName() != null ? race.getName() : "Your race") + ": " + horse + " finished " + pos + ".");
        }

        return CertifyResultsResponse.builder()
                .raceId(raceId)
                .raceStatus(race.getStatus())
                .officialityStatus(OfficialityStatus.OFFICIAL)
                .certifiedByUserId(certifier.getUserId())
                .certifiedByName(certifier.getFullName())
                .publishedAt(now)
                .openInquiries(0)
                .build();
    }

    // ── helpers ──

    /** 1 -> "1st", 2 -> "2nd", 3 -> "3rd", 4 -> "4th" … for human-friendly finish positions. */
    private static String ordinal(int n) {
        if (n % 100 >= 11 && n % 100 <= 13) return n + "th";
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }

    /** Riding (ACCEPTED) jockey name per entry, resolved in one query (avoids N+1). */
    private Map<UUID, String> jockeyNamesByEntryIds(List<UUID> entryIds) {
        if (entryIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> byEntry = new HashMap<>();
        for (JockeyAssignment ja : jockeyAssignmentRepository.findAcceptedByEntryIds(entryIds)) {
            if (ja.getEntry() != null && ja.getJockey() != null) {
                byEntry.put(ja.getEntry().getEntryId(), ja.getJockey().getFullName());
            }
        }
        return byEntry;
    }

    // ── stewards' rulings applied to placings (on certify, before OFFICIAL) ──

    /**
     * Apply issued stewards' rulings to the finishing order before certification freezes it: add time
     * penalties to finish times, disqualify DQ'd entries (their finish position becomes null), then
     * renumber the surviving finishers by adjusted time. Only rulings tied to a specific entry apply.
     * Idempotent: only {@code ISSUED} penalties are applied and they flip to {@code UPHELD}; the
     * FINISHED→OFFICIAL certify transition is itself one-shot, so this never double-applies.
     */
    private void applyPenaltiesAndRerank(Race race, List<RaceResult> results) {
        List<Penalty> penalties = penaltyRepository.findByRace_RaceId(race.getRaceId());
        if (penalties.isEmpty()) {
            return;
        }
        Map<UUID, RaceResult> byEntry = new HashMap<>();
        for (RaceResult r : results) {
            if (r.getEntry() != null) {
                byEntry.put(r.getEntry().getEntryId(), r);
            }
        }

        Set<UUID> disqualified = new HashSet<>();
        boolean changed = false;
        for (Penalty penalty : penalties) {
            if (penalty.getStatus() != PenaltyStatus.ISSUED || penalty.getEntry() == null) {
                continue;
            }
            RaceResult result = byEntry.get(penalty.getEntry().getEntryId());
            if (result == null) {
                continue;
            }
            if (penalty.getPenaltyType() == PenaltyType.TIME_PENALTY
                    && penalty.getTimePenaltyMs() != null && result.getFinishTimeMs() != null) {
                result.setFinishTimeMs(result.getFinishTimeMs() + penalty.getTimePenaltyMs());
                changed = true;
            } else if (penalty.getPenaltyType() == PenaltyType.DISQUALIFICATION) {
                RaceEntry entry = penalty.getEntry();
                entry.setStatus(RaceEntryStatus.DISQUALIFIED);
                raceEntryRepository.save(entry);
                disqualified.add(entry.getEntryId());
                changed = true;
            }
            penalty.setStatus(PenaltyStatus.UPHELD);
            penaltyRepository.save(penalty);
        }

        if (!changed) {
            return;
        }

        // Renumber surviving finishers by adjusted time (null times sort last); DQ'd lose their place.
        List<RaceResult> survivors = results.stream()
                .filter(r -> r.getEntry() == null || !disqualified.contains(r.getEntry().getEntryId()))
                .sorted(Comparator.comparing(RaceResult::getFinishTimeMs,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        int position = 1;
        for (RaceResult r : survivors) {
            r.setFinishPosition(position++);
            raceResultRepository.save(r);
        }
        for (RaceResult r : results) {
            if (r.getEntry() != null && disqualified.contains(r.getEntry().getEntryId())) {
                r.setFinishPosition(null);
                raceResultRepository.save(r);
            }
        }
    }

    // ── prize distribution (credited on certify) ──

    /**
     * Credit each finishing horse's prize to its owner + jockey wallets (MINT — the purse is
     * operator-funded, so a pure CREDIT with no debit source) and record {@link Prize} rows. The
     * jockey takes their {@link JockeyProfile#getPrizePercent()} (default 10% if unset); the owner
     * takes the remainder. Idempotent: skips any entry that has already earned a prize.
     */
    private void creditPrizes(Race race, List<RaceResult> results) {
        List<PrizeDistributionItem> dist = race.getPrizeDistribution();
        if (dist == null || dist.isEmpty()) {
            return;
        }
        Map<Integer, BigDecimal> amountByPosition = new HashMap<>();
        for (PrizeDistributionItem item : dist) {
            Integer pos = parsePlace(item.getPlace());
            if (pos != null && item.getAmount() != null && item.getAmount().signum() > 0) {
                amountByPosition.put(pos, item.getAmount());
            }
        }
        if (amountByPosition.isEmpty()) {
            return;
        }

        // Riding (ACCEPTED) jockey per entry, resolved in one query.
        List<UUID> entryIds = results.stream()
                .map(r -> r.getEntry() != null ? r.getEntry().getEntryId() : null)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, User> jockeyByEntry = new HashMap<>();
        for (JockeyAssignment ja : jockeyAssignmentRepository.findAcceptedByEntryIds(entryIds)) {
            if (ja.getEntry() != null && ja.getJockey() != null) {
                jockeyByEntry.put(ja.getEntry().getEntryId(), ja.getJockey());
            }
        }

        for (RaceResult result : results) {
            RaceEntry entry = result.getEntry();
            Integer pos = result.getFinishPosition();
            if (entry == null || pos == null || entry.getRegistration() == null) {
                continue;
            }
            BigDecimal amount = amountByPosition.get(pos);
            if (amount == null) {
                continue;
            }
            // Idempotency belt: never pay an entry twice.
            if (entry.getPrizeEarned() != null && entry.getPrizeEarned().signum() > 0) {
                continue;
            }
            User owner = entry.getRegistration().getOwner();
            if (owner == null) {
                continue;
            }

            User jockey = jockeyByEntry.get(entry.getEntryId());
            BigDecimal jockeyCut = BigDecimal.ZERO;
            if (jockey != null) {
                BigDecimal pct = jockeyProfileRepository.findById(jockey.getUserId())
                        .map(JockeyProfile::getPrizePercent)
                        .orElse(null);
                if (pct == null) {
                    pct = DEFAULT_JOCKEY_PRIZE_PCT;
                }
                jockeyCut = amount.multiply(pct).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            }
            BigDecimal ownerCut = amount.subtract(jockeyCut);

            if (ownerCut.signum() > 0) {
                walletService.getOrCreateWallet(owner.getUserId());
                walletLedgerService.applyEntry(owner.getUserId(), EntryType.CREDIT, TxnCategory.PRIZE,
                        ownerCut, "RACE_ENTRY", entry.getEntryId());
                savePrize(race, entry, BeneficiaryType.OWNER, pos, ownerCut);
            }
            if (jockey != null && jockeyCut.signum() > 0) {
                walletService.getOrCreateWallet(jockey.getUserId());
                walletLedgerService.applyEntry(jockey.getUserId(), EntryType.CREDIT, TxnCategory.PRIZE,
                        jockeyCut, "RACE_ENTRY", entry.getEntryId());
                savePrize(race, entry, BeneficiaryType.JOCKEY, pos, jockeyCut);
            }

            entry.setPrizeEarned(amount);
            raceEntryRepository.save(entry);
        }
    }

    private void savePrize(Race race, RaceEntry entry, BeneficiaryType type, int position, BigDecimal amount) {
        prizeRepository.save(Prize.builder()
                .race(race)
                .beneficiaryType(type)
                .rankPosition(position)
                .prizeAmount(amount)
                // Unique per (entry, beneficiary): "PRZ-O-<entryId>" / "PRZ-J-<entryId>" (≤ 50 chars).
                .prizeCode("PRZ-" + type.name().charAt(0) + "-" + entry.getEntryId())
                .status(PrizeStatus.AWARDED)
                .build());
    }

    /** Parse a distribution place label ("1st", "2nd", "10th", or "3") to a finish-position int. */
    private static Integer parsePlace(String place) {
        if (place == null) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (char c : place.trim().toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (digits.length() > 0) {
                break;
            }
        }
        if (digits.length() == 0) {
            return null;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Race-level officiality: OFFICIAL only when every result is OFFICIAL; otherwise the most
     * advanced amendment state wins (AMENDED > UNDER_REVIEW > PROVISIONAL). Empty -> PROVISIONAL.
     */
    private OfficialityStatus representativeStatus(List<RaceResult> results) {
        if (results.isEmpty()) {
            return OfficialityStatus.PROVISIONAL;
        }
        boolean allOfficial = results.stream()
                .allMatch(r -> r.getOfficialityStatus() == OfficialityStatus.OFFICIAL);
        if (allOfficial) {
            return OfficialityStatus.OFFICIAL;
        }
        if (results.stream().anyMatch(r -> r.getOfficialityStatus() == OfficialityStatus.AMENDED)) {
            return OfficialityStatus.AMENDED;
        }
        if (results.stream().anyMatch(r -> r.getOfficialityStatus() == OfficialityStatus.UNDER_REVIEW)) {
            return OfficialityStatus.UNDER_REVIEW;
        }
        return OfficialityStatus.PROVISIONAL;
    }

    private ResultRowResponse toRowResponse(RaceResult r, String jockeyName) {
        RaceEntry e = r.getEntry();
        Horse horse = e != null && e.getRegistration() != null ? e.getRegistration().getHorse() : null;
        return ResultRowResponse.builder()
                .resultId(r.getResultId())
                .entryId(e != null ? e.getEntryId() : null)
                .entryNo(e != null ? e.getEntryNo() : null)
                .horseName(horse != null ? horse.getName() : null)
                .jockeyName(jockeyName)
                .finishPosition(r.getFinishPosition())
                .finishTimeMs(r.getFinishTimeMs())
                .lengthsBehind(r.getLengthsBehind())
                .score(r.getScore())
                .officialityStatus(r.getOfficialityStatus())
                .build();
    }
}

package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.races.dto.CertifyResultsRequest;
import com.SWP391.horserace.races.dto.CertifyResultsResponse;
import com.SWP391.horserace.races.dto.RaceResultsResponse;
import com.SWP391.horserace.races.dto.RecordResultsRequest;
import com.SWP391.horserace.races.dto.ResultRowResponse;
import com.SWP391.horserace.races.dto.UpdateResultRequest;
import com.SWP391.horserace.races.dto.UpdateResultResponse;
import com.SWP391.horserace.races.entity.OfficialityStatus;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceFraction;
import com.SWP391.horserace.races.entity.RaceResult;
import com.SWP391.horserace.races.entity.RaceResultVersion;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.races.repository.RaceResultVersionRepository;
import com.SWP391.horserace.races.service.RaceResultService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final com.SWP391.horserace.staffing.service.RefereeCodeValidator refereeCodeValidator;

    @Override
    @Transactional
    public List<ResultRowResponse> recordResults(UUID currentUserId, UUID raceId, RecordResultsRequest request) {
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        // Referees must quote their admin-issued per-race code to file results (admins bypass).
        refereeCodeValidator.validate(currentUserId, raceId, request != null ? request.refCode() : null);

        Race race = raceRepository.findByRaceIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        // Results may only be filed in the post-race window (after the admin ends the race).
        if (race.getStatus() != RaceStatus.FINISHED) {
            throw new AppException(ErrorCode.RACE_NOT_FINISHED);
        }

        List<RecordResultsRequest.ResultRow> rows =
                request != null && request.results() != null ? request.results() : List.of();

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
            result.setOfficialityStatus(OfficialityStatus.PROVISIONAL);

            saved.add(raceResultRepository.save(result));
        }

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

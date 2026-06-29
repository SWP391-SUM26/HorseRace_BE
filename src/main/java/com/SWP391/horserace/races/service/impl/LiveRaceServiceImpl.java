package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.races.dto.LiveRaceResponse;
import com.SWP391.horserace.races.dto.UpdateLivePositionRequest;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceResult;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.races.service.LiveRaceService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class LiveRaceServiceImpl implements LiveRaceService {

    private final RaceRepository raceRepository;
    private final RaceResultRepository raceResultRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final JockeyAssignmentRepository jockeyAssignmentRepository;

    private final Map<UUID, Map<UUID, UpdateLivePositionRequest.RunnerTelemetry>> liveTelemetryCache = new ConcurrentHashMap<>();

    @Override
    public void updateLivePositions(UUID raceId, UpdateLivePositionRequest request) {
        Race race = raceRepository.findByRaceIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        Map<UUID, UpdateLivePositionRequest.RunnerTelemetry> raceTelemetry = liveTelemetryCache.computeIfAbsent(raceId,
                k -> new ConcurrentHashMap<>());
        for (UpdateLivePositionRequest.RunnerTelemetry runner : request.getRunners()) {
            raceTelemetry.put(runner.getEntryId(), runner);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LiveRaceResponse getLive(UUID raceId) {
        // TODO(realtime): WebSocket /topic/races/{id}/live for push; this is the poll
        // fallback.
        Race race = raceRepository.findByRaceIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        // raceClockMs: ms since actual start (>= 0), or 0 if not started yet.
        long raceClockMs = 0L;
        if (race.getActualStartAt() != null) {
            long elapsed = Duration.between(race.getActualStartAt(), OffsetDateTime.now()).toMillis();
            raceClockMs = Math.max(0L, elapsed);
        }

        List<LiveRaceResponse.RunnerRow> runningOrder = buildRunningOrder(raceId);

        return LiveRaceResponse.builder()
                .raceId(raceId)
                .raceClockMs(raceClockMs)
                .videoFeedUrl(race.getVideoFeedUrl())
                .windSpeedKph(race.getWindSpeedKph())
                .windDirection(race.getWindDirection())
                .runningOrder(runningOrder)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LiveRaceResponse.RunnerRow> getLiveLeaderboard(UUID raceId) {
        // Delegate to the same logic that builds the running order for the full live
        // view
        return buildRunningOrder(raceId);
    }

    /**
     * Prefer the current results (ordered by finish position); if none recorded
     * yet, fall back to
     * the race's entries ordered by live telemetry, then lane, then entry number.
     */
    private List<LiveRaceResponse.RunnerRow> buildRunningOrder(UUID raceId) {
        List<RaceResult> results = raceResultRepository.findByRaceIdWithEntry(raceId);
        if (!results.isEmpty()) {
            List<UUID> entryIds = results.stream()
                    .map(r -> r.getEntry().getEntryId())
                    .toList();
            Map<UUID, String> jockeyByEntry = jockeyNamesByEntryIds(entryIds);
            Map<UUID, UpdateLivePositionRequest.RunnerTelemetry> telemetry = liveTelemetryCache.getOrDefault(raceId, Map.of());
            List<LiveRaceResponse.RunnerRow> rows = results.stream()
                    .sorted(Comparator.comparing(RaceResult::getFinishPosition,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(r -> {
                        RaceEntry e = r.getEntry();
                        Horse horse = e.getRegistration() != null ? e.getRegistration().getHorse() : null;
                        UpdateLivePositionRequest.RunnerTelemetry t = telemetry.get(e.getEntryId());
                        BigDecimal speed = t != null && t.getCurrentSpeedKph() != null
                                ? t.getCurrentSpeedKph()
                                : BigDecimal.ZERO;
                        return LiveRaceResponse.RunnerRow.builder()
                                .entryNo(e.getEntryNo())
                                .horseName(horse != null ? horse.getName() : null)
                                .jockeyName(jockeyByEntry.getOrDefault(e.getEntryId(), "TBD"))
                                .currentSpeedKph(speed)
                                .build();
                    })
                    .toList();

            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).setPosition(i + 1);
            }
            return rows;
        }

        // Fallback: no results yet — list the entries by live position, then lane, then
        // entry number.
        List<RaceEntry> entries = raceEntryRepository.findByRaceIdWithHorse(raceId);
        List<UUID> entryIds = entries.stream().map(RaceEntry::getEntryId).toList();
        Map<UUID, String> jockeyByEntry = jockeyNamesByEntryIds(entryIds);
        Map<UUID, UpdateLivePositionRequest.RunnerTelemetry> telemetry = liveTelemetryCache.getOrDefault(raceId,
                Map.of());

        List<LiveRaceResponse.RunnerRow> rows = entries.stream()
                .sorted(Comparator
                        .comparing((RaceEntry e) -> {
                            UpdateLivePositionRequest.RunnerTelemetry t = telemetry.get(e.getEntryId());
                            return t != null ? t.getPosition() : null;
                        }, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(RaceEntry::getLaneNo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(RaceEntry::getEntryNo, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(e -> {
                    Horse horse = e.getRegistration() != null ? e.getRegistration().getHorse() : null;
                    UpdateLivePositionRequest.RunnerTelemetry t = telemetry.get(e.getEntryId());
                    BigDecimal speed = t != null && t.getCurrentSpeedKph() != null
                            ? t.getCurrentSpeedKph()
                            : BigDecimal.ZERO;
                    return LiveRaceResponse.RunnerRow.builder()
                            .entryNo(e.getEntryNo())
                            .horseName(horse != null ? horse.getName() : null)
                            .jockeyName(jockeyByEntry.getOrDefault(e.getEntryId(), "TBD"))
                            .currentSpeedKph(speed)
                            .build();
                })
                .toList();

        // Fallback (no results yet): no official finish position — leave position null.
        return rows;
    }

    /**
     * Riding (ACCEPTED) jockey name per entry, resolved in one query (avoids N+1).
     */
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
}

package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.races.dto.LiveRaceResponse;
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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LiveRaceServiceImpl implements LiveRaceService {

    private final RaceRepository raceRepository;
    private final RaceResultRepository raceResultRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final JockeyAssignmentRepository jockeyAssignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public LiveRaceResponse getLive(UUID raceId) {
        // TODO(realtime): WebSocket /topic/races/{id}/live for push; this is the poll fallback.
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

    /**
     * Prefer the current results (ordered by finish position); if none recorded yet, fall back to
     * the race's entries ordered by lane then entry number. {@code currentSpeedKph} has no live
     * source, so it is always null.
     */
    private List<LiveRaceResponse.RunnerRow> buildRunningOrder(UUID raceId) {
        List<RaceResult> results = raceResultRepository.findByRaceIdWithEntry(raceId);
        if (!results.isEmpty()) {
            List<UUID> entryIds = results.stream()
                    .map(r -> r.getEntry().getEntryId())
                    .toList();
            Map<UUID, String> jockeyByEntry = jockeyNamesByEntryIds(entryIds);
            return results.stream()
                    .sorted(Comparator.comparing(RaceResult::getFinishPosition,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(r -> {
                        RaceEntry e = r.getEntry();
                        Horse horse = e.getRegistration() != null ? e.getRegistration().getHorse() : null;
                        return LiveRaceResponse.RunnerRow.builder()
                                .position(r.getFinishPosition())
                                .entryNo(e.getEntryNo())
                                .horseName(horse != null ? horse.getName() : null)
                                .jockeyName(jockeyByEntry.get(e.getEntryId()))
                                .currentSpeedKph(null)
                                .build();
                    })
                    .toList();
        }

        // Fallback: no results yet — list the entries by lane, then entry number.
        List<RaceEntry> entries = raceEntryRepository.findByRaceIdWithHorse(raceId);
        List<UUID> entryIds = entries.stream().map(RaceEntry::getEntryId).toList();
        Map<UUID, String> jockeyByEntry = jockeyNamesByEntryIds(entryIds);
        return entries.stream()
                .sorted(Comparator
                        .comparing(RaceEntry::getLaneNo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(RaceEntry::getEntryNo, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(e -> {
                    Horse horse = e.getRegistration() != null ? e.getRegistration().getHorse() : null;
                    return LiveRaceResponse.RunnerRow.builder()
                            .position(null)
                            .entryNo(e.getEntryNo())
                            .horseName(horse != null ? horse.getName() : null)
                            .jockeyName(jockeyByEntry.get(e.getEntryId()))
                            .currentSpeedKph(null)
                            .build();
                })
                .toList();
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
}

package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.races.dto.LiveRaceResponse;
import com.SWP391.horserace.races.entity.OfficialityStatus;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceResult;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveRaceServiceImplTest {

    @Mock RaceRepository raceRepository;
    @Mock RaceResultRepository raceResultRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock JockeyAssignmentRepository jockeyAssignmentRepository;

    private LiveRaceServiceImpl service;

    private final UUID raceId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();
    private final UUID entryId2 = UUID.randomUUID();

    private Race race;
    private RaceEntry entry;
    private RaceEntry entry2;

    @BeforeEach
    void setUp() {
        service = new LiveRaceServiceImpl(
                raceRepository, raceResultRepository, raceEntryRepository, jockeyAssignmentRepository);

        race = Race.builder()
                .raceId(raceId)
                .windSpeedKph(new BigDecimal("12.40"))
                .windDirection("NW")
                .videoFeedUrl("https://stream.example/race-1.m3u8")
                .build();

        Horse horse = Horse.builder().horseId(UUID.randomUUID()).name("Thunderbolt").build();
        entry = RaceEntry.builder()
                .entryId(entryId)
                .race(race)
                .registration(TournamentRegistration.builder().horse(horse).build())
                .entryNo(3)
                .laneNo(2)
                .build();

        Horse horse2 = Horse.builder().horseId(UUID.randomUUID()).name("Night Comet").build();
        entry2 = RaceEntry.builder()
                .entryId(entryId2)
                .race(race)
                .registration(TournamentRegistration.builder().horse(horse2).build())
                .entryNo(4)
                .laneNo(1)
                .build();
    }

    @Test
    void live_raceNotFound() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLive(raceId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RACE_NOT_FOUND);
    }

    @Test
    void live_runningOrderFromResults_sortedByFinishPosition_telemetryMapped() {
        race.setActualStartAt(null);

        RaceResult r1 = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry)
                .finishPosition(1).officialityStatus(OfficialityStatus.PROVISIONAL).build();
        RaceResult r2 = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry2)
                .finishPosition(2).officialityStatus(OfficialityStatus.PROVISIONAL).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        // intentionally out of order to prove sorting
        when(raceResultRepository.findByRaceIdWithEntry(raceId)).thenReturn(List.of(r2, r1));
        JockeyAssignment ja = JockeyAssignment.builder()
                .entry(entry)
                .jockey(User.builder().userId(UUID.randomUUID()).fullName("M. Reyes").build())
                .build();
        when(jockeyAssignmentRepository.findAcceptedByEntryIds(any())).thenReturn(List.of(ja));

        LiveRaceResponse resp = service.getLive(raceId);

        assertThat(resp.getRaceId()).isEqualTo(raceId);
        assertThat(resp.getVideoFeedUrl()).isEqualTo("https://stream.example/race-1.m3u8");
        assertThat(resp.getWindSpeedKph()).isEqualByComparingTo("12.40");
        assertThat(resp.getWindDirection()).isEqualTo("NW");
        assertThat(resp.getRaceClockMs()).isZero(); // no actualStartAt

        assertThat(resp.getRunningOrder()).extracting(LiveRaceResponse.RunnerRow::getPosition)
                .containsExactly(1, 2);
        LiveRaceResponse.RunnerRow first = resp.getRunningOrder().get(0);
        assertThat(first.getEntryNo()).isEqualTo(3);
        assertThat(first.getHorseName()).isEqualTo("Thunderbolt");
        assertThat(first.getJockeyName()).isEqualTo("M. Reyes");
        assertThat(first.getCurrentSpeedKph()).isNull(); // no live source
    }

    @Test
    void live_fallsBackToEntries_whenNoResults_sortedByLane() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRaceIdWithEntry(raceId)).thenReturn(List.of());
        // entry (lane 2) and entry2 (lane 1) — entry2 should come first
        when(raceEntryRepository.findByRaceIdWithHorse(raceId)).thenReturn(List.of(entry, entry2));
        lenient().when(jockeyAssignmentRepository.findAcceptedByEntryIds(any())).thenReturn(List.of());

        LiveRaceResponse resp = service.getLive(raceId);

        assertThat(resp.getRunningOrder()).hasSize(2);
        // sorted by lane: entry2 (lane 1) first, then entry (lane 2)
        assertThat(resp.getRunningOrder()).extracting(LiveRaceResponse.RunnerRow::getEntryNo)
                .containsExactly(4, 3);
        // position null when falling back to entries, speed null (no live source)
        assertThat(resp.getRunningOrder()).allSatisfy(row -> {
            assertThat(row.getPosition()).isNull();
            assertThat(row.getCurrentSpeedKph()).isNull();
        });
        assertThat(resp.getRunningOrder().get(0).getHorseName()).isEqualTo("Night Comet");
    }

    @Test
    void live_raceClockFromActualStartAt_isPositive() {
        race.setActualStartAt(OffsetDateTime.now().minusSeconds(90));

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRaceIdWithEntry(raceId)).thenReturn(List.of());
        when(raceEntryRepository.findByRaceIdWithHorse(raceId)).thenReturn(List.of());

        LiveRaceResponse resp = service.getLive(raceId);

        // ~90s elapsed — allow generous bounds for test timing
        assertThat(resp.getRaceClockMs()).isGreaterThanOrEqualTo(89_000L);
        assertThat(resp.getRaceClockMs()).isLessThan(120_000L);
    }
}

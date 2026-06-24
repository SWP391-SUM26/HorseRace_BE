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
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceResultServiceImplTest {

    @Mock RaceRepository raceRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock RaceResultRepository raceResultRepository;
    @Mock RaceResultVersionRepository raceResultVersionRepository;
    @Mock JockeyAssignmentRepository jockeyAssignmentRepository;
    @Mock UserRepository userRepository;

    private RaceResultServiceImpl service;

    private final UUID raceId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();
    private final UUID entryId2 = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID resultId = UUID.randomUUID();

    private Race race;
    private RaceEntry entry;
    private RaceEntry entry2;
    private User certifier;

    @BeforeEach
    void setUp() {
        service = new RaceResultServiceImpl(
                raceRepository, raceEntryRepository, raceResultRepository,
                raceResultVersionRepository, jockeyAssignmentRepository, userRepository);

        race = Race.builder().raceId(raceId).trackCondition("FAST").trackBias("NONE").build();

        Horse horse = Horse.builder().horseId(UUID.randomUUID()).name("Thunderbolt").build();
        entry = RaceEntry.builder()
                .entryId(entryId)
                .race(race)
                .registration(TournamentRegistration.builder().horse(horse).build())
                .entryNo(3)
                .weightCarriedLbs(126)
                .odds("5/2")
                .build();

        Horse horse2 = Horse.builder().horseId(UUID.randomUUID()).name("Night Comet").build();
        entry2 = RaceEntry.builder()
                .entryId(entryId2)
                .race(race)
                .registration(TournamentRegistration.builder().horse(horse2).build())
                .entryNo(4)
                .weightCarriedLbs(124)
                .odds("4/1")
                .build();

        certifier = User.builder().userId(userId).fullName("Chief Steward J. Lim").build();
    }

    private RecordResultsRequest recordReq() {
        return new RecordResultsRequest(List.of(
                new RecordResultsRequest.ResultRow(entryId, 1, 91230L, BigDecimal.ZERO, new BigDecimal("100.0"))));
    }

    // ── record (upsert + entry-race validation) ──

    @Test
    void record_nullPrincipal_unauthenticated() {
        assertThatThrownBy(() -> service.recordResults(null, raceId, recordReq()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void record_createsProvisionalResult_andMapsRow() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));
        when(raceResultRepository.findByEntry_EntryId(entryId)).thenReturn(Optional.empty());
        when(raceResultRepository.save(any(RaceResult.class))).thenAnswer(inv -> {
            RaceResult r = inv.getArgument(0);
            if (r.getResultId() == null) r.setResultId(resultId);
            return r;
        });
        JockeyAssignment ja = JockeyAssignment.builder()
                .entry(entry)
                .jockey(User.builder().userId(UUID.randomUUID()).fullName("M. Reyes").build())
                .build();
        when(jockeyAssignmentRepository.findAcceptedByEntryIds(any())).thenReturn(List.of(ja));

        List<ResultRowResponse> rows = service.recordResults(userId, raceId, recordReq());

        ArgumentCaptor<RaceResult> captor = ArgumentCaptor.forClass(RaceResult.class);
        verify(raceResultRepository).save(captor.capture());
        RaceResult saved = captor.getValue();
        assertThat(saved.getOfficialityStatus()).isEqualTo(OfficialityStatus.PROVISIONAL);
        assertThat(saved.getFinishPosition()).isEqualTo(1);
        assertThat(saved.getFinishTimeMs()).isEqualTo(91230L);
        assertThat(saved.getLengthsBehind()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(rows).hasSize(1);
        ResultRowResponse row = rows.get(0);
        assertThat(row.getEntryId()).isEqualTo(entryId);
        assertThat(row.getEntryNo()).isEqualTo(3);
        assertThat(row.getHorseName()).isEqualTo("Thunderbolt");
        assertThat(row.getJockeyName()).isEqualTo("M. Reyes");
        assertThat(row.getOfficialityStatus()).isEqualTo(OfficialityStatus.PROVISIONAL);
    }

    @Test
    void record_existingResult_upsertsInPlace() {
        RaceResult existing = RaceResult.builder()
                .resultId(resultId).race(race).entry(entry)
                .currentVersionNo(1)
                .officialityStatus(OfficialityStatus.AMENDED)
                .finishPosition(5)
                .build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));
        when(raceResultRepository.findByEntry_EntryId(entryId)).thenReturn(Optional.of(existing));
        when(raceResultRepository.save(any(RaceResult.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(jockeyAssignmentRepository.findAcceptedByEntryIds(any())).thenReturn(List.of());

        service.recordResults(userId, raceId, recordReq());

        // same row reused, overwritten back to PROVISIONAL with new values
        assertThat(existing.getFinishPosition()).isEqualTo(1);
        assertThat(existing.getOfficialityStatus()).isEqualTo(OfficialityStatus.PROVISIONAL);
    }

    @Test
    void record_entryNotInRace_mismatch() {
        Race otherRace = Race.builder().raceId(UUID.randomUUID()).build();
        entry.setRace(otherRace);

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.recordResults(userId, raceId, recordReq()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESULT_ENTRY_RACE_MISMATCH);
    }

    @Test
    void record_raceNotFound() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordResults(userId, raceId, recordReq()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RACE_NOT_FOUND);
    }

    // ── get (order + winningTimeMs + fractions) ──

    @Test
    void get_ordersByPosition_computesWinningTime_mapsFractions() {
        race.getFractions().add(RaceFraction.builder().splitNo(2).timeMs(46800L).build());
        race.getFractions().add(RaceFraction.builder().splitNo(1).timeMs(23100L).build());
        race.getFractions().add(RaceFraction.builder().splitNo(3).timeMs(70200L).build());
        race.setWindSpeedKph(new BigDecimal("12.40"));
        race.setPhotofinishUrl("/api/v1/files/pf-1.jpg");

        RaceResult r1 = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry)
                .finishPosition(1).finishTimeMs(91230L).lengthsBehind(BigDecimal.ZERO)
                .officialityStatus(OfficialityStatus.PROVISIONAL).build();
        RaceResult r2 = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry2)
                .finishPosition(2).finishTimeMs(91500L).lengthsBehind(new BigDecimal("1.80"))
                .officialityStatus(OfficialityStatus.PROVISIONAL).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        // intentionally out of order to prove sorting
        when(raceResultRepository.findByRaceIdWithEntry(raceId)).thenReturn(List.of(r2, r1));
        when(jockeyAssignmentRepository.findAcceptedByEntryIds(any())).thenReturn(List.of());

        RaceResultsResponse resp = service.getResults(raceId);

        assertThat(resp.getRaceId()).isEqualTo(raceId);
        assertThat(resp.getOfficialityStatus()).isEqualTo(OfficialityStatus.PROVISIONAL);
        assertThat(resp.getWinningTimeMs()).isEqualTo(91230L);
        assertThat(resp.getTrackCondition()).isEqualTo("FAST");
        assertThat(resp.getTrackBias()).isEqualTo("NONE");
        assertThat(resp.getWindSpeedKph()).isEqualByComparingTo("12.40");
        assertThat(resp.getPhotofinishUrl()).isEqualTo("/api/v1/files/pf-1.jpg");
        // fractions ordered by split_no, rendered as strings
        assertThat(resp.getFractions()).containsExactly("23100", "46800", "70200");
        // finish order sorted ascending by position
        assertThat(resp.getOrder()).extracting(RaceResultsResponse.OrderRow::getFinishPosition)
                .containsExactly(1, 2);
        assertThat(resp.getOrder().get(0).getHorseName()).isEqualTo("Thunderbolt");
        assertThat(resp.getOrder().get(0).getWeightCarriedLbs()).isEqualTo(126);
        assertThat(resp.getOrder().get(0).getOdds()).isEqualTo("5/2");
    }

    @Test
    void get_allOfficial_statusOfficial() {
        RaceResult r1 = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry)
                .finishPosition(1).finishTimeMs(91230L)
                .officialityStatus(OfficialityStatus.OFFICIAL).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRaceIdWithEntry(raceId)).thenReturn(List.of(r1));
        when(jockeyAssignmentRepository.findAcceptedByEntryIds(any())).thenReturn(List.of());

        RaceResultsResponse resp = service.getResults(raceId);
        assertThat(resp.getOfficialityStatus()).isEqualTo(OfficialityStatus.OFFICIAL);
    }

    // ── edit (writes version + increments currentVersionNo + AMENDED) ──

    @Test
    void update_snapshotsVersion_incrementsVersionNo_setsAmended() {
        RaceResult result = RaceResult.builder()
                .resultId(resultId).race(race).entry(entry)
                .currentVersionNo(1)
                .finishPosition(1).finishTimeMs(91230L).lengthsBehind(BigDecimal.ZERO)
                .score(new BigDecimal("100.0"))
                .officialityStatus(OfficialityStatus.PROVISIONAL)
                .build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceResultRepository.findById(resultId)).thenReturn(Optional.of(result));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(certifier));
        when(raceResultRepository.save(any(RaceResult.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateResultRequest req = new UpdateResultRequest(2, 91500L, new BigDecimal("1.8"), "Photofinish re-read.");
        UpdateResultResponse resp = service.updateResult(userId, raceId, resultId, req);

        // a version snapshot is written BEFORE applying, capturing the OLD values
        ArgumentCaptor<RaceResultVersion> vCaptor = ArgumentCaptor.forClass(RaceResultVersion.class);
        verify(raceResultVersionRepository).save(vCaptor.capture());
        RaceResultVersion version = vCaptor.getValue();
        assertThat(version.getVersionNo()).isEqualTo(1);
        assertThat(version.getFinishPosition()).isEqualTo(1);          // OLD position
        assertThat(version.getFinishTimeMs()).isEqualTo(91230L);       // OLD time
        assertThat(version.getOfficialityStatus()).isEqualTo("PROVISIONAL");
        assertThat(version.getChangedBy()).isEqualTo(certifier);
        assertThat(version.getChangeReason()).isEqualTo("Photofinish re-read.");

        // result now carries the NEW values, bumped version, AMENDED
        assertThat(resp.getFinishPosition()).isEqualTo(2);
        assertThat(resp.getFinishTimeMs()).isEqualTo(91500L);
        assertThat(resp.getLengthsBehind()).isEqualByComparingTo("1.8");
        assertThat(resp.getCurrentVersionNo()).isEqualTo(2);
        assertThat(resp.getOfficialityStatus()).isEqualTo(OfficialityStatus.AMENDED);
    }

    @Test
    void update_resultNotFound() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceResultRepository.findById(resultId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateResult(userId, raceId, resultId,
                new UpdateResultRequest(2, null, null, "x")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESULT_NOT_FOUND);
    }

    // ── certify (OFFICIAL + guard on acknowledgeInquiriesResolved) ──

    @Test
    void certify_acknowledgeFalse_throwsInquiriesUnresolved() {
        assertThatThrownBy(() -> service.certify(userId, raceId,
                new CertifyResultsRequest("4821", false, "report")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESULT_INQUIRIES_UNRESOLVED);

        verify(raceRepository, never()).save(any());
    }

    @Test
    void certify_flipsResultsAndRaceToOfficial() {
        RaceResult r1 = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry)
                .officialityStatus(OfficialityStatus.PROVISIONAL).build();
        RaceResult r2 = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry2)
                .officialityStatus(OfficialityStatus.AMENDED).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(certifier));
        when(raceResultRepository.findByRaceIdWithEntry(raceId)).thenReturn(List.of(r1, r2));
        when(raceResultRepository.save(any(RaceResult.class))).thenAnswer(inv -> inv.getArgument(0));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        CertifyResultsResponse resp = service.certify(userId, raceId,
                new CertifyResultsRequest("4821", true, "Stewards report text."));

        assertThat(r1.getOfficialityStatus()).isEqualTo(OfficialityStatus.OFFICIAL);
        assertThat(r2.getOfficialityStatus()).isEqualTo(OfficialityStatus.OFFICIAL);
        assertThat(r1.getApprovedBy()).isEqualTo(certifier);
        assertThat(r1.getPublishedAt()).isNotNull();

        assertThat(race.getStatus()).isEqualTo(RaceStatus.OFFICIAL);
        assertThat(race.getCertifiedByUserId()).isEqualTo(userId);
        assertThat(race.getCertifiedAt()).isNotNull();
        assertThat(race.getStewardsReport()).isEqualTo("Stewards report text.");

        assertThat(resp.getRaceStatus()).isEqualTo(RaceStatus.OFFICIAL);
        assertThat(resp.getOfficialityStatus()).isEqualTo(OfficialityStatus.OFFICIAL);
        assertThat(resp.getCertifiedByUserId()).isEqualTo(userId);
        assertThat(resp.getCertifiedByName()).isEqualTo("Chief Steward J. Lim");
        assertThat(resp.getPublishedAt()).isNotNull();
        assertThat(resp.getOpenInquiries()).isZero();
    }

    @Test
    void certify_nullPrincipal_unauthenticated() {
        assertThatThrownBy(() -> service.certify(null, raceId,
                new CertifyResultsRequest("4821", true, "report")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }
}

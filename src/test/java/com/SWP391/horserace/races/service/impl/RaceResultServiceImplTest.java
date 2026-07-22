package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.assignments.service.JockeyFeeGate;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.jockeys.entity.JockeyProfile;
import com.SWP391.horserace.jockeys.repository.JockeyProfileRepository;
import com.SWP391.horserace.notifications.service.NotificationService;
import com.SWP391.horserace.penalties.entity.Penalty;
import com.SWP391.horserace.penalties.entity.PenaltyStatus;
import com.SWP391.horserace.penalties.entity.PenaltyType;
import com.SWP391.horserace.prizes.entity.Prize;
import com.SWP391.horserace.prizes.repository.PrizeRepository;
import com.SWP391.horserace.races.entity.PrizeDistributionItem;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import com.SWP391.horserace.wallets.service.HouseWalletService;
import com.SWP391.horserace.wallets.service.WalletService;
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
import com.SWP391.horserace.races.entity.RaceEntryStatus;
import com.SWP391.horserace.races.entity.RaceResultVersion;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.races.repository.RaceResultVersionRepository;
import com.SWP391.horserace.races.dto.SubmitReportRequest;
import com.SWP391.horserace.races.dto.SubmitReportResponse;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.staffing.service.RefereeSubmissionCodeService;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.violations.dto.CreateViolationRequest;
import com.SWP391.horserace.violations.dto.ViolationDetailResponse;
import com.SWP391.horserace.violations.entity.InfractionType;
import com.SWP391.horserace.violations.service.ViolationService;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock NotificationService notificationService;
    @Mock RefereeSubmissionCodeService refereeSubmissionCodeService;
    @Mock ViolationService violationService;
    @Mock RegistrationRepository registrationRepository;
    @Mock com.SWP391.horserace.penalties.repository.PenaltyRepository penaltyRepository;
    @Mock PrizeRepository prizeRepository;
    @Mock JockeyProfileRepository jockeyProfileRepository;
    @Mock WalletLedgerService walletLedgerService;
    @Mock WalletService walletService;
    @Mock HouseWalletService houseWalletService;
    @Mock JockeyFeeGate jockeyFeeGate;

    private RaceResultServiceImpl service;

    private final UUID raceId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();
    private final UUID entryId2 = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID resultId = UUID.randomUUID();

    @Mock com.SWP391.horserace.staffing.repository.RefereeAssignmentRepository refereeAssignmentRepository;

    private Race race;
    private RaceEntry entry;
    private RaceEntry entry2;
    private User certifier;

    @BeforeEach
    void setUp() {
        service = new RaceResultServiceImpl(
                raceRepository, raceEntryRepository, raceResultRepository,
                raceResultVersionRepository, jockeyAssignmentRepository, userRepository,
                notificationService, refereeSubmissionCodeService, violationService,
                registrationRepository, penaltyRepository, prizeRepository, jockeyProfileRepository,
                refereeAssignmentRepository, walletLedgerService, walletService, houseWalletService,
                jockeyFeeGate);
        // #8: most getResults tests build a tournament-less race → repo is not queried; keep this
        // lenient so the stub is harmless where the NOT-ENTERED path is never reached.
        lenient().when(registrationRepository.findApprovedNotEnteredInRace(any(), any()))
                .thenReturn(List.of());

        race = Race.builder().raceId(raceId).status(RaceStatus.FINISHED)
                .trackCondition("FAST").trackBias("NONE").build();

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

        certifier = User.builder().userId(userId).fullName("Chief Steward J. Lim")
                .role(com.SWP391.horserace.roles.entity.Role.builder().roleCode("ADMIN").build())
                .build();
    }

    private RecordResultsRequest recordReq() {
        return new RecordResultsRequest(List.of(
                new RecordResultsRequest.ResultRow(entryId, 1, 91230L, BigDecimal.ZERO, new BigDecimal("100.0"))),
                "4821");
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

    // ── FR-01: duplicate finish-position dedup (loaded under the pessimistic lock) ──

    @Test
    void recordResults_rejectsDuplicateFinishPositionWithinPayload() {
        // Two rows in one payload share finishPosition 1 → reject, persist nothing.
        RecordResultsRequest req = new RecordResultsRequest(List.of(
                new RecordResultsRequest.ResultRow(entryId, 1, 91230L, BigDecimal.ZERO, new BigDecimal("100.0")),
                new RecordResultsRequest.ResultRow(entryId2, 1, 91500L, BigDecimal.ONE, new BigDecimal("90.0"))),
                "4821");

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRace_RaceId(raceId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.recordResults(userId, raceId, req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_FINISH_POSITION);

        verify(raceResultRepository, never()).save(any());
    }

    @Test
    void recordResults_rejectsFinishPositionClashWithPersistedRow() {
        // Payload puts entry-B at position 1, but entry-A (not in this payload) already holds position 1.
        RaceResult persistedA = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry)
                .finishPosition(1).officialityStatus(OfficialityStatus.PROVISIONAL).build();

        RecordResultsRequest req = new RecordResultsRequest(List.of(
                new RecordResultsRequest.ResultRow(entryId2, 1, 91500L, BigDecimal.ONE, new BigDecimal("90.0"))),
                "4821");

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRace_RaceId(raceId)).thenReturn(List.of(persistedA));

        assertThatThrownBy(() -> service.recordResults(userId, raceId, req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_FINISH_POSITION);

        verify(raceResultRepository, never()).save(any());
    }

    @Test
    void recordResults_allowsDistinctFinishPositions() {
        UUID entryId3 = UUID.randomUUID();
        RaceEntry entry3 = RaceEntry.builder()
                .entryId(entryId3).race(race)
                .registration(TournamentRegistration.builder()
                        .horse(Horse.builder().horseId(UUID.randomUUID()).name("Dawn Raider").build()).build())
                .entryNo(5).build();

        RecordResultsRequest req = new RecordResultsRequest(List.of(
                new RecordResultsRequest.ResultRow(entryId, 1, 91230L, BigDecimal.ZERO, new BigDecimal("100.0")),
                new RecordResultsRequest.ResultRow(entryId2, 2, 91500L, BigDecimal.ONE, new BigDecimal("90.0")),
                new RecordResultsRequest.ResultRow(entryId3, 3, 91800L, new BigDecimal("2.0"), new BigDecimal("80.0"))),
                "4821");

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRace_RaceId(raceId)).thenReturn(List.of());
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));
        when(raceEntryRepository.findByIdWithDetails(entryId2)).thenReturn(Optional.of(entry2));
        when(raceEntryRepository.findByIdWithDetails(entryId3)).thenReturn(Optional.of(entry3));
        when(raceResultRepository.findByEntry_EntryId(any())).thenReturn(Optional.empty());
        when(raceResultRepository.save(any(RaceResult.class))).thenAnswer(inv -> {
            RaceResult r = inv.getArgument(0);
            if (r.getResultId() == null) r.setResultId(UUID.randomUUID());
            return r;
        });
        lenient().when(jockeyAssignmentRepository.findAcceptedByEntryIds(any())).thenReturn(List.of());

        List<ResultRowResponse> rows = service.recordResults(userId, raceId, req);

        assertThat(rows).hasSize(3);
        verify(raceResultRepository, times(3)).save(any(RaceResult.class));
    }

    @Test
    void updateResult_rejectsFinishPositionCollidingWithAnotherResult() {
        // Target result (entry A) is being moved to position 1, but another result (entry B) already has 1.
        RaceResult target = RaceResult.builder()
                .resultId(resultId).race(race).entry(entry)
                .currentVersionNo(1).finishPosition(5)
                .officialityStatus(OfficialityStatus.PROVISIONAL).build();
        RaceResult other = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry2)
                .finishPosition(1).officialityStatus(OfficialityStatus.PROVISIONAL).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceResultRepository.findById(resultId)).thenReturn(Optional.of(target));
        when(raceResultRepository.findByRace_RaceId(raceId)).thenReturn(List.of(target, other));

        assertThatThrownBy(() -> service.updateResult(userId, raceId, resultId,
                new UpdateResultRequest(1, null, null, "Re-read")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_FINISH_POSITION);

        verify(raceResultRepository, never()).save(any());
        verify(raceResultVersionRepository, never()).save(any());
    }

    @Test
    void updateResult_allowsSamePositionOnSameResult() {
        // Re-submitting the row's OWN current position is not a self-collision.
        RaceResult target = RaceResult.builder()
                .resultId(resultId).race(race).entry(entry)
                .currentVersionNo(1).finishPosition(1)
                .officialityStatus(OfficialityStatus.PROVISIONAL).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceResultRepository.findById(resultId)).thenReturn(Optional.of(target));
        when(raceResultRepository.findByRace_RaceId(raceId)).thenReturn(List.of(target));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(certifier));
        when(raceResultRepository.save(any(RaceResult.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateResultResponse resp = service.updateResult(userId, raceId, resultId,
                new UpdateResultRequest(1, null, null, "No change to position"));

        assertThat(resp.getFinishPosition()).isEqualTo(1);
        verify(raceResultRepository).save(any(RaceResult.class));
    }

    @Test
    void recordResults_and_updateResult_serializeViaRaceRowLock() {
        // The dedup path must acquire the PESSIMISTIC_WRITE lock on the PARENT race row (which always
        // exists, so it serializes even when no result rows exist yet — a FOR UPDATE over race_result
        // rows can't lock not-yet-inserted rows), then read the committed results.
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        lenient().when(raceRepository.findByRaceIdForUpdate(raceId)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRace_RaceId(raceId)).thenReturn(List.of());
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));
        when(raceResultRepository.findByEntry_EntryId(entryId)).thenReturn(Optional.empty());
        when(raceResultRepository.save(any(RaceResult.class))).thenAnswer(inv -> {
            RaceResult r = inv.getArgument(0);
            if (r.getResultId() == null) r.setResultId(resultId);
            return r;
        });
        lenient().when(jockeyAssignmentRepository.findAcceptedByEntryIds(any())).thenReturn(List.of());

        service.recordResults(userId, raceId, recordReq());
        verify(raceRepository).findByRaceIdForUpdate(raceId);

        RaceResult target = RaceResult.builder()
                .resultId(resultId).race(race).entry(entry)
                .currentVersionNo(1).finishPosition(1)
                .officialityStatus(OfficialityStatus.PROVISIONAL).build();
        when(raceResultRepository.findById(resultId)).thenReturn(Optional.of(target));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(certifier));

        service.updateResult(userId, raceId, resultId, new UpdateResultRequest(2, null, null, "x"));
        verify(raceRepository, times(2)).findByRaceIdForUpdate(raceId);
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

    // ---------- Req 18: a referee may certify, but only their own race ----------

    @Test
    void certify_refereeConfirmedOnThisRace_isAllowed() {
        User referee = User.builder().userId(userId).fullName("Phan Công Lý")
                .role(com.SWP391.horserace.roles.entity.Role.builder().roleCode("RACE_REFEREE").build())
                .build();
        RaceResult r1 = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry)
                .officialityStatus(OfficialityStatus.PROVISIONAL).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(referee));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusIn(
                raceId, userId, com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.OFFICIATING))
                .thenReturn(true);
        when(raceResultRepository.findByRaceIdWithEntry(raceId)).thenReturn(List.of(r1));
        when(raceResultRepository.save(any(RaceResult.class))).thenAnswer(inv -> inv.getArgument(0));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        service.certify(userId, raceId, new CertifyResultsRequest("4821", true, "OK."));

        assertThat(race.getStatus()).isEqualTo(RaceStatus.OFFICIAL);
        assertThat(r1.getOfficialityStatus()).isEqualTo(OfficialityStatus.OFFICIAL);
    }

    @Test
    void certify_refereeNotAssignedToThisRace_isRejected() {
        User referee = User.builder().userId(userId).fullName("Tạ Nghiêm Minh")
                .role(com.SWP391.horserace.roles.entity.Role.builder().roleCode("RACE_REFEREE").build())
                .build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(referee));
        when(refereeAssignmentRepository.existsByRace_RaceIdAndReferee_UserIdAndStatusIn(
                raceId, userId, com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.OFFICIATING))
                .thenReturn(false);

        assertThatThrownBy(() -> service.certify(userId, raceId,
                new CertifyResultsRequest("4821", true, "OK.")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(ErrorCode.REFEREE_NOT_ASSIGNED.getMessage());

        // the race must be untouched — a rejected certify cannot half-publish
        assertThat(race.getStatus()).isEqualTo(RaceStatus.FINISHED);
    }

    @Test
    void certify_creditsPrizesToOwnerAndJockey() {
        // Race whose purse pays 100,000 for 1st place.
        UUID prizeRaceId = UUID.randomUUID();
        Race prizeRace = Race.builder().raceId(prizeRaceId).status(RaceStatus.FINISHED)
                .raceCode("RACE-PRZ")
                .prizeDistribution(new java.util.ArrayList<>(List.of(
                        PrizeDistributionItem.builder().place("1st").amount(new BigDecimal("100000")).build())))
                .build();

        UUID ownerId = UUID.randomUUID();
        UUID jockeyId = UUID.randomUUID();
        User owner = User.builder().userId(ownerId).build();
        User jockeyUser = User.builder().userId(jockeyId).fullName("A. Rider").build();
        Horse h = Horse.builder().horseId(UUID.randomUUID()).name("Winner").build();
        UUID winEntryId = UUID.randomUUID();
        RaceEntry winEntry = RaceEntry.builder()
                .entryId(winEntryId).race(prizeRace)
                .registration(TournamentRegistration.builder().horse(h).owner(owner).build())
                .prizeEarned(BigDecimal.ZERO)
                .build();
        RaceResult winResult = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(prizeRace).entry(winEntry)
                .finishPosition(1).officialityStatus(OfficialityStatus.PROVISIONAL).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(prizeRaceId)).thenReturn(Optional.of(prizeRace));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(certifier));
        when(raceResultRepository.findByRaceIdWithEntry(prizeRaceId)).thenReturn(List.of(winResult));
        when(raceResultRepository.save(any(RaceResult.class))).thenAnswer(inv -> inv.getArgument(0));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jockeyAssignmentRepository.findAcceptedByEntryIds(any()))
                .thenReturn(List.of(JockeyAssignment.builder().entry(winEntry).jockey(jockeyUser).build()));
        when(jockeyProfileRepository.findById(jockeyId))
                .thenReturn(Optional.of(JockeyProfile.builder().prizePercent(new BigDecimal("20")).build()));
        UUID houseId = UUID.randomUUID();
        when(houseWalletService.houseUserId()).thenReturn(houseId);

        service.certify(userId, prizeRaceId, new CertifyResultsRequest("4821", true, "report"));

        // Jockey 20% = 20,000; owner remainder = 80,000; entry marked as having earned the full amount.
        verify(walletService).getOrCreateWallet(ownerId);
        verify(walletService).getOrCreateWallet(jockeyId);

        // Every prize credit has a matching house debit — PRIZE is no longer minted from nothing.
        verify(walletLedgerService).applyEntry(eq(houseId), eq(EntryType.DEBIT), eq(TxnCategory.PRIZE),
                argThat(a -> a.compareTo(new BigDecimal("80000")) == 0), eq("RACE_ENTRY"), eq(winEntryId));
        verify(walletLedgerService).applyEntry(eq(houseId), eq(EntryType.DEBIT), eq(TxnCategory.PRIZE),
                argThat(a -> a.compareTo(new BigDecimal("20000")) == 0), eq("RACE_ENTRY"), eq(winEntryId));
        verify(walletLedgerService).applyEntry(eq(ownerId), eq(EntryType.CREDIT), eq(TxnCategory.PRIZE),
                argThat(a -> a.compareTo(new BigDecimal("80000")) == 0), eq("RACE_ENTRY"), eq(winEntryId));
        verify(walletLedgerService).applyEntry(eq(jockeyId), eq(EntryType.CREDIT), eq(TxnCategory.PRIZE),
                argThat(a -> a.compareTo(new BigDecimal("20000")) == 0), eq("RACE_ENTRY"), eq(winEntryId));
        verify(prizeRepository, times(2)).save(any(Prize.class));
        assertThat(winEntry.getPrizeEarned()).isEqualByComparingTo("100000");
    }

    @Test
    void certify_alreadyEarnedPrize_notPaidTwice() {
        // An entry that already earned a prize must be skipped (idempotency belt).
        UUID prizeRaceId = UUID.randomUUID();
        Race prizeRace = Race.builder().raceId(prizeRaceId).status(RaceStatus.FINISHED).raceCode("RACE-PRZ2")
                .prizeDistribution(new java.util.ArrayList<>(List.of(
                        PrizeDistributionItem.builder().place("1st").amount(new BigDecimal("100000")).build())))
                .build();
        User owner = User.builder().userId(UUID.randomUUID()).build();
        Horse h = Horse.builder().horseId(UUID.randomUUID()).name("Paid").build();
        RaceEntry paidEntry = RaceEntry.builder()
                .entryId(UUID.randomUUID()).race(prizeRace)
                .registration(TournamentRegistration.builder().horse(h).owner(owner).build())
                .prizeEarned(new BigDecimal("100000"))
                .build();
        RaceResult r = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(prizeRace).entry(paidEntry)
                .finishPosition(1).officialityStatus(OfficialityStatus.PROVISIONAL).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(prizeRaceId)).thenReturn(Optional.of(prizeRace));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(certifier));
        when(raceResultRepository.findByRaceIdWithEntry(prizeRaceId)).thenReturn(List.of(r));
        when(raceResultRepository.save(any(RaceResult.class))).thenAnswer(inv -> inv.getArgument(0));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(jockeyAssignmentRepository.findAcceptedByEntryIds(any())).thenReturn(List.of());

        service.certify(userId, prizeRaceId, new CertifyResultsRequest("4821", true, "report"));

        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
        verify(prizeRepository, never()).save(any(Prize.class));
    }

    @Test
    void certify_appliesDisqualification_reranks() {
        // Two finishers; the leader is disqualified on certify → the runner-up is promoted to 1st.
        RaceResult r1 = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry)
                .finishPosition(1).finishTimeMs(60000L)
                .officialityStatus(OfficialityStatus.PROVISIONAL).build();
        RaceResult r2 = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry2)
                .finishPosition(2).finishTimeMs(60500L)
                .officialityStatus(OfficialityStatus.PROVISIONAL).build();
        Penalty dq = Penalty.builder()
                .race(race).entry(entry)
                .penaltyType(PenaltyType.DISQUALIFICATION)
                .status(PenaltyStatus.ISSUED).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(certifier));
        when(raceResultRepository.findByRaceIdWithEntry(raceId)).thenReturn(List.of(r1, r2));
        when(raceResultRepository.save(any(RaceResult.class))).thenAnswer(inv -> inv.getArgument(0));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));
        when(penaltyRepository.findByRace_RaceId(raceId)).thenReturn(List.of(dq));

        service.certify(userId, raceId, new CertifyResultsRequest("4821", true, "report"));

        assertThat(entry.getStatus()).isEqualTo(RaceEntryStatus.DISQUALIFIED);
        assertThat(r1.getFinishPosition()).isNull();       // disqualified → no place
        assertThat(r2.getFinishPosition()).isEqualTo(1);   // promoted to 1st
        assertThat(dq.getStatus()).isEqualTo(PenaltyStatus.UPHELD);
    }

    @Test
    void certify_appliesTimePenalty_reranks() {
        // Leader gets a +1s time penalty that drops them behind the runner-up.
        RaceResult r1 = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry)
                .finishPosition(1).finishTimeMs(60000L)
                .officialityStatus(OfficialityStatus.PROVISIONAL).build();
        RaceResult r2 = RaceResult.builder()
                .resultId(UUID.randomUUID()).race(race).entry(entry2)
                .finishPosition(2).finishTimeMs(60500L)
                .officialityStatus(OfficialityStatus.PROVISIONAL).build();
        Penalty tp = Penalty.builder()
                .race(race).entry(entry)
                .penaltyType(PenaltyType.TIME_PENALTY).timePenaltyMs(1000L)
                .status(PenaltyStatus.ISSUED).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(certifier));
        when(raceResultRepository.findByRaceIdWithEntry(raceId)).thenReturn(List.of(r1, r2));
        when(raceResultRepository.save(any(RaceResult.class))).thenAnswer(inv -> inv.getArgument(0));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));
        when(penaltyRepository.findByRace_RaceId(raceId)).thenReturn(List.of(tp));

        service.certify(userId, raceId, new CertifyResultsRequest("4821", true, "report"));

        assertThat(r1.getFinishTimeMs()).isEqualTo(61000L); // +1s
        assertThat(r2.getFinishPosition()).isEqualTo(1);    // now faster → 1st
        assertThat(r1.getFinishPosition()).isEqualTo(2);
    }

    @Test
    void certify_nullPrincipal_unauthenticated() {
        assertThatThrownBy(() -> service.certify(null, raceId,
                new CertifyResultsRequest("4821", true, "report")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }


    // ── CN3: submitReport (OTP-gated combined publish) ──

    private User refereeUser() {
        return User.builder().userId(userId).fullName("Ref R. Cole").build(); // role null → treated as referee
    }

    private User adminUser() {
        return User.builder().userId(userId).fullName("Admin A. Vale")
                .role(Role.builder().roleCode("ADMIN").build()).build();
    }

    private SubmitReportRequest submitReq(String otp) {
        return SubmitReportRequest.builder()
                .otp(otp)
                .results(List.of(new RecordResultsRequest.ResultRow(
                        entryId, 1, 91230L, BigDecimal.ZERO, new BigDecimal("100.0"))))
                .violations(List.of(new CreateViolationRequest(
                        entryId, InfractionType.INTERFERENCE, null, 2, 1000L, "Bumped", null, null, null)))
                .build();
    }

    private void stubUpsertAndJockeys() {
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));
        when(raceResultRepository.findByEntry_EntryId(entryId)).thenReturn(Optional.empty());
        when(raceResultRepository.save(any(RaceResult.class))).thenAnswer(inv -> {
            RaceResult r = inv.getArgument(0);
            if (r.getResultId() == null) r.setResultId(resultId);
            return r;
        });
        lenient().when(jockeyAssignmentRepository.findAcceptedByEntryIds(any())).thenReturn(List.of());
    }

    @Test
    void submitReport_validOtp_publishesResultsAndViolations_usesTrusted_writesAudit() {
        UUID violationId = UUID.randomUUID();
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(refereeUser()));
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        stubUpsertAndJockeys();
        when(raceResultRepository.markRefereeSubmitted(any(), any(), any())).thenReturn(1);
        when(violationService.createViolationTrusted(any(), any(), any()))
                .thenReturn(ViolationDetailResponse.builder().violationId(violationId).build());

        SubmitReportResponse resp = service.submitReport(userId, raceId, submitReq("123456"));

        // OTP validated + consumed
        verify(refereeSubmissionCodeService).validateAndConsume(userId, raceId, "123456");
        // one-time lock stamped
        verify(raceResultRepository).markRefereeSubmitted(any(), any(), any());
        // RT-CRITICAL-1: violations go through the TRUSTED path (no refCode gate)
        verify(violationService).createViolationTrusted(userId, raceId,
                submitReq("123456").getViolations().get(0));
        verify(violationService, never()).createViolation(any(), any(), any());
        // FR-21 audit snapshot written
        ArgumentCaptor<RaceResultVersion> vCaptor = ArgumentCaptor.forClass(RaceResultVersion.class);
        verify(raceResultVersionRepository).save(vCaptor.capture());
        assertThat(vCaptor.getValue().getChangeReason()).isEqualTo("Referee report published");

        assertThat(resp.getResults()).hasSize(1);
        assertThat(resp.getViolationIds()).containsExactly(violationId);
        assertThat(resp.getSubmittedAt()).isNotNull();
    }

    @Test
    void submitReport_invalidOtp_throws_noWrite() {
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(refereeUser()));
        org.mockito.Mockito.doThrow(new AppException(ErrorCode.REFEREE_CODE_INVALID))
                .when(refereeSubmissionCodeService).validateAndConsume(userId, raceId, "000000");

        assertThatThrownBy(() -> service.submitReport(userId, raceId, submitReq("000000")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFEREE_CODE_INVALID);

        verify(raceResultRepository, never()).save(any());
        verify(raceResultRepository, never()).markRefereeSubmitted(any(), any(), any());
        verify(violationService, never()).createViolationTrusted(any(), any(), any());
    }

    @Test
    void submitReport_raceNotFinished_throws() {
        Race scheduled = Race.builder().raceId(raceId).status(RaceStatus.SCHEDULED).build();
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(refereeUser()));
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(scheduled));

        assertThatThrownBy(() -> service.submitReport(userId, raceId, submitReq("123456")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RACE_NOT_FINISHED);

        verify(raceResultRepository, never()).markRefereeSubmitted(any(), any(), any());
    }

    @Test
    void submitReport_secondRefereeSubmit_throwsAlreadySubmitted() {
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(refereeUser()));
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        // Race already has a referee-published result → the authoritative guard blocks BEFORE upsert.
        when(raceResultRepository.existsByRace_RaceIdAndRefereeSubmittedAtIsNotNull(raceId)).thenReturn(true);

        assertThatThrownBy(() -> service.submitReport(userId, raceId, submitReq("123456")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESULT_ALREADY_SUBMITTED);

        verify(raceResultRepository, never()).save(any());
        verify(violationService, never()).createViolationTrusted(any(), any(), any());
    }

    @Test
    void submitReport_emptyResults_throwsResultsRequired() {
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(refereeUser()));
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        SubmitReportRequest empty = SubmitReportRequest.builder().otp("123456")
                .results(List.of()).violations(List.of()).build();

        assertThatThrownBy(() -> service.submitReport(userId, raceId, empty))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_RESULTS_REQUIRED);

        verify(raceResultRepository, never()).save(any());
    }

    @Test
    void submitReport_adminAfterRefereeLock_allowed() {
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(adminUser()));
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        stubUpsertAndJockeys();
        when(raceResultRepository.markRefereeSubmitted(any(), any(), any())).thenReturn(0); // already locked
        lenient().when(violationService.createViolationTrusted(any(), any(), any()))
                .thenReturn(ViolationDetailResponse.builder().violationId(UUID.randomUUID()).build());

        SubmitReportResponse resp = service.submitReport(userId, raceId, submitReq("ignored"));

        // ADMIN on /report skips the OTP and is not blocked by the one-time lock
        verify(refereeSubmissionCodeService, never()).validateAndConsume(any(), any(), any());
        assertThat(resp.getSubmittedAt()).isNotNull();
    }

    @Test
    void adminOverridePublish_noOtp_publishes() {
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(adminUser()));
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        stubUpsertAndJockeys();
        when(raceResultRepository.markRefereeSubmitted(any(), any(), any())).thenReturn(1);
        lenient().when(violationService.createViolationTrusted(any(), any(), any()))
                .thenReturn(ViolationDetailResponse.builder().violationId(UUID.randomUUID()).build());

        SubmitReportResponse resp = service.adminOverridePublish(userId, raceId, submitReq(null));

        verify(refereeSubmissionCodeService, never()).validateAndConsume(any(), any(), any());
        assertThat(resp.getResults()).hasSize(1);
        assertThat(resp.getSubmittedAt()).isNotNull();
    }


    // ── #8: registered-but-not-entered on the result sheet ──

    @Test
    void getResults_includesRegisteredNotEntered() {
        UUID tid = UUID.randomUUID();
        UUID regId = UUID.randomUUID();
        UUID horseId = UUID.randomUUID();
        Race raceWithT = Race.builder().raceId(raceId).status(RaceStatus.FINISHED)
                .tournament(Tournament.builder().tournamentId(tid).build()).build();
        Horse regHorse = Horse.builder().horseId(horseId).name("Absent Star").build();
        User regOwner = User.builder().userId(UUID.randomUUID()).fullName("Olga Owner").build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(regId).registrationCode("REG00042")
                .horse(regHorse).owner(regOwner).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(raceWithT));
        when(raceResultRepository.findByRaceIdWithEntry(raceId)).thenReturn(List.of());
        when(registrationRepository.findApprovedNotEnteredInRace(tid, raceId)).thenReturn(List.of(reg));

        RaceResultsResponse resp = service.getResults(raceId);

        assertThat(resp.getRegisteredNotEntered()).hasSize(1);
        RaceResultsResponse.RegisteredNotEnteredRow row = resp.getRegisteredNotEntered().get(0);
        assertThat(row.getRegistrationId()).isEqualTo(regId);
        assertThat(row.getRegistrationCode()).isEqualTo("REG00042");
        assertThat(row.getHorseId()).isEqualTo(horseId);
        assertThat(row.getHorseName()).isEqualTo("Absent Star");
        assertThat(row.getOwnerName()).isEqualTo("Olga Owner");
    }

    @Test
    void getResults_emptyWhenNoTournament() {
        // The shared race has no tournament → the NOT-ENTERED repo is never queried.
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRaceIdWithEntry(raceId)).thenReturn(List.of());

        RaceResultsResponse resp = service.getResults(raceId);

        assertThat(resp.getRegisteredNotEntered()).isEmpty();
        verify(registrationRepository, never()).findApprovedNotEnteredInRace(any(), any());
    }
}

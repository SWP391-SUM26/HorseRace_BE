package com.SWP391.horserace.inspections.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.inspections.dto.InspectionListItemResponse;
import com.SWP391.horserace.inspections.dto.InspectionRequest;
import com.SWP391.horserace.inspections.dto.InspectionResponse;
import com.SWP391.horserace.inspections.dto.SubmitAllRequest;
import com.SWP391.horserace.inspections.dto.SubmitAllResponse;
import com.SWP391.horserace.inspections.entity.InspectionStatus;
import com.SWP391.horserace.inspections.entity.RaceEntryInspection;
import com.SWP391.horserace.inspections.repository.RaceEntryInspectionRepository;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServiceImplTest {

    @Mock RaceEntryInspectionRepository inspectionRepository;
    @Mock RaceRepository raceRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock JockeyAssignmentRepository jockeyAssignmentRepository;
    @Mock UserRepository userRepository;

    private InspectionServiceImpl service;

    private final UUID raceId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID horseId = UUID.randomUUID();

    private Race race;
    private RaceEntry entry;
    private User inspector;

    @BeforeEach
    void setUp() {
        service = new InspectionServiceImpl(
                inspectionRepository, raceRepository, raceEntryRepository,
                jockeyAssignmentRepository, userRepository);

        race = Race.builder().raceId(raceId).build();

        Horse horse = Horse.builder().horseId(horseId).name("Thunderbolt").build();
        TournamentRegistration reg = TournamentRegistration.builder().horse(horse).build();
        entry = RaceEntry.builder()
                .entryId(entryId)
                .race(race)
                .registration(reg)
                .laneNo(3)
                .build();

        inspector = User.builder().userId(userId).fullName("Steward A. Khan").build();
    }

    private InspectionRequest req(InspectionStatus status) {
        return new InspectionRequest(entryId, true, true, 126, true, true, status, "Sound on the trot-up.");
    }

    // ── upsert ──

    @Test
    void upsert_nullPrincipal_unauthenticated() {
        assertThatThrownBy(() -> service.upsertInspection(null, raceId, req(InspectionStatus.CLEARED)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void upsert_createsInspection_setsFieldsAndInspectedBy() {
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(inspector));
        when(inspectionRepository.findByEntry_EntryId(entryId)).thenReturn(Optional.empty());
        when(inspectionRepository.save(any(RaceEntryInspection.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InspectionResponse resp = service.upsertInspection(userId, raceId, req(InspectionStatus.CLEARED));

        ArgumentCaptor<RaceEntryInspection> captor = ArgumentCaptor.forClass(RaceEntryInspection.class);
        org.mockito.Mockito.verify(inspectionRepository).save(captor.capture());
        RaceEntryInspection saved = captor.getValue();

        assertThat(saved.isHealthCertPassed()).isTrue();
        assertThat(saved.isWeightVerified()).isTrue();
        assertThat(saved.getWeightCarriedLbs()).isEqualTo(126);
        assertThat(saved.isCogginsTestPassed()).isTrue();
        assertThat(saved.isPreRaceExamPassed()).isTrue();
        assertThat(saved.getInspectionStatus()).isEqualTo(InspectionStatus.CLEARED);
        assertThat(saved.getInspectedBy()).isEqualTo(inspector);
        assertThat(saved.getInspectedAt()).isNotNull();
        assertThat(saved.getRace()).isEqualTo(race);

        assertThat(resp.getEntryId()).isEqualTo(entryId);
        assertThat(resp.getRaceId()).isEqualTo(raceId);
        assertThat(resp.getHorseId()).isEqualTo(horseId);
        assertThat(resp.getHorseName()).isEqualTo("Thunderbolt");
        assertThat(resp.getLaneNo()).isEqualTo(3);
        assertThat(resp.getInspectedByUserId()).isEqualTo(userId);
        assertThat(resp.getInspectedByName()).isEqualTo("Steward A. Khan");
    }

    @Test
    void upsert_existingInspection_updatesInPlace() {
        RaceEntryInspection existing = RaceEntryInspection.builder()
                .inspectionId(UUID.randomUUID())
                .entry(entry)
                .race(race)
                .inspectionStatus(InspectionStatus.PENDING)
                .healthCertPassed(false)
                .build();

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(inspector));
        when(inspectionRepository.findByEntry_EntryId(entryId)).thenReturn(Optional.of(existing));
        when(inspectionRepository.save(any(RaceEntryInspection.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InspectionResponse resp = service.upsertInspection(userId, raceId, req(InspectionStatus.VET_CHECK));

        // same row reused, fields overwritten
        assertThat(resp.getInspectionId()).isEqualTo(existing.getInspectionId());
        assertThat(existing.getInspectionStatus()).isEqualTo(InspectionStatus.VET_CHECK);
        assertThat(existing.isHealthCertPassed()).isTrue();
    }

    @Test
    void upsert_entryNotInRace_mismatch() {
        Race otherRace = Race.builder().raceId(UUID.randomUUID()).build();
        entry.setRace(otherRace);

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByIdWithDetails(entryId)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.upsertInspection(userId, raceId, req(InspectionStatus.CLEARED)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSPECTION_ENTRY_RACE_MISMATCH);
    }

    @Test
    void upsert_raceNotFound() {
        when(raceRepository.findById(raceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertInspection(userId, raceId, req(InspectionStatus.CLEARED)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RACE_NOT_FOUND);
    }

    // ── list (merge) ──

    @Test
    void list_mergesEntriesWithInspections_defaultsPendingWhenMissing() {
        UUID entryId2 = UUID.randomUUID();
        Horse horse2 = Horse.builder().horseId(UUID.randomUUID()).name("Night Comet").build();
        RaceEntry entry2 = RaceEntry.builder()
                .entryId(entryId2)
                .race(race)
                .registration(TournamentRegistration.builder().horse(horse2).build())
                .laneNo(4)
                .build();

        RaceEntryInspection insp1 = RaceEntryInspection.builder()
                .inspectionId(UUID.randomUUID())
                .entry(entry)
                .race(race)
                .inspectionStatus(InspectionStatus.CLEARED)
                .healthCertPassed(true)
                .weightVerified(true)
                .build();

        JockeyAssignment ja = JockeyAssignment.builder()
                .entry(entry2)
                .jockey(User.builder().userId(UUID.randomUUID()).fullName("L. Park").build())
                .build();

        when(raceRepository.existsById(raceId)).thenReturn(true);
        when(raceEntryRepository.findByRaceIdWithHorse(raceId)).thenReturn(List.of(entry, entry2));
        when(inspectionRepository.findByEntry_EntryIdIn(any())).thenReturn(List.of(insp1));
        when(jockeyAssignmentRepository.findAcceptedByEntryIds(any())).thenReturn(List.of(ja));

        List<InspectionListItemResponse> items = service.listInspections(raceId, null);

        assertThat(items).hasSize(2);

        InspectionListItemResponse i1 = items.stream().filter(i -> i.getEntryId().equals(entryId)).findFirst().orElseThrow();
        assertThat(i1.getInspectionId()).isEqualTo(insp1.getInspectionId());
        assertThat(i1.getInspectionStatus()).isEqualTo(InspectionStatus.CLEARED);
        assertThat(i1.getHorseName()).isEqualTo("Thunderbolt");
        assertThat(i1.isHealthCertPassed()).isTrue();

        InspectionListItemResponse i2 = items.stream().filter(i -> i.getEntryId().equals(entryId2)).findFirst().orElseThrow();
        // not yet inspected -> null id, PENDING default
        assertThat(i2.getInspectionId()).isNull();
        assertThat(i2.getInspectionStatus()).isEqualTo(InspectionStatus.PENDING);
        assertThat(i2.getJockeyName()).isEqualTo("L. Park");
        assertThat(i2.getInspectedAt()).isNull();
    }

    @Test
    void list_filtersByStatus() {
        UUID entryId2 = UUID.randomUUID();
        RaceEntry entry2 = RaceEntry.builder()
                .entryId(entryId2)
                .race(race)
                .registration(TournamentRegistration.builder()
                        .horse(Horse.builder().horseId(UUID.randomUUID()).name("Night Comet").build()).build())
                .laneNo(4)
                .build();

        RaceEntryInspection cleared = RaceEntryInspection.builder()
                .inspectionId(UUID.randomUUID()).entry(entry).race(race)
                .inspectionStatus(InspectionStatus.CLEARED).build();

        when(raceRepository.existsById(raceId)).thenReturn(true);
        when(raceEntryRepository.findByRaceIdWithHorse(raceId)).thenReturn(List.of(entry, entry2));
        when(inspectionRepository.findByEntry_EntryIdIn(any())).thenReturn(List.of(cleared));
        lenient().when(jockeyAssignmentRepository.findAcceptedByEntryIds(any())).thenReturn(List.of());

        // entry2 has no inspection => PENDING, so filter PENDING returns just entry2
        List<InspectionListItemResponse> pending = service.listInspections(raceId, InspectionStatus.PENDING);
        assertThat(pending).extracting(InspectionListItemResponse::getEntryId).containsExactly(entryId2);

        List<InspectionListItemResponse> clearedItems = service.listInspections(raceId, InspectionStatus.CLEARED);
        assertThat(clearedItems).extracting(InspectionListItemResponse::getEntryId).containsExactly(entryId);
    }

    @Test
    void list_raceNotFound() {
        when(raceRepository.existsById(raceId)).thenReturn(false);
        assertThatThrownBy(() -> service.listInspections(raceId, null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RACE_NOT_FOUND);
    }

    // ── submit-all ──

    @Test
    void submitAll_blocksNonCleared_countsCleared() {
        UUID entryId2 = UUID.randomUUID();
        RaceEntry entry2 = RaceEntry.builder()
                .entryId(entryId2)
                .race(race)
                .registration(TournamentRegistration.builder()
                        .horse(Horse.builder().horseId(UUID.randomUUID()).name("Night Comet").build()).build())
                .build();
        UUID entryId3 = UUID.randomUUID();
        RaceEntry entry3 = RaceEntry.builder()
                .entryId(entryId3)
                .race(race)
                .registration(TournamentRegistration.builder()
                        .horse(Horse.builder().horseId(UUID.randomUUID()).name("Dawn Runner").build()).build())
                .build();

        RaceEntryInspection cleared = RaceEntryInspection.builder()
                .inspectionId(UUID.randomUUID()).entry(entry).race(race)
                .inspectionStatus(InspectionStatus.CLEARED).build();
        RaceEntryInspection vet = RaceEntryInspection.builder()
                .inspectionId(UUID.randomUUID()).entry(entry2).race(race)
                .inspectionStatus(InspectionStatus.VET_CHECK).build();
        // entry3 has NO inspection

        when(raceRepository.existsById(raceId)).thenReturn(true);
        when(raceEntryRepository.findByRaceIdWithHorse(raceId)).thenReturn(List.of(entry, entry2, entry3));
        when(inspectionRepository.findByEntry_EntryIdIn(any())).thenReturn(List.of(cleared, vet));

        SubmitAllResponse resp = service.submitAll(raceId, new SubmitAllRequest(true));

        assertThat(resp.getRaceId()).isEqualTo(raceId);
        assertThat(resp.getSubmittedCount()).isEqualTo(1);
        assertThat(resp.getBlockedEntries()).hasSize(2);
        assertThat(resp.getBlockedEntries())
                .extracting(SubmitAllResponse.BlockedEntry::getEntryId)
                .containsExactlyInAnyOrder(entryId2, entryId3);
        // missing inspection -> "Not inspected"; VET_CHECK -> "VET_CHECK unresolved"
        assertThat(resp.getBlockedEntries())
                .extracting(SubmitAllResponse.BlockedEntry::getReason)
                .containsExactlyInAnyOrder("VET_CHECK unresolved", "Not inspected");
    }

    @Test
    void submitAll_confirmFalse_throws() {
        assertThatThrownBy(() -> service.submitAll(raceId, new SubmitAllRequest(false)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSPECTION_CONFIRM_REQUIRED);
    }

    @Test
    void submitAll_raceNotFound() {
        when(raceRepository.existsById(raceId)).thenReturn(false);
        assertThatThrownBy(() -> service.submitAll(raceId, new SubmitAllRequest(true)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RACE_NOT_FOUND);
    }
}

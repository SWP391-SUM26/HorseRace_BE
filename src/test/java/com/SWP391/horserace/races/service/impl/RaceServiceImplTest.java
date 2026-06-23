package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.races.dto.AssignParticipantRequest;
import com.SWP391.horserace.races.dto.MyEntryResponse;
import com.SWP391.horserace.races.dto.RaceEntryResponse;
import com.SWP391.horserace.races.dto.RaceFilterRequest;
import com.SWP391.horserace.races.dto.RaceRequest;
import com.SWP391.horserace.races.dto.RaceResponse;
import com.SWP391.horserace.races.dto.ScheduleRaceRequest;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceEntryStatus;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.tournaments.repository.TournamentRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceServiceImplTest {

    @Mock RaceRepository raceRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock TournamentRepository tournamentRepository;
    @Mock UserRepository userRepository;
    @Mock JockeyAssignmentRepository jockeyAssignmentRepository;

    private RaceServiceImpl service;

    private final UUID currentUserId = UUID.randomUUID();
    private final UUID tournamentId = UUID.randomUUID();

    private Tournament tournament;

    @BeforeEach
    void setUp() {
        service = new RaceServiceImpl(
                raceRepository, raceEntryRepository, registrationRepository, tournamentRepository,
                userRepository, jockeyAssignmentRepository);
        tournament = Tournament.builder().tournamentId(tournamentId).name("Spring Cup").build();
    }

    private RaceRequest createReq() {
        return new RaceRequest(tournamentId, "Race 1", "FLAT", 1200, "GOOD", "SUNNY",
                null, null, 8);
    }

    // ── create ──

    @Test
    void create_tournamentNotFound_throws() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRace(currentUserId, createReq()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOURNAMENT_NOT_FOUND);
    }

    @Test
    void create_happyPath_setsScheduledStatusAndCode() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(raceRepository.count()).thenReturn(4L);
        when(raceRepository.existsByRaceCode(any())).thenReturn(false);
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        RaceResponse res = service.createRace(currentUserId, createReq());

        assertThat(res.getStatus()).isEqualTo(RaceStatus.SCHEDULED);
        assertThat(res.getRaceCode()).isEqualTo("RACE00005");
        assertThat(res.getTournamentId()).isEqualTo(tournamentId);
        assertThat(res.getTournamentName()).isEqualTo("Spring Cup");
    }

    // ── update ──

    @Test
    void update_fromCancelled_invalidStatus() {
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.CANCELLED).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> service.updateRace(currentUserId, id, createReq()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_INVALID_STATUS);
    }

    // ── delete ──

    @Test
    void delete_setsDeletedAndDeletedAt() {
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.SCHEDULED).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        service.deleteRace(currentUserId, id);

        assertThat(race.isDeleted()).isTrue();
        assertThat(race.getDeletedAt()).isNotNull();
    }

    // ── schedule ──

    @Test
    void schedule_fromScheduled_opensAndSetsTimes() {
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.SCHEDULED).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        OffsetDateTime cutoff = start.minusHours(1);
        RaceResponse res = service.scheduleRace(currentUserId, id, new ScheduleRaceRequest(start, cutoff));

        assertThat(res.getStatus()).isEqualTo(RaceStatus.OPEN);
        assertThat(res.getScheduledStartAt()).isEqualTo(start);
        assertThat(res.getPredictionCutoffAt()).isEqualTo(cutoff);
    }

    @Test
    void schedule_nullCutoff_keepsExistingCutoff() {
        UUID id = UUID.randomUUID();
        OffsetDateTime existingCutoff = OffsetDateTime.now().plusDays(2);
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.SCHEDULED)
                .predictionCutoffAt(existingCutoff).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        OffsetDateTime start = OffsetDateTime.now().plusDays(3);
        RaceResponse res = service.scheduleRace(currentUserId, id, new ScheduleRaceRequest(start, null));

        assertThat(res.getStatus()).isEqualTo(RaceStatus.OPEN);
        assertThat(res.getScheduledStartAt()).isEqualTo(start);
        // omitted cutoff must not wipe the one set at create time
        assertThat(res.getPredictionCutoffAt()).isEqualTo(existingCutoff);
    }

    @Test
    void schedule_fromOpen_invalidStatus() {
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.OPEN).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> service.scheduleRace(currentUserId, id,
                new ScheduleRaceRequest(OffsetDateTime.now(), null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_INVALID_STATUS);
    }

    // ── cancel ──

    @Test
    void cancel_fromScheduled_cancels() {
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.SCHEDULED).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        RaceResponse res = service.cancelRace(currentUserId, id);

        assertThat(res.getStatus()).isEqualTo(RaceStatus.CANCELLED);
    }

    @Test
    void cancel_fromFinished_invalidStatus() {
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.FINISHED).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> service.cancelRace(currentUserId, id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_INVALID_STATUS);
    }

    // ── listRaces ──

    @Test
    void listRaces_withDateRange_returnsMappedPageWithoutThrowing() {
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.SCHEDULED).build();
        Page<Race> page = new PageImpl<>(List.of(race));
        when(raceRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        RaceFilterRequest filter = RaceFilterRequest.builder()
                .dateFrom(OffsetDateTime.now().minusDays(1))
                .dateTo(OffsetDateTime.now().plusDays(1))
                .build();

        Page<RaceResponse> result = service.listRaces(filter);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getRaceId()).isEqualTo(id);
        assertThat(result.getContent().get(0).getRaceCode()).isEqualTo("RACE00001");
    }

    @Test
    void listRaces_sortByRaceCode_buildsPageableSortedByRaceCode() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(raceRepository.findAll(any(Specification.class), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        RaceFilterRequest filter = RaceFilterRequest.builder()
                .sortBy("raceCode")
                .sortDir("asc")
                .build();

        service.listRaces(filter);

        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("raceCode");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    // ── assignParticipant ──

    private Race openRace(UUID id) {
        return Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.OPEN).maxParticipants(8).build();
    }

    private TournamentRegistration approvedReg(UUID regId, UUID tId) {
        User owner = User.builder().userId(UUID.randomUUID()).fullName("Owen Owner").build();
        Horse horse = Horse.builder().horseId(UUID.randomUUID()).owner(owner).name("Thunder").build();
        Tournament t = Tournament.builder().tournamentId(tId).name("Spring Cup").build();
        return TournamentRegistration.builder()
                .registrationId(regId)
                .owner(owner).horse(horse).tournament(t)
                .status(RegistrationStatus.APPROVED)
                .build();
    }

    @Test
    void assign_raceNotOpen_throws() {
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.FINISHED).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> service.assignParticipant(currentUserId, id,
                new AssignParticipantRequest(UUID.randomUUID(), 1, 1)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_NOT_OPEN_FOR_ENTRY);
    }

    @Test
    void assign_registrationNotApproved_throws() {
        UUID id = UUID.randomUUID();
        UUID regId = UUID.randomUUID();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(openRace(id)));
        TournamentRegistration reg = approvedReg(regId, tournamentId);
        reg.setStatus(RegistrationStatus.SUBMITTED);
        when(registrationRepository.findById(regId)).thenReturn(Optional.of(reg));

        assertThatThrownBy(() -> service.assignParticipant(currentUserId, id,
                new AssignParticipantRequest(regId, 1, 1)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGISTRATION_NOT_APPROVED);
    }

    @Test
    void assign_tournamentMismatch_throws() {
        UUID id = UUID.randomUUID();
        UUID regId = UUID.randomUUID();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(openRace(id)));
        when(registrationRepository.findById(regId))
                .thenReturn(Optional.of(approvedReg(regId, UUID.randomUUID()))); // different tournament

        assertThatThrownBy(() -> service.assignParticipant(currentUserId, id,
                new AssignParticipantRequest(regId, 1, 1)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_TOURNAMENT_MISMATCH);
    }

    @Test
    void assign_capacityReached_throws() {
        UUID id = UUID.randomUUID();
        UUID regId = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.OPEN).maxParticipants(2).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));
        when(registrationRepository.findById(regId)).thenReturn(Optional.of(approvedReg(regId, tournamentId)));
        when(raceEntryRepository.countByRace_RaceId(id)).thenReturn(2L);

        assertThatThrownBy(() -> service.assignParticipant(currentUserId, id,
                new AssignParticipantRequest(regId, 1, 1)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_FULL);
    }

    @Test
    void assign_happyPath_createsEnteredEntryWithCode() {
        UUID id = UUID.randomUUID();
        UUID regId = UUID.randomUUID();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(openRace(id)));
        when(registrationRepository.findById(regId)).thenReturn(Optional.of(approvedReg(regId, tournamentId)));
        when(raceEntryRepository.countByRace_RaceId(id)).thenReturn(0L);
        when(raceEntryRepository.count()).thenReturn(4L);
        when(raceEntryRepository.existsByEntryCode(any())).thenReturn(false);
        when(raceEntryRepository.save(any(RaceEntry.class))).thenAnswer(i -> i.getArgument(0));

        RaceEntryResponse res = service.assignParticipant(currentUserId, id,
                new AssignParticipantRequest(regId, 3, 7));

        assertThat(res.getStatus()).isEqualTo(RaceEntryStatus.ENTERED);
        assertThat(res.getEntryCode()).isEqualTo("ENT00005");
        assertThat(res.getLaneNo()).isEqualTo(3);
        assertThat(res.getDrawStall()).isEqualTo("3");
        assertThat(res.getEntryNo()).isEqualTo(7);
        assertThat(res.getRaceId()).isEqualTo(id);
        assertThat(res.getRegistrationId()).isEqualTo(regId);
        assertThat(res.getHorseName()).isEqualTo("Thunder");
        assertThat(res.getOwnerName()).isEqualTo("Owen Owner");
    }

    // ── listEntries (mapping: drawStall + jockeyName) ──

    @Test
    void listEntries_mapsDrawStallFromLaneAndAcceptedJockeyName() {
        UUID raceId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();

        TournamentRegistration reg = approvedReg(UUID.randomUUID(), tournamentId);
        Race race = openRace(raceId);
        RaceEntry entry = RaceEntry.builder()
                .entryId(entryId).registration(reg).race(race)
                .entryCode("ENT00001").entryNo(1).laneNo(5)
                .weightCarriedLbs(126).recentForm("1-2-1-1-3").odds("5-2")
                .status(RaceEntryStatus.ENTERED).build();

        User jockey = User.builder().userId(UUID.randomUUID()).fullName("D. Oliver").build();
        JockeyAssignment ja = JockeyAssignment.builder()
                .assignmentId(UUID.randomUUID()).entry(entry).jockey(jockey)
                .status(JockeyAssignmentStatus.ACCEPTED).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRace_RaceId(raceId)).thenReturn(List.of(entry));
        when(jockeyAssignmentRepository.findAcceptedByEntryIds(List.of(entryId)))
                .thenReturn(List.of(ja));

        List<RaceEntryResponse> result = service.listEntries(raceId);

        assertThat(result).hasSize(1);
        RaceEntryResponse res = result.get(0);
        assertThat(res.getDrawStall()).isEqualTo("5");
        assertThat(res.getJockeyName()).isEqualTo("D. Oliver");
        assertThat(res.getWeightCarriedLbs()).isEqualTo(126);
        assertThat(res.getRecentForm()).isEqualTo("1-2-1-1-3");
        assertThat(res.getOdds()).isEqualTo("5-2");
    }

    // ── getMyEntry ──

    @Test
    void getMyEntry_happyPath_mapsOwnerEntry() {
        UUID raceId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        User owner = User.builder().userId(ownerId).fullName("Owen Owner").build();
        Horse horse = Horse.builder().horseId(UUID.randomUUID()).owner(owner).name("Storm Weaver").build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .registrationId(UUID.randomUUID()).owner(owner).horse(horse).tournament(tournament)
                .status(RegistrationStatus.APPROVED).build();
        RaceEntry entry = RaceEntry.builder()
                .entryId(entryId).registration(reg).race(openRace(raceId))
                .entryCode("ENT00001").entryNo(1).laneNo(4)
                .weightCarriedLbs(126).status(RaceEntryStatus.CHECKED_IN).build();

        User jockey = User.builder().userId(UUID.randomUUID()).fullName("D. Oliver").build();
        JockeyAssignment ja = JockeyAssignment.builder()
                .assignmentId(UUID.randomUUID()).entry(entry).jockey(jockey)
                .status(JockeyAssignmentStatus.ACCEPTED).build();

        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(openRace(raceId)));
        when(raceEntryRepository.findByRaceIdAndOwnerUserId(raceId, ownerId)).thenReturn(Optional.of(entry));
        when(jockeyAssignmentRepository.findAcceptedByEntryId(entryId)).thenReturn(Optional.of(ja));

        MyEntryResponse res = service.getMyEntry(raceId, ownerId);

        assertThat(res.getHorseName()).isEqualTo("Storm Weaver");
        assertThat(res.getDrawStall()).isEqualTo("4");
        assertThat(res.getJockeyName()).isEqualTo("D. Oliver");
        assertThat(res.getWeightCarriedLbs()).isEqualTo(126);
        assertThat(res.getEntryStatus()).isEqualTo(RaceEntryStatus.CHECKED_IN);
    }

    @Test
    void getMyEntry_raceNotFound_throws() {
        UUID raceId = UUID.randomUUID();
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyEntry(raceId, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_NOT_FOUND);
    }

    @Test
    void getMyEntry_noEntryForOwner_throwsEntryNotFound() {
        UUID raceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(openRace(raceId)));
        when(raceEntryRepository.findByRaceIdAndOwnerUserId(raceId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyEntry(raceId, ownerId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENTRY_NOT_FOUND);
    }
}

package com.SWP391.horserace.races.service.impl;

import com.SWP391.horserace.assignments.entity.JockeyAssignment;
import com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus;
import com.SWP391.horserace.assignments.repository.JockeyAssignmentRepository;
import com.SWP391.horserace.horses.entity.Horse;
import com.SWP391.horserace.races.dto.AssignParticipantRequest;
import com.SWP391.horserace.races.dto.MyEntryResponse;
import com.SWP391.horserace.races.dto.RaceEntryResponse;
import com.SWP391.horserace.races.dto.RaceFilterRequest;
import com.SWP391.horserace.races.dto.PrizeDistributionDto;
import com.SWP391.horserace.races.dto.RaceRequest;
import com.SWP391.horserace.races.dto.RaceResponse;
import com.SWP391.horserace.races.dto.ScheduleRaceRequest;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceEntryStatus;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.prizes.repository.PrizeRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import com.SWP391.horserace.registrations.repository.RegistrationRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.tournaments.entity.Tournament;
import com.SWP391.horserace.tournaments.repository.TournamentRepository;
import com.SWP391.horserace.roles.entity.Role;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceServiceImplTest {

    @Mock RaceRepository raceRepository;
    @Mock PrizeRepository prizeRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock TournamentRepository tournamentRepository;
    @Mock UserRepository userRepository;
    @Mock JockeyAssignmentRepository jockeyAssignmentRepository;
    @Mock com.SWP391.horserace.venues.repository.VenueRepository venueRepository;
    @Mock com.SWP391.horserace.staffing.repository.RefereeAssignmentRepository refereeAssignmentRepository;
    @Mock com.SWP391.horserace.races.service.RaceEntryGate raceEntryGate;
    @Mock com.SWP391.horserace.notifications.service.NotificationService notificationService;
    @Mock com.SWP391.horserace.inspections.repository.EntryDocumentReviewRepository entryDocumentReviewRepository;
    @Mock com.SWP391.horserace.inspections.repository.RaceEntryInspectionRepository raceEntryInspectionRepository;

    private RaceServiceImpl service;

    private final UUID currentUserId = UUID.randomUUID();
    private final UUID tournamentId = UUID.randomUUID();

    private Tournament tournament;

    @BeforeEach
    void setUp() {
        service = new RaceServiceImpl(
                raceRepository, prizeRepository, raceEntryRepository, registrationRepository, tournamentRepository,
                userRepository, jockeyAssignmentRepository, venueRepository, refereeAssignmentRepository,
                raceEntryGate, notificationService, entryDocumentReviewRepository, raceEntryInspectionRepository);
        tournament = Tournament.builder().tournamentId(tournamentId).name("Spring Cup").build();
    }

    private RaceRequest createReq() {
        return new RaceRequest(tournamentId, "Race 1", "FLAT", 1200, "GOOD", "SUNNY",
                null, null, 8, null, null, null);
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

    // ── #2 date validation ──

    private RaceRequest reqDates(OffsetDateTime start, OffsetDateTime cutoff) {
        return new RaceRequest(tournamentId, "R", "FLAT", 1200, "GOOD", "SUNNY", start, cutoff, 8, null, null, null);
    }

    @Test
    void createRace_predictionCutoffAfterStart_throwsInvalidDateRange() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        var now = OffsetDateTime.now();
        assertThatThrownBy(() -> service.createRace(currentUserId, reqDates(now.plusDays(1), now.plusDays(3))))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    void createRace_scheduledStartInPast_throwsDateInPast() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        assertThatThrownBy(() -> service.createRace(currentUserId, reqDates(OffsetDateTime.now().minusDays(2), null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DATE_IN_PAST);
    }

    @Test
    void createRace_scheduledStartToday_passes() {
        // Boundary (Risk c): today accepted.
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(raceRepository.count()).thenReturn(0L);
        when(raceRepository.existsByRaceCode(any())).thenReturn(false);
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));
        var now = OffsetDateTime.now();
        RaceResponse res = service.createRace(currentUserId, reqDates(now, now));
        assertThat(res).isNotNull();
    }

    // ── FR-12 min ≤ max participant range ──

    @Test
    void createRace_rejectsMinGreaterThanMax() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        // params: (…, maxParticipants=4, minParticipants=8, …) → min > max
        RaceRequest req = new RaceRequest(tournamentId, "R", "FLAT", 1200, "GOOD", "SUNNY",
                null, null, 4, 8, null, null);
        assertThatThrownBy(() -> service.createRace(currentUserId, req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_INVALID_PARTICIPANT_RANGE);
        verify(raceRepository, never()).save(any(Race.class));
    }

    @Test
    void createRace_allowsMinEqualMax() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(raceRepository.count()).thenReturn(0L);
        when(raceRepository.existsByRaceCode(any())).thenReturn(false);
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));
        RaceRequest req = new RaceRequest(tournamentId, "R", "FLAT", 1200, "GOOD", "SUNNY",
                null, null, 5, 5, null, null); // max == min
        assertThat(service.createRace(currentUserId, req)).isNotNull();
    }

    @Test
    void createRace_allowsMinLessThanMax() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(raceRepository.count()).thenReturn(0L);
        when(raceRepository.existsByRaceCode(any())).thenReturn(false);
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));
        RaceRequest req = new RaceRequest(tournamentId, "R", "FLAT", 1200, "GOOD", "SUNNY",
                null, null, 8, 2, null, null); // max=8, min=2
        assertThat(service.createRace(currentUserId, req)).isNotNull();
    }

    @Test
    void updateRace_rejectsMinGreaterThanMax_onEffectiveValues() {
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.SCHEDULED).maxParticipants(4).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));
        // supply only min=8 (max stays existing 4) → effective min > max
        RaceRequest req = new RaceRequest(tournamentId, null, null, null, null, null,
                null, null, null, 8, null, null);
        assertThatThrownBy(() -> service.updateRace(currentUserId, id, req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_INVALID_PARTICIPANT_RANGE);
        verify(raceRepository, never()).save(any(Race.class));
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
    void schedule_cutoffNotBeforeStart_throwsInvalidTiming() {
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.SCHEDULED).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));

        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        OffsetDateTime cutoff = start.plusHours(1); // cutoff AFTER start → race would be un-startable

        assertThatThrownBy(() -> service.scheduleRace(currentUserId, id, new ScheduleRaceRequest(start, cutoff)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_INVALID_TIMING);
        verify(raceRepository, never()).save(any(Race.class));
    }

    @Test
    void scheduleRace_rejectsPastStart() {
        // FR-04: a past scheduledStartAt is rejected; status is not flipped to OPEN and nothing is saved.
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.SCHEDULED).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));

        OffsetDateTime pastStart = OffsetDateTime.now().minusDays(1);

        assertThatThrownBy(() -> service.scheduleRace(currentUserId, id, new ScheduleRaceRequest(pastStart, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DATE_IN_PAST);
        assertThat(race.getStatus()).isEqualTo(RaceStatus.SCHEDULED);
        verify(raceRepository, never()).save(any(Race.class));
    }

    @Test
    void scheduleRace_allowsTodayOrFutureStart() {
        // Boundary: today must pass the past-check (mirrors validateDates day-granularity).
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.SCHEDULED).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        OffsetDateTime today = OffsetDateTime.now();
        RaceResponse res = service.scheduleRace(currentUserId, id, new ScheduleRaceRequest(today, null));

        assertThat(res.getStatus()).isEqualTo(RaceStatus.OPEN);
        assertThat(res.getScheduledStartAt()).isEqualTo(today);
    }

    @Test
    void scheduleRace_stillEnforcesCutoffBeforeStart() {
        // Regression: the past-check must not pre-empt the existing cutoff-before-start guard.
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.SCHEDULED).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));

        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        OffsetDateTime cutoff = start.plusHours(1); // cutoff AFTER start → un-startable

        assertThatThrownBy(() -> service.scheduleRace(currentUserId, id, new ScheduleRaceRequest(start, cutoff)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_INVALID_TIMING);
        verify(raceRepository, never()).save(any(Race.class));
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

    // ── close (FR-01: OPEN → CLOSED; the only state betting is accepted in) ──

    @Test
    void closeRace_fromOpen_setsClosed() {
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.OPEN).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        RaceResponse res = service.closeRace(currentUserId, id);

        assertThat(res.getStatus()).isEqualTo(RaceStatus.CLOSED);
        assertThat(race.getStatus()).isEqualTo(RaceStatus.CLOSED);
        verify(raceRepository).save(race);
    }

    @Test
    void closeRace_whenNotOpen_rejects() {
        for (RaceStatus notOpen : new RaceStatus[]{
                RaceStatus.SCHEDULED, RaceStatus.CLOSED, RaceStatus.RUNNING,
                RaceStatus.FINISHED, RaceStatus.OFFICIAL, RaceStatus.CANCELLED}) {
            UUID id = UUID.randomUUID();
            Race race = Race.builder().raceId(id).tournament(tournament)
                    .raceCode("RACE00001").status(notOpen).build();
            when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));

            assertThatThrownBy(() -> service.closeRace(currentUserId, id))
                    .as("closing from %s must be rejected", notOpen)
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_INVALID_STATUS);
            assertThat(race.getStatus()).isEqualTo(notOpen);
        }
        verify(raceRepository, never()).save(any(Race.class));
    }

    @Test
    void closeRace_nullUser_rejects() {
        assertThatThrownBy(() -> service.closeRace(null, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
        verify(raceRepository, never()).findByRaceIdAndDeletedFalse(any());
        verify(raceRepository, never()).save(any(Race.class));
    }

    // ── §D1 venueId set on create / update ──

    @Test
    void create_withVenueId_setsVenueRefAndExposesName() {
        UUID venueId = UUID.randomUUID();
        com.SWP391.horserace.venues.entity.Venue venue =
                com.SWP391.horserace.venues.entity.Venue.builder().venueId(venueId).name("Meydan").build();
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));
        when(raceRepository.count()).thenReturn(0L);
        when(raceRepository.existsByRaceCode(any())).thenReturn(false);
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        RaceRequest req = new RaceRequest(tournamentId, "Race 1", "FLAT", 1200, "GOOD", "SUNNY",
                null, null, 8, null, null, venueId);
        RaceResponse res = service.createRace(currentUserId, req);

        assertThat(res.getVenueId()).isEqualTo(venueId);
        assertThat(res.getVenueName()).isEqualTo("Meydan");
    }

    @Test
    void create_unknownVenueId_throwsVenueNotFound() {
        UUID venueId = UUID.randomUUID();
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        RaceRequest req = new RaceRequest(tournamentId, "Race 1", "FLAT", 1200, "GOOD", "SUNNY",
                null, null, 8, null, null, venueId);

        assertThatThrownBy(() -> service.createRace(currentUserId, req))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VENUE_NOT_FOUND);
    }

    // ── §D2 entriesCount embed ──

    @Test
    void getRaceById_embedsEntriesCount() {
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(RaceStatus.OPEN).maxParticipants(10).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));
        when(raceEntryRepository.countByRace_RaceId(id)).thenReturn(3L);

        RaceResponse res = service.getRaceById(id);

        assertThat(res.getEntriesCount()).isEqualTo(3L);
        assertThat(res.getMaxParticipants()).isEqualTo(10);
    }

    // ── §D3 race stats ──

    @Test
    void getRaceStats_mapsStatusBucketsAndTotal() {
        when(raceRepository.countGroupByStatus(null)).thenReturn(List.of(
                new Object[]{RaceStatus.SCHEDULED, 4L},
                new Object[]{RaceStatus.OPEN, 2L},
                new Object[]{RaceStatus.CANCELLED, 1L},
                new Object[]{RaceStatus.FINISHED, 3L}));

        com.SWP391.horserace.races.dto.RaceStatsResponse stats = service.getRaceStats(null);

        assertThat(stats.getScheduled()).isEqualTo(4L);
        assertThat(stats.getActive()).isEqualTo(2L);   // OPEN -> active
        assertThat(stats.getCancelled()).isEqualTo(1L);
        assertThat(stats.getTotal()).isEqualTo(10L);   // includes FINISHED in total only
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

    /** Owned by {@code currentUserId} — entering a horse debits the owner, so the caller must be them. */
    private TournamentRegistration approvedReg(UUID regId, UUID tId) {
        User owner = User.builder().userId(currentUserId).fullName("Owen Owner").build();
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
        when(raceEntryRepository.findAllByRaceIdAndOwnerUserId(raceId, ownerId)).thenReturn(List.of(entry));
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
        when(raceEntryRepository.findAllByRaceIdAndOwnerUserId(raceId, ownerId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getMyEntry(raceId, ownerId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENTRY_NOT_FOUND);
    }

    // ── startRace: admin override (the admin's decision is final) ──

    private Race startableRace(UUID id, RaceStatus status) {
        return Race.builder().raceId(id).tournament(tournament)
                .raceCode("RACE00001").status(status).maxParticipants(8).minParticipants(2)
                .build();
    }

    @Test
    void startRace_fromOpen_setsRunning() {
        UUID id = UUID.randomUUID();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(startableRace(id, RaceStatus.OPEN)));
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        RaceResponse res = service.startRace(currentUserId, id);

        assertThat(res.getStatus()).isEqualTo(RaceStatus.RUNNING);
    }

    @Test
    void startRace_fromClosed_setsRunning() {
        UUID id = UUID.randomUUID();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(startableRace(id, RaceStatus.CLOSED)));
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        RaceResponse res = service.startRace(currentUserId, id);

        assertThat(res.getStatus()).isEqualTo(RaceStatus.RUNNING);
    }

    @Test
    void startRace_adminOverride_startsWithNoReadinessChecks() {
        // Admin's decision is final: NO readiness gate (min participants, confirmed jockeys/referee,
        // documents accepted, vet-cleared, registration window) blocks the start. Even an empty OPEN
        // race with no referee/documents/vet starts, and those gate repositories are never consulted.
        UUID id = UUID.randomUUID();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(startableRace(id, RaceStatus.OPEN)));
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        RaceResponse res = service.startRace(currentUserId, id);

        assertThat(res.getStatus()).isEqualTo(RaceStatus.RUNNING);
        verify(entryDocumentReviewRepository, never()).findByEntry_EntryIdIn(any());
        verify(raceEntryInspectionRepository, never()).findByEntry_EntryIdIn(any());
        verify(refereeAssignmentRepository, never()).existsByRace_RaceIdAndStatus(any(), any());
    }

    @Test
    void startRace_invalidStatus_throws() {
        UUID id = UUID.randomUUID();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(startableRace(id, RaceStatus.FINISHED)));
        assertThatThrownBy(() -> service.startRace(currentUserId, id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_INVALID_STATUS);
        verify(raceRepository, never()).save(any(Race.class));
    }

    // ── auto-cancel proposal (FR-05) ──

    private Race underfilledRace(UUID id) {
        return Race.builder().raceId(id).name("The Dusty Handicap").status(RaceStatus.OPEN)
                .minParticipants(2)
                .predictionCutoffAt(OffsetDateTime.now().minusMinutes(5)) // cutoff passed
                .build();
    }

    @Test
    void proposeCancel_underfilledPastCutoff_flagsAtomicallyAndNotifiesAdmins() {
        UUID id = UUID.randomUUID();
        Race r = underfilledRace(id);
        User admin = User.builder().userId(UUID.randomUUID()).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(r));
        when(jockeyAssignmentRepository.countAcceptedByRaceId(id)).thenReturn(0L); // < min 2
        when(raceRepository.markCancelProposed(eq(id), any())).thenReturn(1); // won the atomic flag
        when(userRepository.findByRole_RoleCodeAndDeletedFalse("ADMIN")).thenReturn(java.util.List.of(admin));

        service.proposeCancel(id);

        verify(raceRepository).markCancelProposed(eq(id), any());
        verify(raceRepository, never()).save(any()); // no full-row save — can't clobber a concurrent cancel
        verify(notificationService).notifyUser(eq(admin.getUserId()), any(), any());
    }

    @Test
    void proposeCancel_lostRaceToConcurrentWriter_noNotify() {
        UUID id = UUID.randomUUID();
        Race r = underfilledRace(id);
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(r));
        when(jockeyAssignmentRepository.countAcceptedByRaceId(id)).thenReturn(0L);
        when(raceRepository.markCancelProposed(eq(id), any())).thenReturn(0); // already cancelled/proposed

        service.proposeCancel(id);

        verify(notificationService, never()).notifyUser(any(), any(), any());
    }

    @Test
    void proposeCancel_enoughRunners_noProposalNoNotify() {
        UUID id = UUID.randomUUID();
        Race r = underfilledRace(id);
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(r));
        when(jockeyAssignmentRepository.countAcceptedByRaceId(id)).thenReturn(2L); // meets min

        service.proposeCancel(id);

        verify(raceRepository, never()).markCancelProposed(any(), any());
        verify(notificationService, never()).notifyUser(any(), any(), any());
    }

    @Test
    void proposeCancel_alreadyProposed_idempotentSkip() {
        UUID id = UUID.randomUUID();
        Race r = underfilledRace(id);
        r.setCancelProposedAt(OffsetDateTime.now().minusMinutes(1)); // already flagged
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(r));

        service.proposeCancel(id);

        verify(jockeyAssignmentRepository, never()).countAcceptedByRaceId(any());
        verify(raceRepository, never()).markCancelProposed(any(), any());
        verify(notificationService, never()).notifyUser(any(), any(), any());
    }

    // ── cancelRace notifies affected parties (FR-07) ──

    @Test
    void cancelRace_notifiesRefereeOwnerJockey() {
        UUID id = UUID.randomUUID();
        UUID refId = UUID.randomUUID();
        UUID jockeyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(openRace(id)));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));
        var refAssign = com.SWP391.horserace.assignments.entity.RefereeAssignment.builder()
                .referee(User.builder().userId(refId).build()).build();
        when(refereeAssignmentRepository.findByRace_RaceIdAndStatusNot(eq(id), any()))
                .thenReturn(java.util.List.of(refAssign));
        when(jockeyAssignmentRepository.findJockeyIdsAcceptedInRace(id)).thenReturn(java.util.List.of(jockeyId));
        var entry = com.SWP391.horserace.races.entity.RaceEntry.builder()
                .registration(com.SWP391.horserace.registrations.entity.TournamentRegistration.builder()
                        .owner(User.builder().userId(ownerId).build()).build())
                .build();
        when(raceEntryRepository.findByRace_RaceId(id)).thenReturn(java.util.List.of(entry));

        service.cancelRace(currentUserId, id);

        verify(notificationService).notifyUser(eq(refId), any(), any());
        verify(notificationService).notifyUser(eq(jockeyId), any(), any());
        verify(notificationService).notifyUser(eq(ownerId), any(), any());
    }

    // ── auto-close sweep (FR-07) ──

    @Test
    void findRacesToAutoClose_delegatesWithNowPlusLeadBoundary() {
        // lead = 30 min; the finder must be called with a threshold ~= now + 30min.
        org.springframework.test.util.ReflectionTestUtils.setField(service, "autoCloseLeadMs", 1_800_000L);
        UUID x = UUID.randomUUID();
        ArgumentCaptor<OffsetDateTime> threshold = ArgumentCaptor.forClass(OffsetDateTime.class);
        when(raceRepository.findOpenRacesToAutoClose(threshold.capture())).thenReturn(List.of(x));

        OffsetDateTime before = OffsetDateTime.now().plusMinutes(30);
        List<UUID> result = service.findRacesToAutoClose();
        OffsetDateTime after = OffsetDateTime.now().plusMinutes(30);

        assertThat(result).containsExactly(x);
        // threshold is now + 30min, so it sits within the [before, after] bracket around now+30min.
        assertThat(threshold.getValue()).isBetween(before.minusSeconds(5), after.plusSeconds(5));
    }

    @Test
    void autoClose_open_marksClosed() {
        UUID id = UUID.randomUUID();
        when(raceRepository.markClosed(id)).thenReturn(1); // won the atomic OPEN→CLOSED

        service.autoClose(id);

        verify(raceRepository).markClosed(id);
        verify(raceRepository, never()).save(any()); // conditional update only — no full-row save
    }

    @Test
    void autoClose_nonOpen_noOpNoThrow() {
        UUID id = UUID.randomUUID();
        when(raceRepository.markClosed(id)).thenReturn(0); // already CLOSED/CANCELLED/RUNNING or lost race

        service.autoClose(id); // must not throw

        verify(raceRepository).markClosed(id);
        verify(raceRepository, never()).save(any());
    }

    // =========================================================================
    // Prize / purse budget rules (9901–9914) and the tournament date window
    // =========================================================================

    /** A dated, funded tournament — the bare fixture has neither, so budget rules are inert on it. */
    private Tournament fundedTournament(String purse) {
        return Tournament.builder().tournamentId(tournamentId).name("Spring Cup")
                .totalPurse(new java.math.BigDecimal(purse))
                .startDate(OffsetDateTime.now().plusDays(10))
                .endDate(OffsetDateTime.now().plusDays(20))
                .build();
    }

    private RaceRequest reqWithPurse(String purse, java.util.List<PrizeDistributionDto> tiers,
                                     OffsetDateTime start) {
        return new RaceRequest(tournamentId, "Race X", "FLAT", 1200, "GOOD", "SUNNY",
                start, null, 8, null, null, null,
                purse == null ? null : new java.math.BigDecimal(purse), null, tiers);
    }

    @Test
    void createRace_tiersDoNotSumToPurse_throws() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(fundedTournament("1000")));

        var tiers = java.util.List.of(new PrizeDistributionDto("1", new java.math.BigDecimal("400")),
                new PrizeDistributionDto("2", new java.math.BigDecimal("300")));  // 700 != 1000

        assertThatThrownBy(() -> service.createRace(currentUserId,
                reqWithPurse("1000", tiers, OffsetDateTime.now().plusDays(12))))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRIZE_DISTRIBUTION_MISMATCH);
    }

    /**
     * "1st" and "1" are the SAME finish position — parsePlace maps both to 1 and the payout map is a
     * HashMap, so the second silently overwrote the first and one tier's money vanished at certify.
     */
    @Test
    void createRace_sameParsedPlaceSpelledTwoWays_throws() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(fundedTournament("1000")));

        var tiers = java.util.List.of(new PrizeDistributionDto("1st", new java.math.BigDecimal("600")),
                new PrizeDistributionDto("1", new java.math.BigDecimal("400")));

        assertThatThrownBy(() -> service.createRace(currentUserId,
                reqWithPurse("1000", tiers, OffsetDateTime.now().plusDays(12))))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRIZE_DISTRIBUTION_DUPLICATE_PLACE);
    }

    @Test
    void createRace_purseExceedsRemainingTournamentBudget_throws() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(fundedTournament("1000")));
        when(raceRepository.sumAllocatedPurse(tournamentId, null)).thenReturn(new java.math.BigDecimal("900"));

        var tiers = java.util.List.of(new PrizeDistributionDto("1", new java.math.BigDecimal("500")));

        assertThatThrownBy(() -> service.createRace(currentUserId,
                reqWithPurse("500", tiers, OffsetDateTime.now().plusDays(12))))  // 900 + 500 > 1000
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_PURSE_EXCEEDS_TOURNAMENT);
    }

    @Test
    void createRace_purseOmitted_splitsRemainingBudgetAndDefaultsTiersThatSumExactly() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(fundedTournament("1000")));
        when(raceRepository.sumAllocatedPurse(tournamentId, null)).thenReturn(java.math.BigDecimal.ZERO);
        when(raceRepository.countActiveByTournament(tournamentId)).thenReturn(1L);
        when(raceRepository.count()).thenReturn(0L);
        when(raceRepository.existsByRaceCode(any())).thenReturn(false);
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        service.createRace(currentUserId, reqWithPurse(null, null, OffsetDateTime.now().plusDays(12)));

        ArgumentCaptor<Race> captor = ArgumentCaptor.forClass(Race.class);
        verify(raceRepository).save(captor.capture());
        Race saved = captor.getValue();
        // 1000 remaining over (1 existing + this one) = 500
        assertThat(saved.getTotalPurse()).isEqualByComparingTo("500");
        // The tiers must add up to the purse EXACTLY — 1st absorbs the rounding remainder.
        java.math.BigDecimal sum = saved.getPrizeDistribution().stream()
                .map(com.SWP391.horserace.races.entity.PrizeDistributionItem::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(saved.getTotalPurse());
    }

    /** Rounding must never leave a gap: 1000/3 is not exact, yet the tiers still total the purse. */
    @Test
    void createRace_indivisiblePurse_tiersStillSumExactly() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(fundedTournament("1000")));
        when(raceRepository.sumAllocatedPurse(tournamentId, null)).thenReturn(java.math.BigDecimal.ZERO);
        when(raceRepository.countActiveByTournament(tournamentId)).thenReturn(2L);
        when(raceRepository.count()).thenReturn(0L);
        when(raceRepository.existsByRaceCode(any())).thenReturn(false);
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        service.createRace(currentUserId, reqWithPurse(null, null, OffsetDateTime.now().plusDays(12)));

        ArgumentCaptor<Race> captor = ArgumentCaptor.forClass(Race.class);
        verify(raceRepository).save(captor.capture());
        Race saved = captor.getValue();
        assertThat(saved.getTotalPurse()).isEqualByComparingTo("333.33");  // rounded DOWN, never over
        java.math.BigDecimal sum = saved.getPrizeDistribution().stream()
                .map(com.SWP391.horserace.races.entity.PrizeDistributionItem::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("333.33");
    }

    @Test
    void createRace_startBeforeTournamentOpens_throws() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(fundedTournament("1000")));

        assertThatThrownBy(() -> service.createRace(currentUserId,
                reqWithPurse(null, java.util.List.of(), OffsetDateTime.now().plusDays(5))))  // opens on day 10
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_OUTSIDE_TOURNAMENT_WINDOW);
    }

    @Test
    void createRace_startAfterTournamentEnds_throws() {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(fundedTournament("1000")));

        assertThatThrownBy(() -> service.createRace(currentUserId,
                reqWithPurse(null, java.util.List.of(), OffsetDateTime.now().plusDays(25))))  // ends on day 20
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_OUTSIDE_TOURNAMENT_WINDOW);
    }

    /**
     * Both ends are INCLUSIVE. Six seeded races start exactly on their tournament's opening day and
     * are still SCHEDULED/OPEN, i.e. editable — an exclusive rule would break a live click-path.
     */
    @Test
    void createRace_startExactlyOnTournamentBoundaryDays_isAllowed() {
        Tournament t = fundedTournament("1000");
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(t));
        when(raceRepository.count()).thenReturn(0L);
        when(raceRepository.existsByRaceCode(any())).thenReturn(false);
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.createRace(currentUserId,
                reqWithPurse("0", java.util.List.of(), t.getStartDate()))).isNotNull();
        assertThat(service.createRace(currentUserId,
                reqWithPurse("0", java.util.List.of(), t.getEndDate()))).isNotNull();
    }

    @Test
    void deleteRace_withPrizesAlreadyPaid_throws() {
        UUID id = UUID.randomUUID();
        Race race = Race.builder().raceId(id).status(RaceStatus.OFFICIAL).build();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));
        when(prizeRepository.existsByRace_RaceId(id)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteRace(currentUserId, id))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RACE_HAS_PAID_PRIZES);
        verify(raceRepository, never()).save(any(Race.class));
    }

    // ── entering a horse debits the owner's wallet, so only they (or staff) may do it ──

    /**
     * Regression: this endpoint had no @PreAuthorize and no ownership check, so ANY authenticated
     * user could post a stranger's registrationId and drain that owner's wallet.
     */
    @Test
    void assign_byAnotherUser_isRejected() {
        UUID id = UUID.randomUUID();
        UUID regId = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(openRace(id)));
        when(registrationRepository.findById(regId)).thenReturn(Optional.of(approvedReg(regId, tournamentId)));
        when(userRepository.findByUserIdAndDeletedFalse(attacker))
                .thenReturn(Optional.of(User.builder().userId(attacker)
                        .role(Role.builder().roleCode("SPECTATOR").build()).build()));

        assertThatThrownBy(() -> service.assignParticipant(attacker, id,
                new AssignParticipantRequest(regId, 1, 1)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_REGISTRATION_OWNER);

        verify(raceEntryGate, never()).chargeEntryFeeOnce(any(), any());
        verify(raceEntryRepository, never()).save(any(RaceEntry.class));
    }

    @Test
    void assign_byAdmin_isAllowed() {
        UUID id = UUID.randomUUID();
        UUID regId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(openRace(id)));
        when(registrationRepository.findById(regId)).thenReturn(Optional.of(approvedReg(regId, tournamentId)));
        when(userRepository.findByUserIdAndDeletedFalse(adminId))
                .thenReturn(Optional.of(User.builder().userId(adminId)
                        .role(Role.builder().roleCode("ADMIN").build()).build()));
        when(raceEntryRepository.count()).thenReturn(0L);
        when(raceEntryRepository.save(any(RaceEntry.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.assignParticipant(adminId, id, new AssignParticipantRequest(regId, 1, 1)))
                .isNotNull();
    }

    // ── cancelling a race must return every entry fee ──

    @Test
    void cancelRace_refundsEveryPaidRegistration() {
        UUID id = UUID.randomUUID();
        Race race = openRace(id);
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(race));
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));
        TournamentRegistration r1 = approvedReg(UUID.randomUUID(), tournamentId);
        TournamentRegistration r2 = approvedReg(UUID.randomUUID(), tournamentId);
        when(registrationRepository.findUnrefundedPaidByRace(id)).thenReturn(List.of(r1, r2));

        service.cancelRace(currentUserId, id);

        verify(raceEntryGate).refundEntryFeeOnce(r1);
        verify(raceEntryGate).refundEntryFeeOnce(r2);
    }

    @Test
    void cancelRace_nothingPaid_movesNoMoney() {
        UUID id = UUID.randomUUID();
        when(raceRepository.findByRaceIdAndDeletedFalse(id)).thenReturn(Optional.of(openRace(id)));
        when(raceRepository.save(any(Race.class))).thenAnswer(i -> i.getArgument(0));
        when(registrationRepository.findUnrefundedPaidByRace(id)).thenReturn(List.of());

        service.cancelRace(currentUserId, id);

        verify(raceEntryGate, never()).refundEntryFeeOnce(any());
    }
}
